package com.ecommerce.review.service;

import com.ecommerce.common.exception.DuplicateResourceException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.review.api.dto.*;
import com.ecommerce.review.client.OrderServiceClient;
import com.ecommerce.review.client.ProductCatalogClient;
import com.ecommerce.review.domain.*;
import com.ecommerce.review.mapper.ReviewMapper;
import com.ecommerce.review.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewAggregateRepository aggregateRepository;
    private final ReviewVoteRepository voteRepository;
    private final ReviewReportRepository reportRepository;
    private final OutboxRepository outboxRepository;
    private final OrderServiceClient orderServiceClient;
    private final ProductCatalogClient productCatalogClient;
    private final ReviewMapper reviewMapper;

    // ─────────────────────────────────────────────────────────────────
    // SUBMIT REVIEW
    // ─────────────────────────────────────────────────────────────────

    /*
     * submitReview() — the main write operation.
     *
     * One @Transactional scope:
     *   1. Duplicate check
     *   2. Verified purchase check (Feign)
     *   3. Save review (PENDING)
     *   4. Auto-approve + update aggregate
     *   5. Write outbox event
     *
     * Steps 3-5 are atomic. If Kafka is down, the outbox event persists
     * and will be published when Kafka recovers, via OutboxPoller.
     */
    @Transactional
    public ReviewResponse submitReview(UUID userId, UUID keycloakId,
                                       String reviewerName,
                                       CreateReviewRequest request) {
        // ── Guard: one review per user per product ──
        if (reviewRepository.existsByProductIdAndUserId(request.productId(), userId)) {
            throw new DuplicateResourceException(
                "Review", "product+user",
                request.productId() + "+" + userId);
        }

        // ── Guard: Verify product exists (Catalog Service) ──
        try {
            if (productCatalogClient.getProduct(request.productId()) == null) {
                // If fallback returns null or explicit 404
                throw new ResourceNotFoundException("Product", request.productId().toString());
            }
        } catch (Exception e) {
            log.error("Failed to validate productId={}", request.productId(), e);
            throw new ResourceNotFoundException("Product", request.productId().toString());
        }

        // ── Check verified purchase (falls back to false on outage) ──
        boolean verified = orderServiceClient.hasUserPurchasedProduct(
            userId, request.productId());

        // ── Create and save review ──
        Review review = Review.builder()
            .productId(request.productId())
            .userId(userId)
            .reviewerKeycloakId(keycloakId)
            .reviewerName(reviewerName)
            .rating(request.rating().shortValue())
            .title(request.title())
            .body(request.body())
            .verifiedPurchase(verified)
            .status(ReviewStatus.PENDING)
            .build();

        reviewRepository.save(review);

        /*
         * Auto-approve for this implementation.
         * In production replace with: moderationQueue.enqueue(review.getId())
         * and let review stay PENDING until the worker processes it.
         */
        review.approve(null); // null = system-approved
        reviewRepository.save(review);
        updateAggregateOnApproval(request.productId(), request.rating());

        // ── Write outbox event in same transaction ──
        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("Review")
            .aggregateId(review.getId())
            .eventType("review.submitted")
            .payload(Map.of(
                "reviewId",  review.getId().toString(),
                "productId", request.productId().toString(),
                "userId",    userId.toString(),
                "rating",    request.rating(),
                "verified",  verified
            ))
            .build());

        log.info("Review submitted reviewId={} productId={} rating={} verified={}",
            review.getId(), request.productId(), request.rating(), verified);

        return reviewMapper.toResponse(review);
    }

    // ─────────────────────────────────────────────────────────────────
    // READ REVIEWS
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ReviewPageResponse getProductReviews(UUID productId,
                                                int page, int size,
                                                boolean verifiedOnly) {
        Page<Review> reviewPage = verifiedOnly
            ? reviewRepository.findApprovedByProductIdFiltered(
                productId, true, PageRequest.of(page, size))
            : reviewRepository.findApprovedByProductId(
                productId, PageRequest.of(page, size));

        ReviewAggregate aggregate = aggregateRepository
            .findById(productId)
            .orElse(emptyAggregate(productId));

        return new ReviewPageResponse(
            reviewMapper.toResponseList(reviewPage.getContent()),
            reviewMapper.toAggregateResponse(aggregate),
            page, size,
            reviewPage.getTotalElements(),
            reviewPage.getTotalPages(),
            reviewPage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public ReviewAggregateResponse getAggregate(UUID productId) {
        ReviewAggregate aggregate = aggregateRepository
            .findById(productId)
            .orElse(emptyAggregate(productId));
        return reviewMapper.toAggregateResponse(aggregate);
    }

    @Transactional(readOnly = true)
    public ReviewPageResponse getMyReviews(UUID userId, int page, int size) {
        Page<Review> reviewPage = reviewRepository
            .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return new ReviewPageResponse(
            reviewMapper.toResponseList(reviewPage.getContent()),
            null, page, size,
            reviewPage.getTotalElements(),
            reviewPage.getTotalPages(),
            reviewPage.hasNext()
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // HELPFUL VOTING
    // ─────────────────────────────────────────────────────────────────

    /*
     * voteHelpful() — THREE CASES:
     * 1. First time voting → create vote, increment counters
     * 2. Same vote again → idempotent, no change
     * 3. Changing vote → update vote, adjust helpful counter only
     *    (total_votes unchanged — they already voted)
     */
    @Transactional
    public ReviewResponse voteHelpful(UUID reviewId, UUID userId, boolean isHelpful) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId.toString()));

        if (!review.getStatus().isVisible()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }

        if (review.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "You cannot vote on your own review");
        }

        voteRepository.findByReviewIdAndUserId(reviewId, userId)
            .ifPresentOrElse(
                existingVote -> {
                    if (existingVote.getIsHelpful() == isHelpful) return; // Idempotent
                    existingVote.setIsHelpful(isHelpful);
                    voteRepository.save(existingVote);
                    if (isHelpful) {
                        review.setHelpfulVotes(review.getHelpfulVotes() + 1);
                    } else {
                        review.setHelpfulVotes(Math.max(0, review.getHelpfulVotes() - 1));
                    }
                    // total_votes unchanged
                },
                () -> {
                    voteRepository.save(ReviewVote.builder()
                        .reviewId(reviewId)
                        .userId(userId)
                        .isHelpful(isHelpful)
                        .build());
                    review.setTotalVotes(review.getTotalVotes() + 1);
                    if (isHelpful) review.setHelpfulVotes(review.getHelpfulVotes() + 1);
                }
            );

        reviewRepository.save(review);
        return reviewMapper.toResponse(review);
    }

    // ─────────────────────────────────────────────────────────────────
    // REPORTING
    // ─────────────────────────────────────────────────────────────────

    @Transactional
    public void reportReview(UUID reviewId, UUID reporterId,
                             String reason, String details) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId.toString()));

        if (!review.getStatus().isVisible()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }

        if (reportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "You have already reported this review");
        }

        reportRepository.save(ReviewReport.builder()
            .reviewId(reviewId)
            .reporterId(reporterId)
            .reason(reason)
            .details(details)
            .build());

        // Auto-flag at 5 reports: contains APPROVED → FLAGGED logic
        review.incrementReportCount();
        reviewRepository.save(review);

        log.info("Review reported reviewId={} reporterId={} reason={} newCount={}",
            reviewId, reporterId, reason, review.getReportCount());
    }

    // ─────────────────────────────────────────────────────────────────
    // MODERATION — admin only
    // ─────────────────────────────────────────────────────────────────

    @Transactional
    public ReviewResponse moderateReview(UUID reviewId, UUID moderatorId,
                                         String action, String reason) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId.toString()));

        boolean wasApproved = review.getStatus() == ReviewStatus.APPROVED;

        switch (action.toUpperCase()) {
            case "APPROVE" -> review.approve(moderatorId);
            case "REJECT"  -> {
                if (reason == null || reason.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Rejection reason is required");
                }
                review.reject(moderatorId, reason);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid action: " + action + ". Use APPROVE or REJECT");
        }

        reviewRepository.save(review);

        /*
         * Aggregate update logic:
         * PENDING → APPROVE: add to aggregate (first approval)
         * APPROVED → REJECT (via FLAGGED): remove from aggregate
         * PENDING → REJECT: no aggregate change needed
         */
        if (action.equalsIgnoreCase("APPROVE") && !wasApproved) {
            updateAggregateOnApproval(review.getProductId(), review.getRating());
        } else if (action.equalsIgnoreCase("REJECT") && wasApproved) {
            updateAggregateOnRemoval(review.getProductId(), review.getRating());
        }

        log.info("Review moderated reviewId={} action={} by={}",
            reviewId, action, moderatorId);

        return reviewMapper.toResponse(review);
    }

    @Transactional(readOnly = true)
    public ReviewPageResponse getModerationQueue(ReviewStatus status, int page, int size) {
        Page<Review> queue = reviewRepository
            .findByStatusOrderByReportCountDescCreatedAtAsc(
                status, PageRequest.of(page, size));
        return new ReviewPageResponse(
            reviewMapper.toResponseList(queue.getContent()),
            null, page, size,
            queue.getTotalElements(),
            queue.getTotalPages(),
            queue.hasNext()
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────

    private void updateAggregateOnApproval(UUID productId, int rating) {
        ReviewAggregate aggregate = aggregateRepository
            .findByProductIdForUpdate(productId)
            .orElseGet(() -> ReviewAggregate.builder().productId(productId).build());

        aggregate.addReview(rating);
        aggregateRepository.save(aggregate);

        log.debug("Aggregate updated productId={} newAvg={} total={}",
            productId, aggregate.getAverageRating(), aggregate.getTotalReviews());
    }

    private void updateAggregateOnRemoval(UUID productId, int rating) {
        aggregateRepository.findByProductIdForUpdate(productId)
            .ifPresent(aggregate -> {
                aggregate.removeReview(rating);
                aggregateRepository.save(aggregate);
            });
    }

    private ReviewAggregate emptyAggregate(UUID productId) {
        return ReviewAggregate.builder().productId(productId).build();
    }
}

package com.ecommerce.review.api;

import com.ecommerce.common.security.SecurityUtils;
import com.ecommerce.review.api.dto.*;
import com.ecommerce.review.domain.ReviewStatus;
import com.ecommerce.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // ─── Public read endpoints (no auth needed) ───

    /*
     * GET /api/v1/products/{productId}/reviews
     *
     * Returns paginated reviews + aggregate in one response.
     * The aggregate is always included so the product page renders
     * both the star rating summary AND the review list in one API call.
     *
     * verifiedOnly=true filters to only verified purchase reviews.
     */
    @GetMapping("/products/{productId}/reviews")
    public ResponseEntity<ReviewPageResponse> getProductReviews(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0")     int page,
            @RequestParam(defaultValue = "10")    int size,
            @RequestParam(defaultValue = "false") boolean verifiedOnly) {
        return ResponseEntity.ok(
            reviewService.getProductReviews(productId, page, Math.min(size, 50), verifiedOnly));
    }

    /*
     * GET /api/v1/products/{productId}/reviews/aggregate
     *
     * Lightweight endpoint for just the rating summary.
     * Used by product list pages where you show ⭐4.3 (847) per card
     * but don't need the full review text.
     */
    @GetMapping("/products/{productId}/reviews/aggregate")
    public ResponseEntity<ReviewAggregateResponse> getAggregate(
            @PathVariable UUID productId) {
        return ResponseEntity.ok(reviewService.getAggregate(productId));
    }

    // ─── Authenticated user endpoints ───

    @PostMapping("/reviews")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ReviewResponse> submitReview(
            @Valid @RequestBody CreateReviewRequest request) {
        var user = SecurityUtils.getCurrentUser();
        // Snapshot the reviewer name from JWT (email prefix as display name)
        String reviewerName = user.getEmail().split("@")[0];
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(reviewService.submitReview(
                user.getUserId(),
                user.getUserId(), // keycloakId = userId from JWT sub claim
                reviewerName,
                request
            ));
    }

    @GetMapping("/users/me/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewPageResponse> getMyReviews(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
            reviewService.getMyReviews(SecurityUtils.getCurrentUserId(), page, size));
    }

    @PostMapping("/reviews/{reviewId}/vote")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReviewResponse> voteHelpful(
            @PathVariable UUID reviewId,
            @Valid @RequestBody VoteRequest request) {
        return ResponseEntity.ok(
            reviewService.voteHelpful(reviewId, SecurityUtils.getCurrentUserId(),
                request.helpful()));
    }

    @PostMapping("/reviews/{reviewId}/report")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> reportReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReportRequest request) {
        reviewService.reportReview(reviewId, SecurityUtils.getCurrentUserId(),
            request.reason(), request.details());
        return ResponseEntity.accepted().build();
    }

    // ─── Admin moderation endpoints ───

    @PostMapping("/admin/reviews/{reviewId}/moderate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewResponse> moderate(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ModerateReviewRequest request) {
        return ResponseEntity.ok(reviewService.moderateReview(
            reviewId, SecurityUtils.getCurrentUserId(),
            request.action(), request.rejectionReason()));
    }

    @GetMapping("/admin/reviews/moderation-queue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewPageResponse> getModerationQueue(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reviewService.getModerationQueue(
            ReviewStatus.valueOf(status), page, size));
    }
}

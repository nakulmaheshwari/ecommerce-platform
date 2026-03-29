package com.ecommerce.review.repository;

import com.ecommerce.review.domain.Review;
import com.ecommerce.review.domain.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByProductIdAndUserId(UUID productId, UUID userId);

    Optional<Review> findByProductIdAndUserId(UUID productId, UUID userId);

    /*
     * Sort priority:
     * 1. Verified purchases first (more trusted)
     * 2. By helpful votes descending (most helpful at top)
     * 3. By creation date (newer first as tiebreaker)
     */
    @Query("""
        SELECT r FROM Review r
        WHERE r.productId = :productId
          AND r.status = 'APPROVED'
        ORDER BY r.verifiedPurchase DESC,
                 r.helpfulVotes DESC,
                 r.createdAt DESC
        """)
    Page<Review> findApprovedByProductId(
        @Param("productId") UUID productId, Pageable pageable);

    @Query("""
        SELECT r FROM Review r
        WHERE r.productId = :productId
          AND r.status = 'APPROVED'
          AND r.verifiedPurchase = :verifiedOnly
        ORDER BY r.helpfulVotes DESC, r.createdAt DESC
        """)
    Page<Review> findApprovedByProductIdFiltered(
        @Param("productId") UUID productId,
        @Param("verifiedOnly") boolean verifiedOnly,
        Pageable pageable);

    // User's own reviews — shows all statuses (so user sees pending/rejected)
    Page<Review> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // Moderation queue
    Page<Review> findByStatusOrderByReportCountDescCreatedAtAsc(
        ReviewStatus status, Pageable pageable);
}

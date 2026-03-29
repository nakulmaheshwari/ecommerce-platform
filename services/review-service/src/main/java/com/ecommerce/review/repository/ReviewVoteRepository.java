package com.ecommerce.review.repository;

import com.ecommerce.review.domain.ReviewVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewVoteRepository extends JpaRepository<ReviewVote, UUID> {
    Optional<ReviewVote> findByReviewIdAndUserId(UUID reviewId, UUID userId);
    boolean existsByReviewIdAndUserId(UUID reviewId, UUID userId);
}

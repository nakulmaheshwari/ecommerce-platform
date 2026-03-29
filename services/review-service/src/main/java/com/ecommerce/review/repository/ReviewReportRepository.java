package com.ecommerce.review.repository;

import com.ecommerce.review.domain.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, UUID> {
    boolean existsByReviewIdAndReporterId(UUID reviewId, UUID reporterId);
}

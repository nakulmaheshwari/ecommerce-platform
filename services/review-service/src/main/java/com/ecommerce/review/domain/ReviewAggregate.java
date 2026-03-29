package com.ecommerce.review.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_aggregates")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ReviewAggregate {

    @Id
    private UUID productId;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalReviews = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalScore = 0;

    @Column(nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "rating_1_count") @Builder.Default private Integer rating1Count = 0;
    @Column(name = "rating_2_count") @Builder.Default private Integer rating2Count = 0;
    @Column(name = "rating_3_count") @Builder.Default private Integer rating3Count = 0;
    @Column(name = "rating_4_count") @Builder.Default private Integer rating4Count = 0;
    @Column(name = "rating_5_count") @Builder.Default private Integer rating5Count = 0;

    @Column(name = "last_review_at")
    private Instant lastReviewAt;

    @UpdateTimestamp
    private Instant updatedAt;

    /*
     * addReview() — atomically updates all aggregate fields.
     *
     * Called inside @Transactional with a pessimistic lock on this row.
     * If the transaction rolls back, the aggregate is not updated.
     * Division happens once on write, not on every read.
     */
    public void addReview(int rating) {
        this.totalReviews++;
        this.totalScore += rating;
        this.averageRating = BigDecimal.valueOf(totalScore)
            .divide(BigDecimal.valueOf(totalReviews), 2, RoundingMode.HALF_UP);
        this.lastReviewAt = Instant.now();
        incrementRatingCount(rating);
    }

    /*
     * removeReview() — called when a review is rejected after approval.
     * Prevents approved-then-rejected reviews from inflating the average.
     */
    public void removeReview(int rating) {
        if (this.totalReviews <= 0) return;
        this.totalReviews--;
        this.totalScore -= rating;
        this.averageRating = totalReviews == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(totalScore)
                .divide(BigDecimal.valueOf(totalReviews), 2, RoundingMode.HALF_UP);
        decrementRatingCount(rating);
    }

    private void incrementRatingCount(int rating) {
        switch (rating) {
            case 1 -> rating1Count++;
            case 2 -> rating2Count++;
            case 3 -> rating3Count++;
            case 4 -> rating4Count++;
            case 5 -> rating5Count++;
        }
    }

    private void decrementRatingCount(int rating) {
        switch (rating) {
            case 1 -> rating1Count = Math.max(0, rating1Count - 1);
            case 2 -> rating2Count = Math.max(0, rating2Count - 1);
            case 3 -> rating3Count = Math.max(0, rating3Count - 1);
            case 4 -> rating4Count = Math.max(0, rating4Count - 1);
            case 5 -> rating5Count = Math.max(0, rating5Count - 1);
        }
    }

    // Percentage breakdown for rating histogram
    public int ratingPercent(int star) {
        if (totalReviews == 0) return 0;
        int count = switch (star) {
            case 1 -> rating1Count;
            case 2 -> rating2Count;
            case 3 -> rating3Count;
            case 4 -> rating4Count;
            case 5 -> rating5Count;
            default -> 0;
        };
        return (int) Math.round((double) count / totalReviews * 100);
    }
}

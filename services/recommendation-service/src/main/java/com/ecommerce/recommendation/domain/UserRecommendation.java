package com.ecommerce.recommendation.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_recommendations",
       indexes = {
           @Index(name = "idx_user_recs_user",    columnList = "user_id, rank ASC"),
           @Index(name = "idx_user_recs_expires", columnList = "expires_at")
       })
@IdClass(UserRecommendationId.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserRecommendation {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "rank", nullable = false)
    private Short rank;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "score", nullable = false, precision = 8, scale = 4)
    private BigDecimal score;

    @Column(name = "algorithm", nullable = false, length = 30)
    private String algorithm;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

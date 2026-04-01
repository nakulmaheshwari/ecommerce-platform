package com.ecommerce.recommendation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_product_interactions",
       indexes = {
           @Index(name = "idx_interactions_user_time",    columnList = "user_id, occurred_at DESC"),
           @Index(name = "idx_interactions_product_time", columnList = "product_id, occurred_at DESC"),
           @Index(name = "idx_interactions_time",         columnList = "occurred_at DESC")
       })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserProductInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /*
     * Pre-computed weight stored as a column so Spark training
     * just does SUM(implicit_score) per (userId, productId)
     * without needing to reapply the weight mapping.
     *
     * Weights:
     *   order_placed             = 3.0
     *   product_added_to_cart    = 0.7
     *   wishlist_added           = 0.8
     *   search_result_clicked    = 0.5
     *   product_detail_dwell     = 0.5
     *   product_viewed           = 0.3
     *   search_performed         = 0.2
     *   product_removed_from_cart= -0.3
     */
    @Column(name = "implicit_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal implicitScore;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

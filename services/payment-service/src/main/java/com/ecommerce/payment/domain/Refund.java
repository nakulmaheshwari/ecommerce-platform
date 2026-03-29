package com.ecommerce.payment.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "refunds")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID paymentTransactionId;

    @Column(nullable = false)
    private UUID orderId;

    private String razorpayRefundId;

    @Column(nullable = false)
    private Long amountPaise;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(nullable = false)
    private String initiatedBy;

    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> gatewayResponse;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant processedAt;
}

package com.ecommerce.payment.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private UUID idempotencyKey;

    // Razorpay's identifiers
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;

    @Column(nullable = false)
    private Long amountPaise;

    @Column(nullable = false, length = 3)
    @JdbcTypeCode(java.sql.Types.CHAR)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.INITIATED;

    private String failureCode;
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> gatewayResponse;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant webhookReceivedAt;
    private Instant capturedAt;

    public void markCaptured(String razorpayPaymentId,
                             String razorpaySignature,
                             Map<String, Object> gatewayResponse) {
        this.razorpayPaymentId = razorpayPaymentId;
        this.razorpaySignature = razorpaySignature;
        this.status = PaymentStatus.CAPTURED;
        this.gatewayResponse = gatewayResponse;
        this.webhookReceivedAt = Instant.now();
        this.capturedAt = Instant.now();
    }

    public void markFailed(String failureCode, String failureReason,
                           Map<String, Object> gatewayResponse) {
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.status = PaymentStatus.FAILED;
        this.gatewayResponse = gatewayResponse;
        this.webhookReceivedAt = Instant.now();
    }

    public boolean canBeRefunded() {
        return this.status == PaymentStatus.CAPTURED;
    }
}

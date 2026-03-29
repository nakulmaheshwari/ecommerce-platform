package com.ecommerce.notification.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /*
     * idempotencyKey format: "{templateId}:{referenceId}"
     * Examples:
     *   "order-confirmed-v1:ord-abc-123"
     *   "order-shipped-v1:ord-abc-123"
     *
     * Prevents duplicate sends if Kafka delivers the same event twice.
     * UNIQUE constraint in DB enforces this at persistence level.
     */
    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Column(nullable = false)
    private String templateId;

    @Column(nullable = false)
    private String recipient;

    private String subject;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> payloadSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    private String failureReason;

    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer maxRetries = 3;

    private Instant nextRetryAt;
    private Instant sentAt;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    private Instant updatedAt;

    public void markSent() {
        this.status  = NotificationStatus.SENT;
        this.sentAt  = Instant.now();
        this.failureReason = null;
    }

    /*
     * Exponential backoff on failure:
     * Retry 1: 1 minute, Retry 2: 4 minutes, Retry 3: 9 minutes
     * After maxRetries: FAILED permanently
     */
    public void markFailed(String reason) {
        this.retryCount++;
        this.failureReason = reason;

        if (this.retryCount >= this.maxRetries) {
            this.status = NotificationStatus.FAILED;
            this.nextRetryAt = null;
        } else {
            this.status = NotificationStatus.PENDING;
            long backoffMinutes = (long) Math.pow(this.retryCount, 2);
            this.nextRetryAt = Instant.now().plusSeconds(backoffMinutes * 60);
        }
    }

    public boolean canRetry() {
        return this.status == NotificationStatus.PENDING &&
               this.retryCount < this.maxRetries;
    }
}

package com.ecommerce.inventory.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the Transactional Outbox pattern.
 * This entity stores events that need to be published to Kafka in the same database transaction
 * as the business logic. This guarantees "At-Least-Once" delivery and atomic state changes.
 */
@Entity
@Table(name = "outbox_events")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class OutboxEvent {

    /**
     * Unique ID for the event. Used for deduplication in the consumer.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The type of business entity this event relates to (e.g., "INVENTORY").
     */
    @Column(nullable = false)
    private String aggregateType;

    /**
     * The identifier for the business entity (e.g., SKU ID).
     */
    @Column(nullable = false)
    private String aggregateId;

    /**
     * The specific action that occurred (e.g., "inventory.reserved").
     */
    @Column(nullable = false)
    private String eventType;

    /**
     * The actual event data stored in JSON format for flexibility.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    /**
     * Set to true by the OutboxPoller once successfully sent to Kafka.
     */
    @Builder.Default
    private Boolean published = false;

    /**
     * Wall-clock time when the event was generated.
     */
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

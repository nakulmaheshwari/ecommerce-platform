package com.ecommerce.order.domain;

import com.ecommerce.common.exception.BaseException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "orders")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, unique = true)
    private UUID idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(nullable = false)
    private Long subtotalPaise;

    @Column(nullable = false)
    @Builder.Default
    private Long discountPaise = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Long taxPaise = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Long deliveryPaise = 0L;

    @Column(nullable = false)
    private Long totalPaise;

    /**
     * Shipping address stored as JSONB — a snapshot of the address at order time.
     * Map<String, Object> because the address structure might vary
     * (domestic vs international, different required fields).
     *
     * Alternative: separate shipping_addresses table with FK.
     * Rejected because: if a user deletes their address, we'd lose
     * the delivery address for historical orders. JSONB snapshot is simpler
     * and more resilient.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> shippingAddress;

    private String notes;

    /**
     * @Version — Hibernate optimistic locking.
     *
     * Every UPDATE to this row becomes:
     *   UPDATE orders SET status=?, version=version+1
     *   WHERE id=? AND version=?
     *
     * If two transactions try to update simultaneously:
     *   TX1 reads version=5, TX2 reads version=5
     *   TX1 updates: WHERE version=5 → succeeds, version becomes 6
     *   TX2 updates: WHERE version=5 → 0 rows affected → OptimisticLockException
     *
     * TX2 must retry (re-read the order and try again).
     * No deadlocks, no long-held locks, no silent data corruption.
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    /**
     * The ONLY way to change order status.
     * Enforces state machine rules before touching any field.
     * Caller also provides actor (who did this) and reason (why).
     */
    public void transitionTo(OrderStatus newStatus, String actor, String reason) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new InvalidOrderTransitionException(this.id, this.status, newStatus);
        }
        this.status = newStatus;
        // The history record is written by the service layer in the same transaction
    }

    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }
}

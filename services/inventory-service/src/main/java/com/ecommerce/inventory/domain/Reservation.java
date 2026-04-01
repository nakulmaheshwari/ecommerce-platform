package com.ecommerce.inventory.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a temporary "lock" on stock units for a specific customer order.
 * Reservations prevent overselling by moving units from 'available' to 'reserved'.
 */
@Entity
@Table(name = "reservations", uniqueConstraints = {
    @UniqueConstraint(name = "unique_order_sku", columnNames = {"order_id", "sku_id"})
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Reservation {

    /**
     * Unique ID for the reservation record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The product variant being held.
     */
    @Column(nullable = false)
    private String skuId;

    /**
     * The order that requested this stock lock.
     */
    @Column(nullable = false)
    private UUID orderId;

    /**
     * Number of physical units locked.
     */
    @Column(nullable = false)
    private Integer quantity;

    /**
     * Current state of the lock.
     * Starts as HELD and transitions to CONFIRMED or RELEASED based on payment status.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.HELD;

    /**
     * The deadline for the customer to complete payment.
     * Default is 15 minutes. Beyond this, stock may be reclaimed by the system.
     */
    @Column(nullable = false)
    @Builder.Default
    private Instant expiresAt = Instant.now().plusSeconds(900);

    /**
     * When the record was first inserted.
     */
    private Instant createdAt;

    /**
     * When the record's status or expiry was last updated.
     */
    @UpdateTimestamp
    private Instant updatedAt;

    /**
     * Logical check to see if the hold window has closed.
     * Does not auto-release stock; requires a cleanup job to process.
     */
    public boolean isExpired() {
        return status == ReservationStatus.HELD &&
               expiresAt.isBefore(Instant.now());
    }
}

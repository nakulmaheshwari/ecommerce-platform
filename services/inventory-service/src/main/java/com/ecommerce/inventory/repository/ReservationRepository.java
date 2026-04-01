package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.Reservation;
import com.ecommerce.inventory.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing {@link Reservation} records (stock locks).
 */
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /**
     * Finds all reservations associated with a single order.
     */
    List<Reservation> findByOrderId(UUID orderId);

    /**
     * Quick check to see if an order has already had ANY stock reserved.
     */
    boolean existsByOrderId(UUID orderId);

    /**
     * Finds reservations by order and status (e.g., all 'HELD' items).
     */
    List<Reservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);

    /**
     * Lookup a specific SKU reservation for an order.
     */
    Optional<Reservation> findBySkuIdAndOrderId(String skuId, UUID orderId);

    /**
     * Quick check to see if an order has already had stock reserved.
     * Essential for idempotency in the event consumer.
     */
    boolean existsBySkuIdAndOrderId(String skuId, UUID orderId);

    /**
     * Custom query to find all 'HELD' reservations that have surpassed their expiresAt time.
     * Used by the {@link com.ecommerce.inventory.event.producer.ReservationExpiryJob} 
     * to return abandoned stock to the available pool.
     */
    @Query("""
        SELECT r FROM Reservation r
        WHERE r.status = 'HELD'
          AND r.expiresAt < :now
        """)
    List<Reservation> findExpiredReservations(Instant now);
}

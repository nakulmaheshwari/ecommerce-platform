package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.Reservation;
import com.ecommerce.inventory.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);

    Optional<Reservation> findBySkuIdAndOrderId(String skuId, UUID orderId);

    boolean existsBySkuIdAndOrderId(String skuId, UUID orderId);

    @Query("""
        SELECT r FROM Reservation r
        WHERE r.status = 'HELD'
          AND r.expiresAt < :now
        """)
    List<Reservation> findExpiredReservations(Instant now);
}

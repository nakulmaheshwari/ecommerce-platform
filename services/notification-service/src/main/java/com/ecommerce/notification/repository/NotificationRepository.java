package com.ecommerce.notification.repository;

import com.ecommerce.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /*
     * Fetches notifications due for retry.
     * nextRetryAt <= NOW means it's time to try again.
     * Ordered by nextRetryAt so oldest retries go first.
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.status = 'PENDING'
          AND (n.nextRetryAt IS NULL OR n.nextRetryAt <= :now)
        ORDER BY n.nextRetryAt ASC NULLS FIRST
        """)
    List<Notification> findDueForProcessing(Instant now);
}

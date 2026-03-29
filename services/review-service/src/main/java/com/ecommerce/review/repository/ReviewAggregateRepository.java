package com.ecommerce.review.repository;

import com.ecommerce.review.domain.ReviewAggregate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewAggregateRepository extends JpaRepository<ReviewAggregate, UUID> {

    /*
     * Pessimistic write lock — prevents lost-update on concurrent review submissions.
     *
     * Without: Thread A reads total=100, Thread B reads total=100.
     * Both write 101. One update is lost.
     *
     * With lock: Thread A acquires lock, Thread B waits.
     * Thread A updates to 101, releases. Thread B reads 101, writes 102.
     * Result is always correct.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ReviewAggregate a WHERE a.productId = :productId")
    Optional<ReviewAggregate> findByProductIdForUpdate(
        @Param("productId") UUID productId);
}

package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the Transactional Outbox.
 * Stores events that are pending dispatch to Kafka.
 */
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Finds a batch of unpublished events, ordered by age.
     * Used by the background {@link com.ecommerce.inventory.event.producer.OutboxPoller} 
     * to publish events to Kafka with guaranteed ordering per aggregate.
     * 
     * @param limit Maximum number of events to fetch in one batch.
     * @return List of events where published = FALSE.
     */
    @Query(value = "SELECT * FROM outbox_events WHERE published = FALSE ORDER BY created_at ASC LIMIT :limit",
           nativeQuery = true)
    List<OutboxEvent> findUnpublished(int limit);
}

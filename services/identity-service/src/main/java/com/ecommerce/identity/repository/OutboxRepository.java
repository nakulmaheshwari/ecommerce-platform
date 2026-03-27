package com.ecommerce.identity.repository;

import com.ecommerce.identity.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
        SELECT * FROM outbox_events
        WHERE published = FALSE
        ORDER BY created_at ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<OutboxEvent> findUnpublished(int limit);
}

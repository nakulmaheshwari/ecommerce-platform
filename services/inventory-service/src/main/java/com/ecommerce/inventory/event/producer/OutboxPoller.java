package com.ecommerce.inventory.event.producer;

import com.ecommerce.inventory.domain.OutboxEvent;
import com.ecommerce.inventory.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Background worker that implements the "Relay" part of the Transactional Outbox pattern.
 * 
 * It periodically polls the database for unpublished events and attempts to 
 * forward them to Kafka. This ensures that even if Kafka is down, events are 
 * never lost, as they are part of the same DB transaction as the stock update.
 * 
 * RELIABILITY MODEL:
 * 1. InventoryService writes to DB + Outbox table (Atomic Transaction).
 * 2. OutboxPoller reads from Outbox table.
 * 3. OutboxPoller sends to Kafka.
 * 4. OutboxPoller marks as 'published' in DB.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Maps internal event types to physical Kafka topic names.
     */
    private static final Map<String, String> TOPIC_MAP = Map.of(
        "inventory.reserved",            "inventory-reserved",
        "inventory.reservation-failed",  "inventory-reservation-failed"
    );

    /**
     * Polls the outbox table every 500ms for pending events.
     * Uses a fixed limit (50) to prevent memory issues if the outbox has backed up.
     */
    @Scheduled(fixedDelay = 500)
    @Transactional
    public void pollAndPublish() {
        var events = outboxRepository.findUnpublished(50);
        if (events.isEmpty()) return;

        for (OutboxEvent event : events) {
            try {
                String topic = TOPIC_MAP.getOrDefault(event.getEventType(),
                    event.getEventType().replace(".", "-"));
                
                // Synchronously wait for Kafka acknowledgement (max 5s) 
                // before marking as published to ensure At-Least-Once delivery.
                kafkaTemplate.send(topic, event.getAggregateId(),
                    objectMapper.writeValueAsString(event.getPayload()))
                    .get(5, TimeUnit.SECONDS);

                event.setPublished(true);
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Outbox publish failed id={}. Will retry in next poll.", event.getId(), e);
            }
        }
    }
}

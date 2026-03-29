package com.ecommerce.order.event.producer;

import com.ecommerce.order.domain.OutboxEvent;
import com.ecommerce.order.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Topic routing map.
     * The eventType in the outbox row maps to a Kafka topic name.
     * The order-placed event goes to "order-placed" topic.
     * Inventory and Notification services listen to their respective topics.
     */
    private static final Map<String, String> TOPIC_MAP = Map.of(
        "order.placed",             "order-placed",
        "order.cancelled",          "order-cancelled",
        "order.shipped",            "order-shipped",
        "notification.triggered",   "notification-triggered"
    );

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void pollAndPublish() {
        var events = outboxRepository.findUnpublished(100);
        if (events.isEmpty()) return;

        log.debug("Publishing {} outbox events", events.size());

        for (OutboxEvent event : events) {
            try {
                String topic = TOPIC_MAP.getOrDefault(
                    event.getEventType(),
                    event.getEventType().replace(".", "-")
                );

                // Partition key = aggregateId (orderId)
                // All events for the same order go to the same partition
                // This ensures ordering: order.placed always arrives before
                // order.cancelled for the same order
                kafkaTemplate.send(
                    topic,
                    event.getAggregateId().toString(),
                    objectMapper.writeValueAsString(event.getPayload())
                ).get(5, TimeUnit.SECONDS);

                event.setPublished(true);
                outboxRepository.save(event);

            } catch (Exception e) {
                log.error("Outbox publish failed eventId={} type={}",
                    event.getId(), event.getEventType(), e);
                // Not marked published — will retry on next poll (500ms)
            }
        }
    }
}

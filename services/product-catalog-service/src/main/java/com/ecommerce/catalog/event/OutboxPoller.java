package com.ecommerce.catalog.event;

import com.ecommerce.catalog.domain.OutboxEvent;
import com.ecommerce.catalog.repository.OutboxRepository;
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

    private static final Map<String, String> TOPIC_MAP = Map.of(
        "product.created",   "product-created",
        "product.published", "product-published",
        "product.updated",   "product-updated"
    );

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void pollAndPublish() {
        var events = outboxRepository.findUnpublished(50);
        if (events.isEmpty()) return;

        for (OutboxEvent event : events) {
            try {
                String topic = TOPIC_MAP.getOrDefault(event.getEventType(),
                    event.getEventType().replace(".", "-"));
                kafkaTemplate.send(topic,
                    event.getAggregateId().toString(),
                    objectMapper.writeValueAsString(event.getPayload()))
                    .get(5, TimeUnit.SECONDS);

                event.setPublished(true);
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Outbox publish failed id={}", event.getId(), e);
            }
        }
    }
}

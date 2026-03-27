package com.ecommerce.identity.event;

import com.ecommerce.identity.domain.OutboxEvent;
import com.ecommerce.identity.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {
/*
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> EVENT_TOPIC_MAP = Map.of(
        "user.registered", "user-registered"
    );

    @Scheduled(fixedDelay = 500)  // Every 500ms
    @Transactional
    public void pollAndPublish() {
        var unpublished = outboxRepository.findUnpublished(50);
        if (unpublished.isEmpty()) return;

        log.debug("Processing {} outbox events", unpublished.size());

        for (OutboxEvent event : unpublished) {
            try {
                String topic = EVENT_TOPIC_MAP.getOrDefault(event.getEventType(),
                    event.getEventType().replace(".", "-"));
                String payload = objectMapper.writeValueAsString(event.getPayload());

                // Synchronous send — we need confirmation before marking published
                kafkaTemplate.send(topic, event.getAggregateId().toString(), payload)
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);

                event.setPublished(true);
                outboxRepository.save(event);
                log.debug("Published outbox event id={} type={}", event.getId(), event.getEventType());

            } catch (Exception e) {
                log.error("Failed to publish outbox event id={} type={}",
                    event.getId(), event.getEventType(), e);
                // Don't mark published — will retry on next poll
            }
        }
    }*/
}

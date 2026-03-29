package com.ecommerce.payment.event.producer;

import com.ecommerce.payment.domain.OutboxEvent;
import com.ecommerce.payment.repository.OutboxRepository;
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
        "payment.succeeded",      "payment-succeeded",
        "payment.failed",         "payment-failed",
        "payment.refunded",       "payment-refunded",
        "notification.triggered", "notification-triggered"
    );

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void pollAndPublish() {
        var events = outboxRepository.findUnpublished(100);
        if (events.isEmpty()) return;

        for (OutboxEvent event : events) {
            try {
                String topic = TOPIC_MAP.getOrDefault(
                    event.getEventType(),
                    event.getEventType().replace(".", "-"));

                kafkaTemplate.send(
                    topic,
                    event.getAggregateId().toString(),
                    objectMapper.writeValueAsString(event.getPayload())
                ).get(5, TimeUnit.SECONDS);

                event.setPublished(true);
                outboxRepository.save(event);

            } catch (Exception e) {
                log.error("Outbox publish failed id={} type={}",
                    event.getId(), event.getEventType(), e);
            }
        }
    }
}

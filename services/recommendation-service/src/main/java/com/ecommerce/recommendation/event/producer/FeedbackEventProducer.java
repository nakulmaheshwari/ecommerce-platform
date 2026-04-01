package com.ecommerce.recommendation.event.producer;

import com.ecommerce.recommendation.api.dto.FeedbackRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedbackEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;

    private static final String TOPIC = "rec-feedback";

    // Fire-and-forget — never blocks the HTTP response
    public void publishFeedback(FeedbackRequest request, UUID userId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("feedbackId",         UUID.randomUUID().toString());
            payload.put("recommendationId",   request.recommendationId());
            payload.put("userId",             userId != null ? userId.toString() : null);
            payload.put("productId",          request.productId());
            payload.put("recommendationType", request.recommendationType());
            payload.put("action",             request.action().name());
            payload.put("position",           request.position());
            payload.put("sessionId",          request.sessionId());
            payload.put("occurredAt",         Instant.now().toString());

            String json = objectMapper.writeValueAsString(payload);
            String key  = userId != null ? userId.toString() : request.sessionId();

            kafkaTemplate.send(TOPIC, key, json).whenComplete((result, ex) -> {
                if (ex != null)
                    log.warn("Feedback publish failed recId={}: {}", request.recommendationId(), ex.getMessage());
            });

        } catch (Exception e) {
            log.error("Feedback serialisation failed", e);
        }
    }
}

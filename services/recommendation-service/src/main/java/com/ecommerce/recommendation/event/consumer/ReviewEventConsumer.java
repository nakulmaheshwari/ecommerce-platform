package com.ecommerce.recommendation.event.consumer;

import com.ecommerce.recommendation.config.EventSignalWeights;
import com.ecommerce.recommendation.domain.UserProductInteraction;
import com.ecommerce.recommendation.repository.InteractionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewEventConsumer {

    private final InteractionRepository interactionRepository;
    private final ObjectMapper          objectMapper;

    @KafkaListener(
        topics           = "review-submitted",
        groupId          = "rec-service-review-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode p       = objectMapper.readTree(record.value());
            String productId = p.path("productId").asText(null);
            String userIdStr = p.path("userId").asText(null);
            int rating       = p.path("rating").asInt(3);
            boolean verified = p.path("verified").asBoolean(false);

            if (productId == null || userIdStr == null || rating == 3) {
                ack.acknowledge();
                return; // neutral review — no signal
            }

            // 5★ = +1.0, 4★ = +0.5, 2★ = -0.5, 1★ = -1.0
            double raw = EventSignalWeights.reviewRatingToPreference(rating).doubleValue();
            double weight = verified ? raw * 1.2 : raw; // verified reviews carry more weight

            interactionRepository.save(UserProductInteraction.builder()
                .userId(UUID.fromString(userIdStr))
                .productId(UUID.fromString(productId))
                .eventType(rating >= 4 ? "review_positive" : "review_negative")
                .implicitScore(BigDecimal.valueOf(weight))
                .occurredAt(Instant.now())
                .build());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process review-submitted event", e);
        }
    }
}

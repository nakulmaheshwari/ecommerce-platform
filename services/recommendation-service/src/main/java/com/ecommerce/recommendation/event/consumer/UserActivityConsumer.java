package com.ecommerce.recommendation.event.consumer;

import com.ecommerce.recommendation.config.EventSignalWeights;
import com.ecommerce.recommendation.domain.UserProductInteraction;
import com.ecommerce.recommendation.repository.InteractionRepository;
import com.ecommerce.recommendation.service.PersonalisationService;
import com.ecommerce.recommendation.service.TrendingService;
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
public class UserActivityConsumer {

    private final InteractionRepository  interactionRepository;
    private final PersonalisationService personalisationService;
    private final TrendingService        trendingService;
    private final ObjectMapper           objectMapper;

    @KafkaListener(
        topics           = "user-activity",
        groupId          = "rec-service-activity-group",
        containerFactory = "activityListenerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String eventId = "unknown";
        try {
            JsonNode p        = objectMapper.readTree(record.value());
            eventId           = p.path("eventId").asText("unknown");
            String eventType  = p.path("eventType").asText();
            String userIdStr  = p.path("userId").asText(null);
            String sessionId  = p.path("sessionId").asText(null);
            String productId  = p.path("productId").asText(null);
            String categoryId = p.path("categoryId").asText(null);
            String brand      = p.path("brand").asText(null);
            String deviceType = p.path("deviceType").asText(null);
            Instant occurredAt = parseInstant(p.path("occurredAt").asText());

            BigDecimal weight = EventSignalWeights.weightOf(eventType);

            // PostgreSQL: persist for Spark training — must succeed before ack
            if (userIdStr != null && productId != null && !productId.isBlank()) {
                interactionRepository.save(UserProductInteraction.builder()
                    .userId(UUID.fromString(userIdStr))
                    .productId(UUID.fromString(productId))
                    .sessionId(sessionId != null ? UUID.fromString(sessionId) : null)
                    .eventType(eventType)
                    .implicitScore(weight)
                    .deviceType(deviceType)
                    .occurredAt(occurredAt)
                    .build());
            }

            // Redis: real-time feature updates (best-effort — failure logs but doesn't block ack)
            try {
                switch (eventType) {
                    case "product_viewed" -> {
                        if (productId != null && userIdStr != null)
                            personalisationService.recordProductView(userIdStr, productId);
                        if (productId != null)
                            trendingService.incrementTrendingScore(productId, 0.3, categoryId);
                        if (userIdStr != null && categoryId != null)
                            personalisationService.updateCategoryAffinity(userIdStr, categoryId, 0.2);
                    }
                    case "product_detail_dwell" -> {
                        if (productId != null)
                            trendingService.incrementTrendingScore(productId, 0.5, categoryId);
                    }
                    case "product_added_to_cart" -> {
                        if (productId != null)
                            trendingService.incrementTrendingScore(productId, 1.0, categoryId);
                        if (userIdStr != null && categoryId != null)
                            personalisationService.updateCategoryAffinity(userIdStr, categoryId, 0.5);
                        if (userIdStr != null && brand != null)
                            personalisationService.updateBrandAffinity(userIdStr, brand, 0.5);
                    }
                    case "search_result_clicked" -> {
                        if (productId != null)
                            trendingService.incrementTrendingScore(productId, 0.5, categoryId);
                    }
                    case "wishlist_added" -> {
                        if (userIdStr != null && categoryId != null)
                            personalisationService.updateCategoryAffinity(userIdStr, categoryId, 0.7);
                        if (userIdStr != null && brand != null)
                            personalisationService.updateBrandAffinity(userIdStr, brand, 0.6);
                    }
                    default -> {}
                }
            } catch (Exception redisEx) {
                log.warn("Redis update failed for eventType={} — non-critical, continuing", eventType, redisEx);
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process user-activity event eventId={}", eventId, e);
            // No ack → retry → DLQ after max retries
        }
    }

    private Instant parseInstant(String v) {
        if (v == null || v.isBlank()) return Instant.now();
        try { return Instant.parse(v); } catch (Exception e) { return Instant.now(); }
    }
}

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

import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final InteractionRepository  interactionRepository;
    private final PersonalisationService personalisationService;
    private final TrendingService        trendingService;
    private final ObjectMapper           objectMapper;

    @KafkaListener(
        topics           = "order-placed",
        groupId          = "rec-service-order-group",
        containerFactory = "orderListenerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String orderId = "unknown";
        try {
            JsonNode p       = objectMapper.readTree(record.value());
            orderId          = p.path("orderId").asText("unknown");
            String userIdStr = p.path("userId").asText(null);
            JsonNode items   = p.path("items");
            Instant placedAt = parseInstant(p.path("occurredAt").asText());

            if (userIdStr == null || !items.isArray()) {
                ack.acknowledge();
                return;
            }

            UUID userId = UUID.fromString(userIdStr);
            List<UserProductInteraction> interactions = new ArrayList<>();

            for (JsonNode item : items) {
                String productId  = item.path("productId").asText(null);
                String categoryId = item.path("categoryId").asText(null);
                String brand      = item.path("brand").asText(null);
                if (productId == null || productId.isBlank()) continue;

                interactions.add(UserProductInteraction.builder()
                    .userId(userId)
                    .productId(UUID.fromString(productId))
                    .eventType("order_placed")
                    .implicitScore(EventSignalWeights.weightOf("order_placed")) // 3.0
                    .occurredAt(placedAt)
                    .build());

                trendingService.recordPurchaseForTrending(productId, categoryId);

                if (categoryId != null && !categoryId.isBlank())
                    personalisationService.updateCategoryAffinity(userIdStr, categoryId, 1.0);
                if (brand != null && !brand.isBlank())
                    personalisationService.updateBrandAffinity(userIdStr, brand, 1.0);
            }

            if (!interactions.isEmpty()) {
                interactionRepository.saveAll(interactions);
                log.info("Recorded {} purchase interactions userId={} orderId={}",
                    interactions.size(), userId, orderId);
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process order-placed orderId={}", orderId, e);
        }
    }

    private Instant parseInstant(String v) {
        if (v == null || v.isBlank()) return Instant.now();
        try { return Instant.parse(v); } catch (Exception e) { return Instant.now(); }
    }
}

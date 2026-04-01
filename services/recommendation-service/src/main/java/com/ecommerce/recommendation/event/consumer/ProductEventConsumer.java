package com.ecommerce.recommendation.event.consumer;

import com.ecommerce.recommendation.service.ItemRecommendationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventConsumer {

    private final ItemRecommendationService itemRecommendationService;
    private final ObjectMapper              objectMapper;

    // product-updated: invalidate content-based similarity cache
    // I2I_CF and FBT are NOT invalidated — they depend on behaviour, not attributes
    @KafkaListener(
        topics           = "product-updated",
        groupId          = "rec-service-product-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleProductUpdated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode p       = objectMapper.readTree(record.value());
            String productId = p.path("productId").asText(null);
            if (productId != null) {
                itemRecommendationService.invalidateProductCache(productId);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process product-updated", e);
        }
    }
}

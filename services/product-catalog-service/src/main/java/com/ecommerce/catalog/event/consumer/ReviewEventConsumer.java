package com.ecommerce.catalog.event.consumer;

import com.ecommerce.catalog.service.ProductService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes review events from review-service and updates product ratings.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewEventConsumer {

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "review-submitted",
        groupId = "product-catalog-review-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleReviewSubmitted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID productId = UUID.fromString(payload.get("productId").asText());
            int rating = payload.get("rating").asInt();

            log.info("Processing review-submitted event for productId={} rating={}", productId, rating);
            
            productService.updateRating(productId, rating);
            
            ack.acknowledge();
            log.info("Successfully updated rating for productId={}", productId);
        } catch (Exception e) {
            log.error("Failed to process review-submitted event", e);
            // Non-ack triggers retry
        }
    }
}

package com.ecommerce.catalog.event.consumer;

import com.ecommerce.catalog.repository.ProductRepository;
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
 * Consumes inventory status changed events and updates product availability.
 * This ensures the Catalog Service has a near real-time denormalized 'available'
 * flag for high-performance filtering in recommendations and search.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "inventory.status-changed",
        groupId = "product-catalog-inventory-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleInventoryStatusChanged(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID productId = UUID.fromString(payload.get("productId").asText());
            boolean available = payload.get("available").asBoolean();
            String sku = payload.get("sku").asText();

            log.info("Processing inventory.status-changed: productId={}, sku={}, available={}", 
                productId, sku, available);
            
            int updatedRows = productRepository.updateProductAvailability(productId, available);
            
            if (updatedRows > 0) {
                log.info("Successfully updated availability for productId={}", productId);
            } else {
                log.warn("No product found for productId={} to update availability", productId);
            }
            
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process inventory.status-changed event", e);
            // Non-ack triggers retry according to Kafka container config
        }
    }
}

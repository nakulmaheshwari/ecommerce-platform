package com.ecommerce.inventory.event.consumer;

import com.ecommerce.inventory.service.InventoryService;
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
 * Kafka Consumer for events related to the Product Lifecycle.
 * 
 * Ensures that the Inventory service is always in sync with the Product Catalog 
 * as new items are added to the store.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventConsumer {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    /**
     * Consumes product.created events published by the Catalog service.
     * Creates an initial zero-stock inventory record for the product's SKU
     * so the reservation flow works from day one.
     *
     * Topic: product-created
     * Producer: product-catalog-service
     * Payload: { productId, sku, name, categoryId, pricePaise }
     */
    @KafkaListener(
        topics = "product-created",
        groupId = "inventory-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleProductCreated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String sku = "unknown";
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID productId = UUID.fromString(payload.get("productId").asText());
            sku = payload.get("sku").asText();

            log.info("Processing product.created event productId={} sku={}", productId, sku);

            // Create placeholder record with 0 stock
            inventoryService.initializeStock(productId, sku);
            
            // Mark as processed
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process product.created event sku={}. Cause: {}", sku, e.getMessage());
            // DO NOT ACK - Spring Kafka will retry based on BackOff policy
        }
    }

    /**
     * Dead Letter Queue for failed product creation events.
     */
    @KafkaListener(
        topics = "product-created.DLQ",
        groupId = "inventory-service-dlq-group"
    )
    public void handleDlq(ConsumerRecord<String, String> record) {
        log.error("CRITICAL: product.created message could not be processed after retries. Topic={} Key={} Value={}",
            record.topic(), record.key(), record.value());
    }
}

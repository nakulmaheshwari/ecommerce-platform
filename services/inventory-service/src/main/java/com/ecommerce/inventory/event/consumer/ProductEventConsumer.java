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
     * Producer: product-catalog-service OutboxPoller
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

            inventoryService.initializeStock(productId, sku);
            ack.acknowledge();

            log.info("Initialized inventory for sku={}", sku);
        } catch (Exception e) {
            log.error("Failed to process product.created event sku={}", sku, e);
            // Do NOT ack — Spring Kafka will retry (3x), then route to DLQ
        }
    }

    // Catch messages that failed all retries
    @KafkaListener(
        topics = "product-created.DLQ",
        groupId = "inventory-service-dlq-group"
    )
    public void handleDlq(ConsumerRecord<String, String> record) {
        log.error("DLQ: product.created message could not be processed topic={} key={} value={}",
            record.topic(), record.key(), record.value());
        // In production: alert/write to a dead-letter tracking table here
    }
}

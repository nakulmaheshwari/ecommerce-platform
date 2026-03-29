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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "order-placed",
        groupId = "inventory-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderPlaced(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String orderId = "unknown";
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            orderId = payload.get("orderId").asText();

            log.info("Processing order-placed event orderId={}", orderId);

            // Build sku -> quantity map from the event payload
            Map<String, Integer> skuQty = new HashMap<>();
            payload.get("items").forEach(item ->
                skuQty.put(
                    item.get("skuId").asText(),
                    item.get("quantity").asInt()
                )
            );

            inventoryService.reserveStock(UUID.fromString(orderId), skuQty);
            ack.acknowledge(); // Commit offset ONLY after successful processing

        } catch (Exception e) {
            log.error("Failed to process order-placed event orderId={}", orderId, e);
            // Do NOT acknowledge — message will be redelivered
            // After max retries, Spring Kafka sends to DLQ automatically
        }
    }

    @KafkaListener(
        topics = "payment-succeeded",
        groupId = "inventory-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentSucceeded(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID orderId = UUID.fromString(payload.get("orderId").asText());

            log.info("Confirming inventory for orderId={}", orderId);
            inventoryService.confirmOrderReservations(orderId);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process payment-succeeded event", e);
        }
    }

    @KafkaListener(
        topics = "payment-failed",
        groupId = "inventory-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID orderId = UUID.fromString(payload.get("orderId").asText());

            log.info("Releasing inventory for orderId={} reason=payment-failed", orderId);
            inventoryService.releaseOrderReservations(orderId);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process payment-failed event", e);
        }
    }

    // DLQ handler — alert fires when messages land here
    @KafkaListener(
        topics = {"order-placed.DLQ", "payment-succeeded.DLQ", "payment-failed.DLQ"},
        groupId = "inventory-service-dlq-group"
    )
    public void handleDlq(ConsumerRecord<String, String> record) {
        log.error("DLQ message received topic={} key={} value={}",
            record.topic(), record.key(), record.value());
        // In production: alert PagerDuty, write to dead-letter DB table for manual review
    }
}

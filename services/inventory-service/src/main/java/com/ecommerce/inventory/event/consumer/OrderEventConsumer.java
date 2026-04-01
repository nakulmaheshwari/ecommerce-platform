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

/**
 * Kafka Consumer for events related to the Order Lifecycle.
 * 
 * This class coordinates the physical stock "locking" mechanism in response to 
 * business events from the Order and Payment services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    /**
     * Responds to new orders being placed in the system.
     * Transitions stock from the 'available' pool to the 'reserved' pool.
     * 
     * Topic: order-placed
     * Producer: order-service
     */
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

            log.info("Received order-placed for orderId={}. Attempting reservation...", orderId);

            Map<String, Integer> skuQty = new HashMap<>();
            payload.get("items").forEach(item ->
                skuQty.put(
                    item.get("skuId").asText(),
                    item.get("quantity").asInt()
                )
            );

            // Attempt to hold the stock
            inventoryService.reserveStock(UUID.fromString(orderId), skuQty);
            
            // Commit offsets so this message isn't processed again
            ack.acknowledge(); 

        } catch (Exception e) {
            log.error("Failed to process order-placed event orderId={}. Cause: {}", orderId, e.getMessage());
            // DO NOT ACK - This will trigger Kafka's retry mechanism (configured in KafkaConfig)
        }
    }

    /**
     * Finalizes the inventory removal once payment is confirmed.
     * 
     * Topic: payment-succeeded
     * Producer: payment-service
     */
    @KafkaListener(
        topics = "payment-succeeded",
        groupId = "inventory-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentSucceeded(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID orderId = UUID.fromString(payload.get("orderId").asText());

            log.info("Payment success for order {}. Confirming reservations.", orderId);
            inventoryService.confirmOrderReservations(orderId);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process payment-succeeded event", e);
        }
    }

    /**
     * Returns reserved stock to the available pool if payment fails.
     * 
     * Topic: payment-failed
     * Producer: payment-service
     */
    @KafkaListener(
        topics = "payment-failed",
        groupId = "inventory-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID orderId = UUID.fromString(payload.get("orderId").asText());

            log.info("Payment failed for order {}. Releasing reserved stock.", orderId);
            inventoryService.releaseOrderReservations(orderId);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process payment-failed event", e);
        }
    }

    /**
     * Safety net for messages that cannot be processed after multiple retries.
     * These messages are routed to topics ending in '.DLQ'.
     */
    @KafkaListener(
        topics = {"order-placed.DLQ", "payment-succeeded.DLQ", "payment-failed.DLQ"},
        groupId = "inventory-service-dlq-group"
    )
    public void handleDlq(ConsumerRecord<String, String> record) {
        log.error("CRITICAL: Message landed in Inventory DLQ. Topic={} Key={} Value={}",
            record.topic(), record.key(), record.value());
        // Potential action: Write to a specialized 'DeadLetter' table for manual intervention.
    }
}

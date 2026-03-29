package com.ecommerce.shipping.event.consumer;

import com.ecommerce.shipping.service.ShippingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Shipping Service listens for payment-succeeded.
 *
 * Only after payment is confirmed do we:
 * 1. Know money has moved
 * 2. Know inventory is reserved
 * 3. Know it's safe to book a carrier
 *
 * We never ship before payment — hence listening to payment-succeeded,
 * NOT order-placed or order-confirmed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final ShippingService shippingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "payment-succeeded",
        groupId = "shipping-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentSucceeded(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID orderId = UUID.fromString(payload.get("orderId").asText());
            UUID userId  = UUID.fromString(payload.get("userId").asText());

            // In production: include shipping address in payment event
            // or fetch via Feign from Order Service
            Map<String, Object> address = Map.of(
                "note", "Fetch from order service in production"
            );

            log.info("Creating shipment for orderId={}", orderId);
            shippingService.createShipment(orderId, userId, address);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process payment-succeeded in shipping service", e);
        }
    }
}

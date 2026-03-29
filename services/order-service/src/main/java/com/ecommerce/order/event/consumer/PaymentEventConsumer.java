package com.ecommerce.order.event.consumer;

import com.ecommerce.order.service.OrderService;
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
 * Payment event consumers.
 *
 * These listeners react to events published by the Payment Service.
 * The Order Service doesn't call Payment Service directly — it just
 * listens. This decoupling means:
 * - Payment Service can be down and orders still get placed
 * - Payment Service can retry independently
 * - No timeout waiting for payment in the order placement flow
 *
 * Manual acknowledgment (Acknowledgment ack) means:
 * - Offset is committed ONLY after successful processing
 * - If processing fails (exception thrown), message is redelivered
 * - After 3 retries, message goes to DLQ
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "payment-succeeded",
        groupId = "order-service-payment-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentSucceeded(
            ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID orderId   = UUID.fromString(payload.get("orderId").asText());
            UUID paymentId = UUID.fromString(payload.get("paymentId").asText());

            log.info("Received payment.succeeded for orderId={}", orderId);
            orderService.handlePaymentSucceeded(orderId, paymentId);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process payment.succeeded event", e);
            // Don't ack — will retry, then DLQ
        }
    }

    @KafkaListener(
        topics = "payment-failed",
        groupId = "order-service-payment-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentFailed(
            ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID orderId     = UUID.fromString(payload.get("orderId").asText());
            String reason    = payload.path("failureReason").asText("Unknown");

            log.info("Received payment.failed for orderId={} reason={}", orderId, reason);
            orderService.handlePaymentFailed(orderId, reason);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process payment.failed event", e);
        }
    }

    // DLQ handler — alert fires here
    @KafkaListener(
        topics = {"payment-succeeded.DLQ", "payment-failed.DLQ"},
        groupId = "order-service-dlq-group"
    )
    public void handleDlq(ConsumerRecord<String, String> record) {
        log.error("CRITICAL: DLQ message received topic={} key={} — manual intervention required",
            record.topic(), record.key());
        // In production: PagerDuty alert + write to dead_letter_orders table
    }
}

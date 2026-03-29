package com.ecommerce.payment.event.consumer;

import com.ecommerce.payment.api.dto.InitiatePaymentRequest;
import com.ecommerce.payment.service.PaymentService;
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
public class OrderEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "order-placed",
        groupId = "payment-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderPlaced(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String orderId = "unknown";
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            orderId          = payload.get("orderId").asText();
            String userId    = payload.get("userId").asText();
            long amountPaise = payload.get("totalPaise").asLong();

            log.info("Received order.placed orderId={} amountPaise={}", orderId, amountPaise);

            InitiatePaymentRequest request = new InitiatePaymentRequest(
                UUID.fromString(orderId),
                UUID.fromString(userId),
                amountPaise,
                UUID.fromString(orderId), // Using orderId as idempotency key
                "INR"
            );

            paymentService.initiatePayment(request);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process order.placed event orderId={}", orderId, e);
        }
    }

    @KafkaListener(
        topics = "order-placed.DLQ",
        groupId = "payment-service-dlq-group"
    )
    public void handleDlq(ConsumerRecord<String, String> record) {
        log.error("CRITICAL DLQ: Failed to initiate payment for order key={} value={}",
            record.key(), record.value());
    }
}

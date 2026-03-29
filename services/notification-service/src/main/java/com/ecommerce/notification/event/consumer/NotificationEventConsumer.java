package com.ecommerce.notification.event.consumer;

import com.ecommerce.notification.service.NotificationService;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * Consumes notification-triggered events from ALL services (fan-in pattern).
 *
 * Each producing service emits the same schema:
 * {
 *   "userId":         "uuid",
 *   "channel":        "EMAIL",
 *   "templateId":     "order-confirmed-v1",
 *   "templateVars":   { "orderId": "...", "totalRupees": "..." },
 *   "recipientEmail": "user@example.com"
 * }
 *
 * This service handles ALL notification types without knowing business logic.
 * The producer packs all template variables; we just route and deliver.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "notification-triggered",
        groupId = "notification-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleNotificationTriggered(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        try {
            JsonNode payload = objectMapper.readTree(record.value());

            UUID userId       = UUID.fromString(payload.get("userId").asText());
            String channel    = payload.get("channel").asText("EMAIL");
            String templateId = payload.get("templateId").asText();

            Map<String, String> templateVars = new HashMap<>();
            JsonNode varsNode = payload.path("templateVars");
            if (!varsNode.isMissingNode()) {
                templateVars = objectMapper.convertValue(varsNode, new TypeReference<>() {});
            }

            String recipient = payload.path("recipientEmail").asText("");
            if (recipient.isBlank()) {
                recipient = payload.path("recipientPhone").asText("");
            }

            String referenceId = templateVars.getOrDefault("orderId",
                templateVars.getOrDefault("userId", userId.toString()));

            log.info("Processing notification userId={} templateId={} channel={}",
                userId, templateId, channel);

            notificationService.processNotification(
                userId, channel, templateId, templateVars, recipient, referenceId);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process notification-triggered event", e);
            // Don't ack — Kafka will redeliver, eventually goes to DLQ
        }
    }

    @KafkaListener(
        topics = "user-registered",
        groupId = "notification-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserRegistered(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        try {
            JsonNode payload = objectMapper.readTree(record.value());
            UUID userId    = UUID.fromString(payload.get("userId").asText());
            String email   = payload.get("email").asText();
            String firstName = payload.path("firstName").asText("there");

            notificationService.processNotification(
                userId, "EMAIL", "user-registered-v1",
                Map.of("firstName", firstName, "email", email),
                email, userId.toString());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process user-registered event", e);
        }
    }

    @KafkaListener(
        topics = "notification-triggered.DLQ",
        groupId = "notification-service-dlq-group"
    )
    public void handleDlq(ConsumerRecord<String, String> record) {
        log.error("Notification DLQ: key={} value={}", record.key(), record.value());
    }
}

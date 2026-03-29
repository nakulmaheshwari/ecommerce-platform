package com.ecommerce.user.event;

import com.ecommerce.user.service.UserService;
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
public class UserRegisteredConsumer {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "user-registered",
        groupId = "user-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserRegistered(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String keycloakId = "unknown";
        try {
            JsonNode payload  = objectMapper.readTree(record.value());
            keycloakId        = payload.get("userId").asText();
            String email      = payload.get("email").asText();
            String firstName  = payload.get("firstName").asText();
            String lastName   = payload.get("lastName").asText();
            String phone      = payload.path("phoneNumber").asText(null);

            log.info("Creating profile for keycloakId={} email={}",
                keycloakId, email);

            userService.createProfile(
                UUID.fromString(keycloakId),
                email, firstName, lastName, phone
            );

            ack.acknowledge();
            log.info("Profile created successfully keycloakId={}", keycloakId);

        } catch (Exception e) {
            log.error("Failed to create profile keycloakId={}", keycloakId, e);
        }
    }

    @KafkaListener(
        topics = "user-registered.DLQ",
        groupId = "user-service-dlq-group"
    )
    public void handleDlq(ConsumerRecord<String, String> record) {
        log.error("CRITICAL: Failed to create user profile for key={} — manual intervention needed",
            record.key());
    }
}

package com.ecommerce.recommendation.event.consumer;

import com.ecommerce.recommendation.service.PersonalisationService;
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
public class GdprConsumer {

    private final PersonalisationService personalisationService;
    private final ObjectMapper           objectMapper;

    @KafkaListener(
        topics           = "user-deleted",
        groupId          = "rec-service-gdpr-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserDeleted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode p       = objectMapper.readTree(record.value());
            String userIdStr = p.path("userId").asText(null);
            if (userIdStr == null) { ack.acknowledge(); return; }

            personalisationService.eraseUserData(UUID.fromString(userIdStr));
            ack.acknowledge();

        } catch (Exception e) {
            log.error("GDPR erasure failed — user data may persist. No ack — will retry.", e);
            // Deliberately no ack on failure — this is critical data compliance
        }
    }
}

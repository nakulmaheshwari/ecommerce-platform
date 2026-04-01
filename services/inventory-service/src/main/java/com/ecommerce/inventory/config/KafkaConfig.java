package com.ecommerce.inventory.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * Kafka Configuration for the Inventory Service.
 * 
 * Provides centralized settings for:
 * 1. Consumer Factory: Manual acknowledgements, 'read_committed' isolation.
 * 2. Error Handling: DLQ routing for failed messages.
 * 3. Topic Blueprints: Initial partition count and replication factor.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Configures properties for reading from Kafka.
     * Note: 'read_committed' ensures that the Inventory Service only processes 
     * messages from other services (like Order/Payment) that have successfully 
     * committed their DB transactions.
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,     // MUST be false for manual ack
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest",
            ConsumerConfig.ISOLATION_LEVEL_CONFIG,    "read_committed",
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG,   100
        ));
    }

    /**
     * Container factory with built-in retry and DLQ logic.
     * 
     * RETRY STRATEGY:
     * - Fixed delay (1s) between retries.
     * - Total attempts: 3.
     * - After 3 failures: Route message to a <topic>.DLQ topic for manual review.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3); // 3 worker threads per listener
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Standard recoverer for sending failures to a DLQ
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));

        // Skip retries for unrecoverable errors (e.g., malformed JSON)
        errorHandler.addNotRetryableExceptions(
            com.fasterxml.jackson.core.JsonProcessingException.class,
            IllegalArgumentException.class
        );

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // ─── Explicit Topic Beans ────────────────────────────────────────────────
    // Ensures physical Kafka topics are initialized with desired configuration.

    @Bean
    public NewTopic productCreatedTopic() {
        return TopicBuilder.name("product-created")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic productCreatedDlqTopic() {
        return TopicBuilder.name("product-created.DLQ")
            .partitions(1)
            .replicas(1)
            .build();
    }
}

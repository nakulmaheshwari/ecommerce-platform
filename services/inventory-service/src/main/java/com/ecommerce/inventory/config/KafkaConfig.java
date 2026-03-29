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

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,     // Manual ack only
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest",
            ConsumerConfig.ISOLATION_LEVEL_CONFIG,    "read_committed",
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG,   100
        ));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3); // 3 consumer threads
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Retry 3 times with 1s interval, then send to DLQ topic
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));

        // Don't retry deserialization errors — they'll never succeed
        errorHandler.addNotRetryableExceptions(
            com.fasterxml.jackson.core.JsonProcessingException.class,
            IllegalArgumentException.class
        );

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // ─── Topic declarations ──────────────────────────────────────────────────
    // Kafka auto-creates topics if they don't exist, but explicit beans
    // let us control partition count and replication factor from day one.

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

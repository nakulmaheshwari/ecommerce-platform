package com.ecommerce.recommendation.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,        bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,       false,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,        "earliest",
            ConsumerConfig.ISOLATION_LEVEL_CONFIG,          "read_committed",
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG,         100,
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,       30_000,
            ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG,    10_000
        ));
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.ACKS_CONFIG,                   "all",
            ProducerConfig.RETRIES_CONFIG,                3,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,     true
        ));
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // 6 concurrent threads for high-volume user-activity topic
    @Bean("activityListenerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> activityListenerFactory(
            KafkaTemplate<String, String> kafkaTemplate) {
        return buildFactory(kafkaTemplate, 6);
    }

    // 3 threads for order-placed — lower volume, highest signal value
    @Bean("orderListenerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> orderListenerFactory(
            KafkaTemplate<String, String> kafkaTemplate) {
        return buildFactory(kafkaTemplate, 3);
    }

    // Default 2 threads for review, product-updated, user-deleted
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            KafkaTemplate<String, String> kafkaTemplate) {
        return buildFactory(kafkaTemplate, 2);
    }

    private ConcurrentKafkaListenerContainerFactory<String, String> buildFactory(
            KafkaTemplate<String, String> kafkaTemplate, int concurrency) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(concurrency);
        factory.getContainerProperties()
               .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Exponential backoff: 1s → 2s → 4s → 8s → DLQ after 4 retries
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(4);
        backOff.setMaxInterval(8_000L);

        factory.setCommonErrorHandler(new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(
                    record.topic() + ".REC-DLQ", record.partition())),
            backOff));

        return factory;
    }
}

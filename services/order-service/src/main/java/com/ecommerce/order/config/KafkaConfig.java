package com.ecommerce.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Configuration.
 *
 * It is best practice for the PRODUCER to define the topics it owns.
 * Order Service owns the "order-placed", "order-cancelled", and "order-shipped"
 * events, so it creates those topics if they don't exist.
 *
 * Why 3 partitions?
 * To allow 3 instances of the Inventory Service (consumers) to process
 * events in parallel.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic orderPlacedTopic() {
        return TopicBuilder.name("order-placed")
            .partitions(3)
            .replicas(1) // 1 for local dev, 3 for prod
            .build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name("order-cancelled")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic notificationTriggeredTopic() {
        return TopicBuilder.name("notification-triggered")
            .partitions(3)
            .replicas(1)
            .build();
    }
}

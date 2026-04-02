package com.ecommerce.seller.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic sellerRegisteredTopic() {
        return TopicBuilder.name("seller-registered")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic listingApprovedTopic() {
        return TopicBuilder.name("listing-approved")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic settlementCreatedTopic() {
        return TopicBuilder.name("settlement-created")
                .partitions(3)
                .replicas(1)
                .build();
    }
}

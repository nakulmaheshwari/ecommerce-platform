package com.ecommerce.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Entry point for the Inventory Microservice.
 * 
 * CORE FEATURES:
 * - Distributed Stock Management: Atomic reservations and confirmations.
 * - Event-Driven: Consumes Order/Product events and produces Inventory events via Outbox.
 * - Resilient: Transactional Outbox ensures message delivery even if Kafka is down.
 * - Audited: Every stock change is recorded in the inventory_movements table.
 */
@SpringBootApplication(scanBasePackages = {
    "com.ecommerce.inventory",
    "com.ecommerce.common"
})
@EnableDiscoveryClient
@EnableScheduling
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}

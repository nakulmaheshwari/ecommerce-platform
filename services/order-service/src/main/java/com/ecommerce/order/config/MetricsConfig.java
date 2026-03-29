package com.ecommerce.order.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom business metrics for the Order Service.
 *
 * These complement the automatic HTTP metrics Spring Boot exports.
 * They answer BUSINESS questions that HTTP metrics cannot:
 *   - How many orders per second are we processing?
 *   - What is the p99 placement time end-to-end?
 *   - How many orders are being cancelled and why?
 *
 * Usage in OrderService:
 *   ordersCreatedCounter.increment();
 *   orderPlacementTimer.record(() -> { ... });
 *
 * Grafana queries:
 *   rate(ecom_orders_created_total[5m])  → throughput
 *   histogram_quantile(0.99, ecom_order_placement_duration_seconds_bucket)
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter ordersCreatedCounter(MeterRegistry registry) {
        return Counter.builder("ecom.orders.created")
            .description("Total orders successfully created")
            .tag("service", "order-service")
            .register(registry);
    }

    @Bean
    public Counter ordersCancelledCounter(MeterRegistry registry) {
        return Counter.builder("ecom.orders.cancelled")
            .description("Total orders cancelled")
            .tag("service", "order-service")
            .register(registry);
    }

    @Bean
    public Timer orderPlacementTimer(MeterRegistry registry) {
        return Timer.builder("ecom.order.placement.duration")
            .description("Time to place an order end to end")
            .tag("service", "order-service")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }
}

package com.ecommerce.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.ecommerce.shipping", "com.ecommerce.common"})
@EnableDiscoveryClient
@EnableScheduling
public class ShippingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShippingServiceApplication.class, args);
    }
}

package com.ecommerce.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.ecommerce.catalog",
    "com.ecommerce.common"
})
@EnableDiscoveryClient
@EnableScheduling
public class ProductCatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductCatalogApplication.class, args);
    }
}

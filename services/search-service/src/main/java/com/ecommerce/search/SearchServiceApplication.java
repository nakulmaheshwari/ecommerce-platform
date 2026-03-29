package com.ecommerce.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {
    "com.ecommerce.search",
    "com.ecommerce.common"
})
@EnableDiscoveryClient
public class SearchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}

package com.ecommerce.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.ecommerce.review",
    "com.ecommerce.common"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.ecommerce.review.client")
@EnableScheduling
public class ReviewServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }
}

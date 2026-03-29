package com.ecommerce.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {
    "com.ecommerce.cart",
    "com.ecommerce.common"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.ecommerce.cart.client")
public class CartServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}

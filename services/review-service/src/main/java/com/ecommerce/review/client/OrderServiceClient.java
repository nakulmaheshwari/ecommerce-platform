package com.ecommerce.review.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/*
 * OrderServiceClient — checks if a user actually purchased a product.
 *
 * FALLBACK STRATEGY: if Order Service is down, allow the review but
 * set verifiedPurchase = false. This is intentional graceful degradation:
 *
 * Option A (rejected): Block review if Order Service is down.
 *   Problem: outage prevents ALL reviews platform-wide. Bad UX.
 *
 * Option B (this): Allow review, no verified badge.
 *   Review is submitted and visible. Badge is absent — honest.
 *   No customer review abandon due to an unrelated service outage.
 */
@FeignClient(
    name = "order-service",
    fallback = OrderServiceClient.OrderServiceFallback.class
)
public interface OrderServiceClient {

    @GetMapping("/api/v1/internal/orders/verified-purchase")
    @CircuitBreaker(name = "orderService")
    boolean hasUserPurchasedProduct(
        @RequestParam("userId") UUID userId,
        @RequestParam("productId") UUID productId
    );

    @Slf4j
    class OrderServiceFallback implements OrderServiceClient {
        @Override
        public boolean hasUserPurchasedProduct(UUID userId, UUID productId) {
            log.warn("Order service unavailable — setting verifiedPurchase=false " +
                     "for userId={} productId={}", userId, productId);
            return false;
        }
    }
}

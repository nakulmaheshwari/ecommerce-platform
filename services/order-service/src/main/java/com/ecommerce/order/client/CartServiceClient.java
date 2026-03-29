package com.ecommerce.order.client;

import com.ecommerce.order.client.dto.CartItemDto;
import com.ecommerce.common.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(
    name = "cart-service",
    fallback = CartServiceClient.CartFallback.class
)
public interface CartServiceClient {

    /**
     * Fetches cart items for checkout.
     * The Authorization header forwards the user's JWT to Cart Service.
     * Cart Service uses it to identify which user's cart to return.
     */
    @GetMapping("/api/v1/cart/checkout-items")
    @CircuitBreaker(name = "cartService")
    List<CartItemDto> getCartForCheckout(
        @RequestHeader("Authorization") String authHeader);

    /**
     * Clears the cart after order is successfully placed.
     * Called inside the same checkout transaction flow (but after
     * the order DB write — cart clear failure is non-critical).
     */
    @DeleteMapping("/api/v1/cart")
    @CircuitBreaker(name = "cartService")
    void clearCart(@RequestHeader("Authorization") String authHeader);

    @Slf4j
    class CartFallback implements CartServiceClient {
        @Override
        public List<CartItemDto> getCartForCheckout(String authHeader) {
            log.error("Cart service unavailable");
            throw new ServiceUnavailableException("cart-service",
                "Cannot place order while cart service is unavailable");
        }

        @Override
        public void clearCart(String authHeader) {
            // Non-critical — log and continue. Cart will expire via TTL.
            log.warn("Cart service unavailable — cart not cleared. Will expire via TTL.");
        }
    }
}

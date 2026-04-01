package com.ecommerce.review.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Feign client for Product Catalog Service.
 * Used to validate product existence before allowing reviews.
 */
@FeignClient(
    name = "product-catalog-service",
    fallback = ProductCatalogClient.ProductCatalogFallback.class
)
public interface ProductCatalogClient {

    @GetMapping("/api/v1/products/{id}")
    @CircuitBreaker(name = "productCatalogService")
    Object getProduct(@PathVariable("id") UUID productId);

    @Slf4j
    class ProductCatalogFallback implements ProductCatalogClient {
        @Override
        public Object getProduct(UUID productId) {
            log.warn("Product catalog service unavailable — unable to validate productId={}", productId);
            // We return null to indicate we couldn't verify. 
            // The service layer will decide whether to block or allow based on this.
            return null;
        }
    }
}

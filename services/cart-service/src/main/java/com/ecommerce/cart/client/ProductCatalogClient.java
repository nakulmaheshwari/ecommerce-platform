package com.ecommerce.cart.client;

import com.ecommerce.cart.client.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

/**
 * Feign client for Product Catalog Service.
 *
 * @FeignClient(name = "product-catalog-service") — Feign uses Eureka to
 *                   resolve "product-catalog-service" to an actual host:port.
 *                   This is service
 *                   discovery in action. You never hardcode localhost:8082 in
 *                   service code.
 *                   In production, the service might be on any of 8 pods.
 *                   Eureka returns
 *                   a load-balanced address.
 *
 *                   The circuit breaker wraps every call. If Product Catalog is
 *                   down or slow,
 *                   the circuit opens and the fallback fires — preventing
 *                   cascade failure into
 *                   Cart Service.
 */
@FeignClient(name = "product-catalog-service", fallback = ProductCatalogClientFallback.class)
public interface ProductCatalogClient {

    @GetMapping("/api/v1/products/{id}")
    ProductDto getProductById(@PathVariable("id") String productId);

    @GetMapping("/api/v1/products/sku/{sku}")
    ProductDto getProductBySku(@PathVariable("sku") String sku);
}

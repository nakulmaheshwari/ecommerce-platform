package com.ecommerce.recommendation.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@FeignClient(
    name = "product-catalog-service",
    fallback = ProductCatalogClient.ProductCatalogFallback.class
)
public interface ProductCatalogClient {

    // Single batch call per API response — never per-product
    @PostMapping("/api/v1/internal/products/batch")
    @CircuitBreaker(name = "catalogService")
    List<ProductDto> getProductsByIds(@RequestBody List<UUID> productIds);

    record ProductDto(
        UUID    productId,
        String  sku,
        String  name,
        String  slug,
        String  brand,
        String  categoryId,
        String  categoryName,
        String  categorySlug,
        long    pricePaise,
        double  priceRupees,
        long    mrpPaise,
        int     discountPercent,
        double  averageRating,
        int     totalReviews,
        boolean inStock,
        boolean isDigital,
        String  primaryImageUrl,
        String  status
    ) {}

    // Fallback: return shells with only productId — frontend shows loading state
    class ProductCatalogFallback implements ProductCatalogClient {
        @Override
        public List<ProductDto> getProductsByIds(List<UUID> productIds) {
            return productIds.stream()
                .map(id -> new ProductDto(id, null, null, null, null, null,
                    null, null, 0L, 0.0, 0L, 0, 0.0, 0, false, false, null, null))
                .toList();
        }
    }
}

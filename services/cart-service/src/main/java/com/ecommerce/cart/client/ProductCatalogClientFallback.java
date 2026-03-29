package com.ecommerce.cart.client;

import com.ecommerce.cart.client.dto.ProductDto;
import com.ecommerce.common.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback strategy: FAIL FAST, don't silently add items with wrong prices.
 *
 * Alternative considered: return a cached/stale product.
 * Rejected because: adding items with potentially wrong prices creates
 * financial liability. Better to tell the user to try again.
 */
@Component
@Slf4j
public class ProductCatalogClientFallback implements ProductCatalogClient {

    @Override
    public ProductDto getProductById(String productId) {
        log.error("Product catalog unavailable for productId={}", productId);
        throw new ServiceUnavailableException("product-catalog-service",
            "Cannot add to cart while product service is unavailable");
    }

    @Override
    public ProductDto getProductBySku(String sku) {
        log.error("Product catalog unavailable for sku={}", sku);
        throw new ServiceUnavailableException("product-catalog-service",
            "Cannot add to cart while product service is unavailable");
    }
}

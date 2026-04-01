package com.ecommerce.cart.client;

import com.ecommerce.cart.client.dto.InventoryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for Inventory Service to perform availability checks.
 */
@FeignClient(name = "inventory-service", fallback = InventoryClientFallback.class)
public interface InventoryClient {

    @GetMapping("/api/v1/inventory/{skuId}")
    InventoryDto getInventory(@PathVariable("skuId") String skuId);
}

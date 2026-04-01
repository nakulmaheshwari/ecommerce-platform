package com.ecommerce.cart.client;

import com.ecommerce.cart.client.dto.InventoryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for InventoryClient when Inventory Service is unreachable.
 * 
 * DESIGN DECISION: In a fallback scenario (Inventory Service down), we default to 
 * NOT ALLOWING the add-to-cart operation (availableQty = 0) to prevent 
 * over-selling, although a more "optimistic" approach could be chosen 
 * depending on business requirements.
 */
@Component
@Slf4j
public class InventoryClientFallback implements InventoryClient {

    @Override
    public InventoryDto getInventory(String skuId) {
        log.error("Inventory Service is down or timed out. Falling back for SKU: {}", skuId);
        // Default to out of stock for safety during service outage
        return new InventoryDto(skuId, null, 0, true);
    }
}

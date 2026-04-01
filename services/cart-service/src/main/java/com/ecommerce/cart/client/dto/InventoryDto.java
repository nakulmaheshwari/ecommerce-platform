package com.ecommerce.cart.client.dto;

import java.util.UUID;

/**
 * Simplified inventory state for Cart Service availability checks.
 */
public record InventoryDto(
    String skuId,
    UUID productId,
    int availableQty,
    boolean outOfStock
) {}

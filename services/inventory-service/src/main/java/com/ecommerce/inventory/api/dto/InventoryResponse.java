package com.ecommerce.inventory.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Detailed representation of an SKU's inventory state.
 * 
 * @param skuId The identifier for the product variant.
 * @param productId The parent product identifier.
 * @param availableQty Units ready for sale.
 * @param reservedQty Units held for pending orders.
 * @param totalQty Sum of available and reserved units.
 * @param lowStock Boolean flag based on reorderPoint.
 * @param outOfStock Boolean flag (availableQty == 0).
 * @param warehouseId The physical/logical storage location.
 * @param updatedAt Last modification timestamp.
 */
public record InventoryResponse(
    String skuId,
    UUID productId,
    int availableQty,
    int reservedQty,
    int totalQty,
    boolean lowStock,
    boolean outOfStock,
    String warehouseId,
    Instant updatedAt
) {}

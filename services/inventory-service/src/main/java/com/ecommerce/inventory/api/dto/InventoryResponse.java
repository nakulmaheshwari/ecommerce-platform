package com.ecommerce.inventory.api.dto;

import java.time.Instant;
import java.util.UUID;

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

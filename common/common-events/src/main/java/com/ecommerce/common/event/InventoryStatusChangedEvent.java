package com.ecommerce.common.event;

import java.util.UUID;

/**
 * Event published when a product's availability status flips between 
 * available (stock > 0) and out-of-stock (stock = 0).
 * 
 * This enables the Catalog Service to maintain a denormalized 'available'
 * field for high-performance filtering.
 */
public record InventoryStatusChangedEvent(
    UUID productId,
    String sku,
    boolean available
) {}

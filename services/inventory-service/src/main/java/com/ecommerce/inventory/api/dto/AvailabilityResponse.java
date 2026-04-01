package com.ecommerce.inventory.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Aggregated response for multi-SKU availability checks.
 * Used by the Checkout flow to determine if an entire cart can be fulfilled.
 * 
 * @param allAvailable True if EVERY requested SKU has sufficient stock.
 * @param unavailableSkus List of SKU IDs that failed the availability check.
 * @param availableQuantities A snapshot of actual available quantities for each SKU.
 */
public record AvailabilityResponse(
    boolean allAvailable,
    List<String> unavailableSkus,
    Map<String, Integer> availableQuantities
) {}

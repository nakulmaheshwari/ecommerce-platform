package com.ecommerce.inventory.api.dto;

import java.util.List;
import java.util.Map;

public record AvailabilityResponse(
    boolean allAvailable,
    List<String> unavailableSkus,
    Map<String, Integer> availableQuantities
) {}

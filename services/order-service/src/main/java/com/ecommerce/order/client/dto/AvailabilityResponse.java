package com.ecommerce.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AvailabilityResponse(
    boolean allAvailable,
    List<String> unavailableSkus,
    Map<String, Integer> availableQuantities
) {}

package com.ecommerce.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for increasing stock levels for a specific SKU.
 * Typically used for inbound shipments or manual stock corrections.
 * 
 * @param quantity The positive integer amount to add to 'availableQty'.
 * @param notes Contextual information (e.g., "PO-1234 arrived", "Cycle count correction").
 */
public record AddStockRequest(
    @NotNull @Min(1)
    Integer quantity,

    @NotBlank
    String notes
) {}

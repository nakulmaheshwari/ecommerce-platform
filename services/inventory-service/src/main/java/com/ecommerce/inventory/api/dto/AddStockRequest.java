package com.ecommerce.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddStockRequest(
    @NotNull @Min(1)
    Integer quantity,

    @NotBlank
    String notes
) {}

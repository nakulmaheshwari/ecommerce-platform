package com.ecommerce.seller.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUpdateRequest {
    @NotNull
    private Integer quantityChange;

    private String transactionType; // REPLENISHMENT, ADJUSTMENT, etc.
    private String notes;
}

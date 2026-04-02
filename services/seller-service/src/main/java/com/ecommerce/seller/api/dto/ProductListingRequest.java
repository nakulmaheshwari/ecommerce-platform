package com.ecommerce.seller.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class ProductListingRequest {
    @NotNull
    private UUID productId;

    @NotBlank
    private String sku;

    @NotNull
    @Min(1)
    private Long sellingPricePaise;

    @NotNull
    @Min(1)
    private Long mrpPaise;

    private Integer dispatchDays;
    private String shipsFromCity;
    private String shipsFromState;
    
    private String customTitle;
    private String customDescription;

    private Integer initialQuantity;
}

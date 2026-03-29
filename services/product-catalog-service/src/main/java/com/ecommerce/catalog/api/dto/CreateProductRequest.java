package com.ecommerce.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateProductRequest(
    @NotBlank @Size(max = 100)
    String sku,

    @NotBlank @Size(max = 500)
    String name,

    @Size(max = 5000)
    String description,

    @NotNull
    UUID categoryId,

    @Size(max = 150)
    String brand,

    @NotNull @Min(1)
    Long pricePaise,

    @NotNull @Min(1)
    Long mrpPaise,

    @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
    BigDecimal taxPercent,

    Boolean isDigital,

    @Min(0)
    Integer weightGrams,

    @Valid
    List<CreateVariantRequest> variants,

    List<String> imageUrls
) {
    public record CreateVariantRequest(
        @NotBlank String sku,
        @NotBlank String name,
        @NotNull @Min(1) Long pricePaise,
        @NotNull Map<String, String> attributes
    ) {}
}

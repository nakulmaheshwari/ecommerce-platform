package com.ecommerce.cart.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @JsonIgnoreProperties(ignoreUnknown = true) is critical for service-to-service DTOs.
 * If Product Catalog adds a new field tomorrow, Cart Service won't break.
 * Without this annotation, Jackson throws an exception on unknown fields.
 * This is a form of backward compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(
    UUID id,
    String sku,
    String name,
    String brand,
    long pricePaise,
    long mrpPaise,
    String status,
    CategoryDto category,
    List<VariantDto> variants,
    List<ImageDto> images
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryDto(UUID id, String name, String slug) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VariantDto(
        UUID id, String sku, String name,
        long pricePaise, Map<String, String> attributes, boolean isActive
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageDto(
        UUID id, String url, String altText, boolean isPrimary
    ) {}

    // Helper: find the primary image URL
    public String primaryImageUrl() {
        if (images == null || images.isEmpty()) return null;
        return images.stream()
            .filter(ImageDto::isPrimary)
            .findFirst()
            .map(ImageDto::url)
            .orElse(images.get(0).url());
    }

    // Helper: find variant by SKU
    public VariantDto findVariant(String sku) {
        if (variants == null) return null;
        return variants.stream()
            .filter(v -> v.sku().equals(sku))
            .findFirst()
            .orElse(null);
    }
}

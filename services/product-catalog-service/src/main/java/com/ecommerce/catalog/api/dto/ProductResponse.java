package com.ecommerce.catalog.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Serializable — this gets stored in Redis
@Builder
public record ProductResponse(
    UUID id,
    String sku,
    String name,
    String slug,
    String description,
    String brand,
    CategorySummary category,
    long pricePaise,
    long mrpPaise,
    int discountPercent,
    BigDecimal taxPercent,
    String status,
    boolean available,
    List<VariantResponse> variants,
    List<ImageResponse> images,
    Instant publishedAt
) implements Serializable {

    // Convenience: price in rupees for display
    @JsonProperty("priceRupees")
    public double priceRupees() { return pricePaise / 100.0; }

    @JsonProperty("mrpRupees")
    public double mrpRupees() { return mrpPaise / 100.0; }

    public record CategorySummary(UUID id, String name, String slug) implements Serializable {}

    public record VariantResponse(
        UUID id, String sku, String name,
        long pricePaise, Map<String, String> attributes,
        boolean isActive
    ) implements Serializable {}

    public record ImageResponse(
        UUID id, String url, String altText,
        boolean isPrimary, int sortOrder
    ) implements Serializable {}
}

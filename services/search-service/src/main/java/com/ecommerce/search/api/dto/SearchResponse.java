package com.ecommerce.search.api.dto;

import java.util.List;
import java.util.Map;

public record SearchResponse(
    List<ProductSearchResult> hits,
    Map<String, List<FacetEntry>> facets,
    long totalHits,
    int page,
    int size,
    int totalPages,
    boolean hasNext,
    long tookMs,
    List<String> suggestions
) {
    public record FacetEntry(String value, long count) {}

    public record ProductSearchResult(
        String productId,
        String sku,
        String name,
        String brand,
        String categoryName,
        String categorySlug,
        long pricePaise,
        double priceRupees,
        long mrpPaise,
        int discountPercent,
        double averageRating,
        int totalReviews,
        boolean inStock,
        boolean isDigital,
        String primaryImageUrl,
        float relevanceScore,
        List<String> tags
    ) {}
}

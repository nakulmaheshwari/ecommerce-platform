package com.ecommerce.search.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public record SearchRequest(
    String q,
    String categoryId,
    List<String> brands,
    Long minPrice,
    Long maxPrice,
    Double minRating,
    Boolean inStockOnly,
    List<String> tags,
    String sortBy,
    @Min(0) int page,
    @Min(1) @Max(100) int size
) {
    public SearchRequest {
        if (sortBy == null) sortBy = q != null ? "RELEVANCE" : "POPULARITY";
        if (size == 0) size = 20;
    }
}

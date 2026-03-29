package com.ecommerce.catalog.api.dto;

import java.util.List;

public record ProductPageResponse(
    List<ProductResponse> products,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious
) {}

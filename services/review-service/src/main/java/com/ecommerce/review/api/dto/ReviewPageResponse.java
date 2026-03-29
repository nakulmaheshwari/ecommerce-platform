package com.ecommerce.review.api.dto;

import java.util.List;

public record ReviewPageResponse(
    List<ReviewResponse> reviews,
    ReviewAggregateResponse aggregate,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {}

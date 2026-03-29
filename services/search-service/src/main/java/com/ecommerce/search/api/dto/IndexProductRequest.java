package com.ecommerce.search.api.dto;

import java.util.List;
import java.util.Map;

public record IndexProductRequest(
    String productId,
    String sku,
    String name,
    String description,
    String brand,
    String categoryId,
    String categoryName,
    String categorySlug,
    Long pricePaise,
    Long mrpPaise,
    Integer discountPercent,
    String status,
    Double averageRating,
    Integer totalReviews,
    List<String> tags,
    Map<String, List<String>> attributes,
    String primaryImageUrl,
    Boolean inStock,
    Integer stockQuantity,
    Boolean isDigital
) {}

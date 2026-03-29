package com.ecommerce.review.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/*
 * ReviewAggregateResponse — the rating summary shown on product pages.
 *
 * Displayed as:
 *   ⭐ 4.3  2,847 ratings
 *   ⭐⭐⭐⭐⭐  ████████████████ 60%
 *   ⭐⭐⭐⭐    ████████ 25%
 *   ...
 */
public record ReviewAggregateResponse(
    UUID productId,
    int totalReviews,
    BigDecimal averageRating,
    int rating1Count,
    int rating2Count,
    int rating3Count,
    int rating4Count,
    int rating5Count,
    int rating1Percent,
    int rating2Percent,
    int rating3Percent,
    int rating4Percent,
    int rating5Percent
) {}

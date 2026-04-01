package com.ecommerce.recommendation.api.dto;

import com.ecommerce.recommendation.client.ProductCatalogClient;
import java.time.Instant;
import java.util.List;

public record RecommendationResponse(
    List<RecommendationItem> recommendations,
    String  algorithm,
    String  experimentId,
    int     totalCount,
    int     page,
    int     size,
    boolean servedFromCache,
    long    tookMs,
    String  requestId,
    Instant generatedAt
) {
    public record RecommendationItem(
        String productId,
        int    rank,           // 1-based (rank 1 = best)
        double score,          // normalised 0.0-1.0
        String recommendationType,
        String recommendationId, // unique per impression — for click tracking
        ProductCatalogClient.ProductDto product
    ) {}
}

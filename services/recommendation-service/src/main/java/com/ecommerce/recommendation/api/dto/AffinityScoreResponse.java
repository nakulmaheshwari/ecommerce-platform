package com.ecommerce.recommendation.api.dto;

import java.time.Instant;
import java.util.Map;

public record AffinityScoreResponse(
    String userId,
    Map<String, Double> productScores,
    Instant computedAt
) {}

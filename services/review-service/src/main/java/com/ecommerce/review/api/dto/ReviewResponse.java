package com.ecommerce.review.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
    UUID id,
    UUID productId,
    UUID userId,
    String reviewerName,
    int rating,
    String title,
    String body,
    boolean verifiedPurchase,
    String status,
    int helpfulVotes,
    int totalVotes,
    double helpfulnessRatio,
    Instant createdAt,
    Instant updatedAt
) {}

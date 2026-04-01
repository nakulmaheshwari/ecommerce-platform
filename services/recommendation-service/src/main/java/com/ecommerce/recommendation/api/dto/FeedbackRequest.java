package com.ecommerce.recommendation.api.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record FeedbackRequest(
    @NotBlank String recommendationId,
    @NotBlank String productId,
    @NotBlank String recommendationType,
    @NotNull  FeedbackAction action,
    @Min(1) @Max(50) int position,
    String sessionId
) {
    public enum FeedbackAction {
        CLICKED, ADDED_TO_CART, PURCHASED, DISMISSED
    }
}

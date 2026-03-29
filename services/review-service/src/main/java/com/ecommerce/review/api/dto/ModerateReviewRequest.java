package com.ecommerce.review.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ModerateReviewRequest(
    @NotBlank String action,       // APPROVE or REJECT
    String rejectionReason         // Required when action = REJECT
) {}

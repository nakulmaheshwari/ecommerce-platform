package com.ecommerce.review.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReportRequest(
    @NotBlank String reason,    // SPAM, FAKE, OFFENSIVE, IRRELEVANT, OTHER
    String details
) {}

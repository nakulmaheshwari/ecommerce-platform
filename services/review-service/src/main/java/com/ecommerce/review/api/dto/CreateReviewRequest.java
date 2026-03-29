package com.ecommerce.review.api.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreateReviewRequest(

    @NotNull
    UUID productId,

    /*
     * Two layers of validation: @Min/@Max + DB CHECK constraint.
     * Annotation gives a clear error message to the API client.
     * DB constraint is the final safety net.
     */
    @NotNull @Min(1) @Max(5)
    Integer rating,

    @Size(max = 200, message = "Title cannot exceed 200 characters")
    String title,

    /*
     * Minimum 10 characters enforced — "Good" is not a review.
     * Amazon requires 20 chars. We set 10 for flexibility.
     */
    @Size(min = 10, max = 5000,
          message = "Review body must be between 10 and 5000 characters")
    String body
) {}

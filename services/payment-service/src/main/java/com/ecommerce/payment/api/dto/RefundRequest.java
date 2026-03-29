package com.ecommerce.payment.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RefundRequest(
    @NotNull @Min(1)
    Long amountPaise,
    @NotBlank
    String reason,
    String notes
) {}

package com.ecommerce.payment.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InitiatePaymentRequest(
    @NotNull UUID orderId,
    @NotNull UUID userId,
    @NotNull Long amountPaise,
    @NotNull UUID idempotencyKey,
    String currency
) {}

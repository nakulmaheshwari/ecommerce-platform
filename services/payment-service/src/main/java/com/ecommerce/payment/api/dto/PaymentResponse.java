package com.ecommerce.payment.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID orderId,
    String razorpayOrderId,
    String razorpayPaymentId,
    long amountPaise,
    double amountRupees,
    String currency,
    String status,
    String failureCode,
    String failureReason,
    Instant createdAt,
    Instant capturedAt
) {}

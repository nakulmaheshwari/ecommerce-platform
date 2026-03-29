package com.ecommerce.payment.api.dto;

import java.util.UUID;

public record InitiatePaymentResponse(
    UUID paymentId,
    UUID orderId,
    String razorpayOrderId,
    String razorpayKeyId,
    long amountPaise,
    String currency,
    String status,
    String description
) {}

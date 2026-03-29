package com.ecommerce.order.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    UUID userId,
    String status,
    String currency,
    long subtotalPaise,
    double subtotalRupees,
    long discountPaise,
    long taxPaise,
    long deliveryPaise,
    long totalPaise,
    double totalRupees,
    Map<String, Object> shippingAddress,
    List<OrderItemResponse> items,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {
    public record OrderItemResponse(
        UUID id,
        String skuId,
        UUID productId,
        String productName,
        String variantName,
        int quantity,
        long unitPricePaise,
        double unitPriceRupees,
        long lineTotalPaise,
        double lineTotalRupees,
        String imageUrl
    ) {}
}

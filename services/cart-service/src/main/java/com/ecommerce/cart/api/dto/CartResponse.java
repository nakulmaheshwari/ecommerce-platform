package com.ecommerce.cart.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CartResponse(
    UUID userId,
    List<CartItemResponse> items,
    int totalItems,
    long subtotalPaise,
    double subtotalRupees,      // Convenience for frontend
    long totalSavingsPaise,
    double totalSavingsRupees
) {
    public record CartItemResponse(
        String skuId,
        UUID productId,
        String productName,
        String variantName,
        int quantity,
        long pricePaise,
        double priceRupees,
        long mrpPaise,
        int discountPercent,
        long itemTotalPaise,
        double itemTotalRupees,
        String imageUrl,
        String brand,
        Map<String, String> attributes
    ) {}
}

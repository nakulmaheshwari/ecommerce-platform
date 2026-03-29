package com.ecommerce.cart.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Client sends skuId + quantity.
 * Cart Service fetches product details from Product Catalog Service
 * to build the full CartItem snapshot.
 *
 * Why not let the client send the product name, price, image?
 * Because clients can be tampered with. Never trust price data
 * from the client. Always fetch from a trusted internal service.
 */
public record AddToCartRequest(
    @NotBlank
    String skuId,

    @Min(1)
    int quantity
) {}

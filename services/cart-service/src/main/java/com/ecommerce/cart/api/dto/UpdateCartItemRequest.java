package com.ecommerce.cart.api.dto;

import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
    @Min(0) // 0 means remove the item
    int quantity
) {}

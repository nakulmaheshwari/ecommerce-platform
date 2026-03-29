package com.ecommerce.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CartItemDto(
    String skuId,
    UUID productId,
    String productName,
    String variantName,
    int quantity,
    long pricePaise,
    long mrpPaise,
    String imageUrl
) {}

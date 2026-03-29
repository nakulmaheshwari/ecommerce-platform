package com.ecommerce.catalog.api.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public record CategoryResponse(
    UUID id,
    String name,
    String slug,
    String description,
    List<CategoryResponse> children
) implements Serializable {}

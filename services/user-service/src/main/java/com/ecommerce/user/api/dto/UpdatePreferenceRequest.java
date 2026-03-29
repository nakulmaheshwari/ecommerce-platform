package com.ecommerce.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdatePreferenceRequest(
    @NotBlank String key,
    @NotNull Object value
) {}

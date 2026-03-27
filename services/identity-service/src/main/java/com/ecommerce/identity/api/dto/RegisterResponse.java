package com.ecommerce.identity.api.dto;

import java.util.UUID;

public record RegisterResponse(
    UUID userId,
    String email,
    String status,
    String message
) {}

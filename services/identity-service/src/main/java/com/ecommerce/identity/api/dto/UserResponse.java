package com.ecommerce.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String status,
    boolean emailVerified,
    Instant createdAt,
    Instant lastLoginAt
) {}

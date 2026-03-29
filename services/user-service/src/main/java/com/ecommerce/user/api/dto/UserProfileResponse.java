package com.ecommerce.user.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String fullName,
    String phoneNumber,
    LocalDate dateOfBirth,
    String gender,
    String avatarUrl,
    Map<String, Object> preferences,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {}

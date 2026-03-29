package com.ecommerce.user.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
    UUID id,
    String label,
    String fullName,
    String phoneNumber,
    String line1,
    String line2,
    String city,
    String state,
    String pincode,
    String country,
    boolean isDefault,
    Instant createdAt
) {}

package com.ecommerce.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    String tokenType,
    UUID userId,
    String email,
    String fullName
) {
    public static AuthResponse of(String accessToken, String refreshToken,
                                  long expiresIn, UUID userId,
                                  String email, String fullName) {
        return new AuthResponse(accessToken, refreshToken,
            expiresIn, "Bearer", userId, email, fullName);
    }
}

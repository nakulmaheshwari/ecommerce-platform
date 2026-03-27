package com.ecommerce.identity.api.dto;

import com.ecommerce.identity.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
    @NotNull(message = "Status cannot be null")
    UserStatus status
) {}

package com.ecommerce.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignRoleRequest(
    @NotBlank(message = "Role name cannot be empty")
    String roleName
) {}

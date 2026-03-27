package com.ecommerce.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email
    String email,

    @NotBlank
    @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).+$",
        message = "Password must contain uppercase, lowercase, digit and special character"
    )
    String password,

    @NotBlank @Size(min = 1, max = 100)
    String firstName,

    @NotBlank @Size(min = 1, max = 100)
    String lastName,

    @Pattern(regexp = "^\\+?[1-9]\\d{9,14}$", message = "Invalid phone number")
    String phoneNumber
) {}

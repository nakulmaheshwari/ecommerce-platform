package com.ecommerce.user.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(

    @Size(min = 1, max = 100, message = "First name must be 1-100 characters")
    String firstName,

    @Size(min = 1, max = 100, message = "Last name must be 1-100 characters")
    String lastName,

    @Pattern(
        regexp = "^\\+?[1-9]\\d{9,14}$",
        message = "Invalid phone number format"
    )
    String phoneNumber,

    LocalDate dateOfBirth,

    String gender,

    @Size(max = 1000)
    String avatarUrl
) {}

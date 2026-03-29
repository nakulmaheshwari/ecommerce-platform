package com.ecommerce.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(

    @Size(min = 1, max = 50)
    String label,

    @NotBlank @Size(max = 200)
    String fullName,

    @NotBlank
    @Pattern(regexp = "^\\+?[1-9]\\d{9,14}$")
    String phoneNumber,

    @NotBlank @Size(max = 255)
    String line1,

    @Size(max = 255)
    String line2,

    @NotBlank @Size(max = 100)
    String city,

    @NotBlank @Size(max = 100)
    String state,

    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Invalid PIN code")
    String pincode,

    @Size(max = 100)
    String country,

    boolean makeDefault
) {}

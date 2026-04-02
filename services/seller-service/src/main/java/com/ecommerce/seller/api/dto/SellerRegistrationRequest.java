package com.ecommerce.seller.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerRegistrationRequest {
    @NotBlank
    private String businessName;

    @NotBlank
    private String displayName;

    @NotBlank
    @Email
    private String email;

    private String phone;

    private String description;

    @NotBlank
    private String entityType; // INDIVIDUAL, SOLE_PROPRIETORSHIP, etc.

    private String panNumber;
    private String gstin;

    private AddressDto address;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressDto {
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String pincode;
        private String country;
    }
}

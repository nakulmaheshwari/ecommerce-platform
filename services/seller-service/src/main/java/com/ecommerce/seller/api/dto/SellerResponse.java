package com.ecommerce.seller.api.dto;

import com.ecommerce.seller.domain.enums.SellerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerResponse {
    private UUID id;
    private String keycloakId;
    private String email;
    private String phone;
    private String businessName;
    private String displayName;
    private String status;
    private BigDecimal averageRating;
    private Long pendingSettlementPaise;
    private OffsetDateTime createdAt;
}

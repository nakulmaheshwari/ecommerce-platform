package com.ecommerce.seller.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementResponse {
    private UUID id;
    private Long grossSalesPaise;
    private Long netPayoutPaise;
    private String status;
    private OffsetDateTime periodStart;
    private OffsetDateTime periodEnd;
    private String payoutReference;
    private OffsetDateTime payoutAt;
}

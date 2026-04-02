package com.ecommerce.common.event;

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
public class SellerRegisteredEvent {
    private UUID sellerId;
    private String email;
    private String businessName;
    private String status;
    private OffsetDateTime timestamp;
}

package com.ecommerce.inventory.api.dto;

import java.util.List;
import java.util.UUID;

public record ReservationResult(
    boolean success,
    UUID orderId,
    List<String> reservedSkus,
    List<String> failedSkus
) {
    public static ReservationResult success(UUID orderId, List<String> reserved) {
        return new ReservationResult(true, orderId, reserved, List.of());
    }

    public static ReservationResult failed(UUID orderId, List<String> failed) {
        return new ReservationResult(false, orderId, List.of(), failed);
    }
}

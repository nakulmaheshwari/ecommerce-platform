package com.ecommerce.inventory.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of a stock reservation attempt.
 * Used internally and as a response to asynchronous order-placement events.
 * 
 * @param success True if the entire reservation request was fulfilled.
 * @param orderId The target order identifier.
 * @param reservedSkus List of SKUs successfully locked.
 * @param failedSkus List of SKUs that could not be locked (insufficient stock).
 */
public record ReservationResult(
    boolean success,
    UUID orderId,
    List<String> reservedSkus,
    List<String> failedSkus
) {
    /**
     * Factory method for successful multi-SKU reservations.
     */
    public static ReservationResult success(UUID orderId, List<String> reserved) {
        return new ReservationResult(true, orderId, reserved, List.of());
    }

    /**
     * Factory method for failed reservation attempts.
     */
    public static ReservationResult failed(UUID orderId, List<String> failed) {
        return new ReservationResult(false, orderId, List.of(), failed);
    }
}

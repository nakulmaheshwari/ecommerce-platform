package com.ecommerce.inventory.domain;

/**
 * Defines the lifecycle states of a stock reservation.
 */
public enum ReservationStatus {
    /**
     * Stock is reserved ('locked') and moved from available to reserved.
     * The customer is usually at the checkout page, awaiting payment confirmation.
     */
    HELD,

    /**
     * Payment was successful. Stock is now committed.
     * Decreases the 'reservedQty' and 'totalQty' permanently.
     */
    CONFIRMED,

    /**
     * Customer cancelled the order or payment was declined.
     * Stock is returned from 'reserved' back to 'available'.
     */
    RELEASED,

    /**
     * The 15-minute hold window passed without action.
     * System processes this exactly like a RELEASE.
     */
    EXPIRED
}

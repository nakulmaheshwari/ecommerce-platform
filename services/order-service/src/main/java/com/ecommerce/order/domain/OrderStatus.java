package com.ecommerce.order.domain;

import java.util.Map;
import java.util.Set;

/**
 * OrderStatus is a state machine encoded as an enum.
 *
 * VALID_TRANSITIONS defines exactly which state changes are legal.
 * Any attempt to transition outside these rules throws
 * InvalidOrderTransitionException before touching the database.
 *
 * Why encode transitions in the enum rather than in service logic?
 * Because the rules are intrinsic to the ORDER CONCEPT, not to any
 * particular service method. Any code anywhere that tries to update
 * an order status must go through this enum, and it will always
 * enforce the rules.
 *
 * Example of what this prevents:
 * A bug in the payment webhook handler tries to set status = PENDING
 * on a DELIVERED order. Without this guard, it would succeed silently.
 * With this guard, it throws immediately with a clear error message.
 */
public enum OrderStatus {
    PENDING,            // Created, not yet sent to payment
    AWAITING_PAYMENT,   // Payment initiated, waiting for confirmation
    CONFIRMED,          // Payment succeeded, inventory reserved
    PROCESSING,         // Being packed / prepared
    SHIPPED,            // Handed to courier
    DELIVERED,          // Customer received it
    CANCELLED,          // Cancelled (before shipping)
    REFUNDED;           // Money returned

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
        PENDING,          Set.of(AWAITING_PAYMENT, CANCELLED),
        AWAITING_PAYMENT, Set.of(CONFIRMED, CANCELLED),
        CONFIRMED,        Set.of(PROCESSING, CANCELLED),
        PROCESSING,       Set.of(SHIPPED, CANCELLED),
        SHIPPED,          Set.of(DELIVERED),
        DELIVERED,        Set.of(REFUNDED),
        CANCELLED,        Set.of(),   // Terminal state
        REFUNDED,         Set.of()    // Terminal state
    );

    public boolean canTransitionTo(OrderStatus target) {
        return VALID_TRANSITIONS
            .getOrDefault(this, Set.of())
            .contains(target);
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == REFUNDED;
    }
}

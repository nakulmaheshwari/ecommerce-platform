package com.ecommerce.payment.domain;

public enum PaymentStatus {
    INITIATED,
    PENDING,
    CAPTURED,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED;

    public boolean isTerminal() {
        return this == CAPTURED || this == FAILED ||
               this == REFUNDED || this == PARTIALLY_REFUNDED;
    }

    public boolean isSuccessful() {
        return this == CAPTURED;
    }
}

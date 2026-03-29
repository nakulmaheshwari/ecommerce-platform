package com.ecommerce.inventory.domain;

public enum ReservationStatus {
    HELD,       // Stock reserved, awaiting payment
    CONFIRMED,  // Payment succeeded — stock committed
    RELEASED,   // Payment failed or order cancelled — stock freed
    EXPIRED     // Hold window passed without payment
}

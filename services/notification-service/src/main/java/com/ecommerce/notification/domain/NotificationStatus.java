package com.ecommerce.notification.domain;

public enum NotificationStatus {
    PENDING,   // Queued, not yet sent
    SENT,      // Successfully delivered to provider
    FAILED,    // Exhausted retries
    SKIPPED    // Intentionally not sent (user unsubscribed, etc.)
}

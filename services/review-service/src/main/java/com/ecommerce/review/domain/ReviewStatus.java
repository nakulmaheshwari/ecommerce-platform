package com.ecommerce.review.domain;

/*
 * ReviewStatus state machine:
 *
 * PENDING  → APPROVED  (moderation passes)
 * PENDING  → REJECTED  (moderation rejects: spam, fake, etc.)
 * APPROVED → FLAGGED   (users report it, hits report threshold)
 * FLAGGED  → APPROVED  (moderator reviews and clears it)
 * FLAGGED  → REJECTED  (moderator reviews and confirms removal)
 *
 * Terminal state: REJECTED (review is permanently hidden)
 */
public enum ReviewStatus {
    PENDING,
    APPROVED,
    REJECTED,
    FLAGGED;

    public boolean isVisible() {
        return this == APPROVED;
    }

    public boolean canBeModerated() {
        return this == PENDING || this == FLAGGED;
    }
}

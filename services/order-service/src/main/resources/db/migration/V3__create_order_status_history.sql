/**
 * order_status_history — immutable audit log of every state change.
 *
 * This is not optional. In production you need this because:
 * 1. Customer support: "Why was my order cancelled?" — read the history.
 * 2. Dispute resolution: proof of what happened and when.
 * 3. Debugging: trace exactly which event caused which transition.
 * 4. SLA monitoring: how long did orders spend in each state?
 *
 * The 'actor' field tells you WHO made the change:
 *   "system"           — automatic (e.g., payment webhook)
 *   "user:uuid"        — customer action (e.g., customer cancelled)
 *   "admin:uuid"       — admin action (e.g., admin override)
 *   "service:payment"  — another service via Kafka event
 */
CREATE TABLE order_status_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES orders(id),
    from_status VARCHAR(30),           -- NULL for initial creation
    to_status   VARCHAR(30) NOT NULL,
    reason      TEXT,
    actor       VARCHAR(100) NOT NULL DEFAULT 'system',
    metadata    JSONB,                 -- e.g., {"paymentId": "...", "failureCode": "..."}
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_history_order ON order_status_history(order_id, created_at ASC);

/**
 * outbox_events — the Transactional Outbox pattern.
 *
 * THE PROBLEM IT SOLVES (Dual-Write problem):
 *
 * Naive approach (WRONG):
 *   1. Save order to PostgreSQL  ← transaction commits
 *   2. Publish to Kafka           ← might fail!
 *
 * If step 2 fails (Kafka down, network blip, pod crash between 1 and 2),
 * the order exists in the DB but NO downstream services know about it.
 * Inventory never reserves stock. Payment never charges. Order is stuck forever.
 *
 * Outbox approach (CORRECT):
 *   In ONE database transaction:
 *     1a. Save order to orders table
 *     1b. Save event to outbox_events table
 *   Transaction commits atomically — both records exist or neither.
 *
 *   Separately (the outbox poller):
 *     2. Poll outbox_events WHERE published = FALSE
 *     3. Publish to Kafka
 *     4. Mark published = TRUE
 *
 * If Kafka is down, the order still exists and the event is still
 * in the outbox. The poller retries until Kafka is back.
 * The system is eventually consistent — never loses events.
 */
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50)  NOT NULL,   -- 'Order'
    aggregate_id    UUID         NOT NULL,   -- orderId
    event_type      VARCHAR(100) NOT NULL,   -- 'order.placed'
    payload         JSONB        NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Poller queries this index constantly — must be fast
-- Partial index: only unpublished rows (published=FALSE rows are a tiny fraction)
CREATE INDEX idx_order_outbox_unpublished
    ON outbox_events(created_at ASC)
    WHERE published = FALSE;

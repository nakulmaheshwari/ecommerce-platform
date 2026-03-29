CREATE TABLE webhook_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    razorpay_event_id   VARCHAR(100) NOT NULL UNIQUE, -- deduplication key
    event_type          VARCHAR(100) NOT NULL,         -- "payment.captured"
    payload             JSONB NOT NULL,                -- full raw body
    signature_valid     BOOLEAN NOT NULL,
    processed           BOOLEAN NOT NULL DEFAULT FALSE,
    processing_error    TEXT,                          -- what went wrong if failed
    source_ip           VARCHAR(45),
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMPTZ
);

CREATE INDEX idx_webhook_event_id   ON webhook_events(razorpay_event_id);
CREATE INDEX idx_webhook_unprocessed ON webhook_events(received_at)
    WHERE processed = FALSE;

-- Outbox for payment events to Kafka
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_outbox_unpublished ON outbox_events(created_at ASC)
    WHERE published = FALSE;

/*
 * notifications — record of every send attempt.
 *
 * WHY PERSIST NOTIFICATIONS?
 *
 * 1. Deduplication: before sending, check if we already sent
 *    a notification for this idempotency_key. Prevents duplicate
 *    emails when Kafka delivers the same event twice.
 *
 * 2. Retry tracking: if sending fails (SMTP down), the record
 *    stays in PENDING. A scheduler retries PENDING records.
 *    Without this, failed notifications are lost forever.
 *
 * 3. Audit: customer says "I never got my order confirmation".
 *    Check this table: was it sent? What was the status?
 *    When was it sent? What address?
 *
 * 4. Debugging: the actual content sent is stored in payload_snapshot.
 *    When a customer reports the email had wrong info, you can see
 *    exactly what was sent without recreating it.
 */
CREATE TABLE notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key     VARCHAR(200) NOT NULL UNIQUE,
    user_id             UUID NOT NULL,
    channel             VARCHAR(20) NOT NULL,
    template_id         VARCHAR(100) NOT NULL,
    recipient           VARCHAR(255) NOT NULL,
    subject             VARCHAR(500),
    payload_snapshot    JSONB,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_reason      TEXT,
    retry_count         INTEGER NOT NULL DEFAULT 0,
    max_retries         INTEGER NOT NULL DEFAULT 3,
    next_retry_at       TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_notification_channel CHECK (
        channel IN ('EMAIL', 'SMS', 'PUSH')
    ),
    CONSTRAINT chk_notification_status CHECK (
        status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')
    )
);

CREATE INDEX idx_notifications_user    ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_status  ON notifications(status, next_retry_at)
    WHERE status = 'PENDING';
CREATE UNIQUE INDEX idx_notifications_idempotency ON notifications(idempotency_key);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notifications_updated_at
    BEFORE UPDATE ON notifications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE notification_templates (
    id              VARCHAR(100) PRIMARY KEY,
    channel         VARCHAR(20) NOT NULL,
    subject_template VARCHAR(500),
    description     VARCHAR(255),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO notification_templates (id, channel, subject_template, description) VALUES
    ('order-confirmed-v1',  'EMAIL', 'Your order #{{orderId}} is confirmed!', 'Sent when payment succeeds'),
    ('payment-confirmed-v1','EMAIL', 'Payment received for order #{{orderId}}', 'Sent when payment captured'),
    ('order-shipped-v1',    'EMAIL', 'Your order #{{orderId}} has been shipped!', 'Sent when order shipped'),
    ('order-delivered-v1',  'EMAIL', 'Your order #{{orderId}} has been delivered', 'Sent on delivery'),
    ('order-cancelled-v1',  'EMAIL', 'Your order #{{orderId}} has been cancelled', 'Sent on cancellation'),
    ('user-registered-v1',  'EMAIL', 'Welcome to EcomPlatform!', 'Sent on registration');

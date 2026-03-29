CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE payment_transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                UUID NOT NULL,
    user_id                 UUID NOT NULL,
    idempotency_key         UUID NOT NULL,

    -- Razorpay identifiers
    razorpay_order_id       VARCHAR(100),    -- rzp_order_xxx — created by us
    razorpay_payment_id     VARCHAR(100),    -- pay_xxx — created when user pays
    razorpay_signature      VARCHAR(256),    -- HMAC-SHA256 for verification

    -- Amount
    amount_paise            BIGINT NOT NULL,
    currency                CHAR(3) NOT NULL DEFAULT 'INR',

    -- State
    status                  VARCHAR(30) NOT NULL DEFAULT 'INITIATED',

    -- Error info (populated on failure)
    failure_code            VARCHAR(100),
    failure_reason          TEXT,

    -- Raw PSP response for debugging and reconciliation
    gateway_response        JSONB,

    -- Timestamps
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    webhook_received_at     TIMESTAMPTZ,     -- when Razorpay confirmed it
    captured_at             TIMESTAMPTZ,     -- when money was actually captured

    CONSTRAINT uq_payment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_razorpay_payment    UNIQUE (razorpay_payment_id),
    CONSTRAINT chk_payment_status CHECK (status IN (
        'INITIATED',      -- We created the Razorpay order
        'PENDING',        -- User is on payment screen
        'CAPTURED',       -- Payment succeeded, money captured
        'FAILED',         -- Payment failed
        'REFUNDED',       -- Money returned to customer
        'PARTIALLY_REFUNDED'
    )),
    CONSTRAINT chk_amount_positive CHECK (amount_paise > 0)
);

CREATE INDEX idx_payment_order_id    ON payment_transactions(order_id);
CREATE INDEX idx_payment_user_id     ON payment_transactions(user_id);
CREATE INDEX idx_payment_razorpay_order ON payment_transactions(razorpay_order_id);
CREATE INDEX idx_payment_status_date ON payment_transactions(status, created_at DESC);

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payment_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

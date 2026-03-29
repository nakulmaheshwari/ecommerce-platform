CREATE TABLE refunds (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_transaction_id  UUID NOT NULL REFERENCES payment_transactions(id),
    order_id                UUID NOT NULL,
    razorpay_refund_id      VARCHAR(100) UNIQUE,
    amount_paise            BIGINT NOT NULL,
    reason                  VARCHAR(255) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    initiated_by            VARCHAR(100) NOT NULL, -- 'user:uuid' or 'admin:uuid'
    notes                   TEXT,
    gateway_response        JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at            TIMESTAMPTZ,

    CONSTRAINT chk_refund_status CHECK (
        status IN ('PENDING', 'PROCESSED', 'FAILED')
    ),
    CONSTRAINT chk_refund_amount CHECK (amount_paise > 0)
);

CREATE INDEX idx_refunds_payment ON refunds(payment_transaction_id);
CREATE INDEX idx_refunds_order   ON refunds(order_id);

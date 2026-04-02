-- Settlements and Commission layer.
-- 
-- Manages the periodic payout process to sellers after 
-- deduction of platform commissions and fees.
--
-- Logic:
--   Total Sales (from Order Service)
-- - Platform Commission (based on category or seller rate)
-- - Shipping Fees (if fulfilled by platform)
-- - Returns/Refunds
-- = Net Payout Amount

CREATE TABLE IF NOT EXISTS settlements (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id           UUID        NOT NULL REFERENCES sellers(id),
    bank_account_id     UUID        NOT NULL REFERENCES seller_bank_accounts(id),
    
    -- Financials in Paise (BigInt)
    gross_sales_paise   BIGINT      NOT NULL DEFAULT 0,
    commission_paise    BIGINT      NOT NULL DEFAULT 0,
    platform_fee_paise  BIGINT      NOT NULL DEFAULT 0,
    shipping_fee_paise  BIGINT      NOT NULL DEFAULT 0,
    refund_paise        BIGINT      NOT NULL DEFAULT 0,
    tax_paise           BIGINT      NOT NULL DEFAULT 0,  -- GST on commission etc.
    net_payout_paise    BIGINT      NOT NULL DEFAULT 0,  -- gross - fees - refunds
    
    -- Status
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    -- PENDING, PROCESSING, COMPLETED, FAILED, HELD
    
    -- Scheduling
    period_start        TIMESTAMPTZ NOT NULL,
    period_end          TIMESTAMPTZ NOT NULL,
    
    -- Payout metadata
    payout_reference    VARCHAR(100), -- Bank/Gateway transfer ID
    payout_at           TIMESTAMPTZ,
    notes               TEXT,
    
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version             BIGINT NOT NULL DEFAULT 0
);

-- Line items for a settlement (orders included in this payout)
CREATE TABLE IF NOT EXISTS settlement_items (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id       UUID        NOT NULL REFERENCES settlements(id) ON DELETE CASCADE,
    order_id            UUID        NOT NULL,
    order_item_id       UUID        NOT NULL,
    order_amount_paise  BIGINT      NOT NULL,
    commission_paise    BIGINT      NOT NULL,
    item_status         VARCHAR(30) NOT NULL, -- DELIVERED, RETURN_REQUESTED, RETURNED
    
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_settlement_seller     ON settlements (seller_id, status);
CREATE INDEX idx_settlement_period     ON settlements (period_start, period_end);
CREATE INDEX idx_settlement_items_main ON settlement_items (settlement_id);
CREATE INDEX idx_settlement_items_order ON settlement_items (order_id);

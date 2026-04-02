-- Commissions and category configuration layer.
-- 
-- Defines how much platform takes for each sale.
-- Rates are usually per-category.
--
-- Logic:
--   - FIXED_AMOUNT → platform takes flat fee (e.g. ₹10)
--   - PERCENTAGE   → platform takes % of selling price (e.g. 10%)
--   - PROGRESSIVE  → higher price, different %

CREATE TABLE IF NOT EXISTS platform_commissions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id         UUID        NOT NULL,  -- FK to Catalog category
    category_name       VARCHAR(100) NOT NULL,
    commission_type     VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE',
    
    -- Rate configuration
    rate_percent        NUMERIC(5,2) DEFAULT 0.00,
    fixed_fee_paise     BIGINT      DEFAULT 0,
    
    -- Minimum/Maximum commission caps
    min_commission_paise BIGINT,
    max_commission_paise BIGINT,
    
    -- Availability
    is_active          BOOLEAN      NOT NULL DEFAULT true,
    effective_from     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version            BIGINT NOT NULL DEFAULT 0,
    
    UNIQUE (category_id)
);

CREATE INDEX idx_comm_active ON platform_commissions (category_id, is_active);

CREATE TABLE products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku             VARCHAR(100) NOT NULL UNIQUE,   -- Stock Keeping Unit: "APPLE-IPH15-128-BLK"
    name            VARCHAR(500) NOT NULL,
    slug            VARCHAR(500) NOT NULL UNIQUE,   -- URL key: "apple-iphone-15-128gb-black"
    description     TEXT,
    category_id     UUID NOT NULL REFERENCES categories(id),
    brand           VARCHAR(150),
    -- Money stored in smallest unit (paise). NEVER float.
    price_paise     BIGINT NOT NULL,
    mrp_paise       BIGINT NOT NULL,                -- Maximum retail price
    cost_paise      BIGINT,                         -- Cost price (internal use only)
    tax_percent     NUMERIC(5,2) NOT NULL DEFAULT 18.00,
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    is_digital      BOOLEAN NOT NULL DEFAULT FALSE,
    weight_grams    INTEGER,
    created_by      UUID NOT NULL,                  -- admin user ID
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,

    CONSTRAINT chk_product_status CHECK (status IN (
        'DRAFT', 'ACTIVE', 'INACTIVE', 'DISCONTINUED'
    )),
    CONSTRAINT chk_price_positive   CHECK (price_paise > 0),
    CONSTRAINT chk_mrp_gte_price    CHECK (mrp_paise >= price_paise)
);

CREATE INDEX idx_products_sku        ON products(sku);
CREATE INDEX idx_products_slug       ON products(slug);
CREATE INDEX idx_products_category   ON products(category_id);
CREATE INDEX idx_products_status     ON products(status);
CREATE INDEX idx_products_brand      ON products(brand);
-- Partial index: only active products are queried by customers
CREATE INDEX idx_products_active     ON products(category_id, price_paise)
    WHERE status = 'ACTIVE';

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

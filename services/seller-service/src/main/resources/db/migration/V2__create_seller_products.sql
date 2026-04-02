-- Seller product listings.
-- 
-- In a marketplace, a single "product" (e.g., iPhone 15 128GB Black)
-- can be listed by MULTIPLE sellers at different prices.
-- This table is the seller's specific listing of a product from the catalog.
--
-- product_id references the canonical product in Product Catalog Service.
-- The Seller Service does NOT own product master data — it owns the listing.
--
-- Listing lifecycle:
--   DRAFT       → seller is preparing the listing
--   PENDING_APPROVAL → seller submitted, admin must approve
--   ACTIVE      → visible to buyers, can be added to cart
--   PAUSED      → seller temporarily paused (vacation mode etc.)
--   REJECTED    → admin rejected this listing
--   DELISTED    → permanently removed

CREATE TABLE IF NOT EXISTS seller_products (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id           UUID        NOT NULL REFERENCES sellers(id),
    product_id          UUID        NOT NULL,  -- FK to Product Catalog (different service)
    sku                 VARCHAR(100) NOT NULL,
    
    -- Seller's own price (may differ from catalog MRP)
    selling_price_paise BIGINT      NOT NULL CHECK (selling_price_paise > 0),
    mrp_paise           BIGINT      NOT NULL CHECK (mrp_paise >= selling_price_paise),
    
    -- Shipping
    dispatch_days       SMALLINT    NOT NULL DEFAULT 2,  -- days to dispatch after order
    ships_from_city     VARCHAR(100),
    ships_from_state    VARCHAR(100),
    
    -- Listing status
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    rejection_reason    TEXT,
    approved_by         VARCHAR(255),
    approved_at         TIMESTAMPTZ,
    
    -- Listing metadata
    custom_title        VARCHAR(500),  -- seller can override the catalog title
    custom_description  TEXT,          -- seller can add their own description
    
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version             BIGINT NOT NULL DEFAULT 0,
    
    -- A seller cannot list the same product twice with the same SKU
    UNIQUE (seller_id, product_id, sku)
);

CREATE INDEX idx_seller_products_seller   ON seller_products (seller_id, status);
CREATE INDEX idx_seller_products_product  ON seller_products (product_id, status);
CREATE INDEX idx_seller_products_sku      ON seller_products (sku);

-- Seller Inventory layer.
-- 
-- Manages stock levels for seller products across locations (usually 1 default).
-- Used for real-time inventory checks during checkout.
--
-- Note: This is separate from the physical inventory in the fulfillment center (if any).
-- This represents what the seller is CLAIMS to have in stock.
--
-- Reservation system:
--   - quantity_available: total units the seller is currently holding
--   - quantity_reserved: units committed to pending orders (not yet shipped)
--   - quantity_sold: units shipped and confirmed sold

CREATE TABLE IF NOT EXISTS seller_inventories (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_product_id   UUID        NOT NULL REFERENCES seller_products(id) ON DELETE CASCADE,
    quantity_available  INTEGER     NOT NULL DEFAULT 0 CHECK (quantity_available >= 0),
    quantity_reserved   INTEGER     NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0),
    quantity_sold       BIGINT      NOT NULL DEFAULT 0,
    
    -- Low stock alerts
    low_stock_threshold INTEGER     DEFAULT 5,
    is_out_of_stock     BOOLEAN     GENERATED ALWAYS AS (quantity_available <= 0) STORED,
    
    last_updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_updated_by    VARCHAR(255),
    version            BIGINT NOT NULL DEFAULT 0,
    
    UNIQUE (seller_product_id)
);

-- Inventory Transaction Log (Audit trail)
--   REPLENISHMENT  → seller added stock
--   ORDER_RESERVED → decrement available, increment reserved
--   ORDER_CANCELLED→ decrement reserved, increment available
--   ORDER_SHIPPED  → decrement reserved, increment sold
--   RETURN_STOCK   → increment available (if restockable)

CREATE TABLE IF NOT EXISTS inventory_transactions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id        UUID        NOT NULL REFERENCES seller_inventories(id),
    transaction_type    VARCHAR(30) NOT NULL,
    quantity_change     INTEGER     NOT NULL,
    reference_id        VARCHAR(100),  -- order_id, shipment_id, etc.
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_seller_product ON seller_inventories (seller_product_id);
CREATE INDEX idx_inventory_tx_inventory  ON inventory_transactions (inventory_id, created_at DESC);

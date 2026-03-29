CREATE TABLE inventory (
    sku_id          VARCHAR(100) PRIMARY KEY,
    product_id      UUID NOT NULL,
    available_qty   INTEGER NOT NULL DEFAULT 0,
    reserved_qty    INTEGER NOT NULL DEFAULT 0,
    reorder_point   INTEGER NOT NULL DEFAULT 10,
    reorder_qty     INTEGER NOT NULL DEFAULT 100,
    warehouse_id    VARCHAR(50) NOT NULL DEFAULT 'WH-MAIN',
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Database-level guard against negative stock
    -- Even if application logic fails, DB rejects it
    CONSTRAINT chk_available_non_negative CHECK (available_qty >= 0),
    CONSTRAINT chk_reserved_non_negative  CHECK (reserved_qty >= 0)
);

CREATE INDEX idx_inventory_product   ON inventory(product_id);
CREATE INDEX idx_inventory_low_stock ON inventory(sku_id)
    WHERE available_qty <= reorder_point;

-- Seed test data
INSERT INTO inventory (sku_id, product_id, available_qty, reorder_point) VALUES
    ('TEST-SKU-001', gen_random_uuid(), 100, 10),
    ('TEST-SKU-002', gen_random_uuid(), 50,  5),
    ('TEST-SKU-003', gen_random_uuid(), 0,   10);

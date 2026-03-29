/**
 * order_items — product snapshot at order time.
 *
 * WHY SNAPSHOT product_name, unit_price_paise here?
 *
 * Products change. An iPhone listed at ₹79,999 today might be
 * ₹69,999 tomorrow (price drop) or discontinued next year.
 * If we only store product_id and join to the product table,
 * every historical order shows the CURRENT price, not the price
 * paid. This breaks:
 *   - Invoices (legal document must show price at time of sale)
 *   - Finance reconciliation
 *   - Customer service ("I paid X, why does it show Y?")
 *
 * Snapshot everything you need for the invoice at order time.
 * The product_id FK is kept for reference only.
 */
CREATE TABLE order_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sku_id              VARCHAR(100) NOT NULL,
    product_id          UUID NOT NULL,
    product_name        VARCHAR(500) NOT NULL,   -- snapshot
    variant_name        VARCHAR(200),            -- snapshot: "128GB - Black"
    quantity            INTEGER NOT NULL,
    unit_price_paise    BIGINT NOT NULL,         -- snapshot: price at order time
    mrp_paise           BIGINT NOT NULL,         -- snapshot: for invoice
    image_url           VARCHAR(1000),           -- snapshot: for order emails
    CONSTRAINT chk_item_qty CHECK (quantity > 0),
    CONSTRAINT chk_item_price CHECK (unit_price_paise > 0)
);

CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_sku   ON order_items(sku_id);

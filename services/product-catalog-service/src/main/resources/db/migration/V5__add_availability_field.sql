-- Add availability field to products for high-performance filtering.
-- Initial state: all existing products are assumed Available until Inventory Service syncs.
ALTER TABLE products ADD COLUMN available BOOLEAN NOT NULL DEFAULT TRUE;

-- Partial index to optimize recommendation queries which always filter available = true.
-- This index will be smaller and faster than a full index on the boolean column.
CREATE INDEX idx_products_available ON products(category_id) WHERE available = true;

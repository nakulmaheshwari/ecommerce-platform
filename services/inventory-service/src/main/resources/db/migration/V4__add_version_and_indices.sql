-- [V4] Architectural Hardening: Versioning, Indices, and Constraints

-- 1. Add versioning for Optimistic Concurrency Control
ALTER TABLE inventory ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;

-- 2. Add Unique Constraint for strict idempotency
-- Ensures an order cannot reserve the same SKU twice across multiple concurrent attempts
ALTER TABLE reservations ADD CONSTRAINT unique_order_sku UNIQUE (order_id, sku_id);

-- 3. Add Performance Indices
-- Accelerate order-level lookups during confirm/release
CREATE INDEX IF NOT EXISTS idx_reservation_order_id ON reservations (order_id);

-- Accelerate background expiry job scans
CREATE INDEX IF NOT EXISTS idx_reservation_expires_at ON reservations (expires_at);

-- Performance index for SKU lookups (good practice, even if PK)
CREATE INDEX IF NOT EXISTS idx_inventory_sku_id ON inventory (sku_id);

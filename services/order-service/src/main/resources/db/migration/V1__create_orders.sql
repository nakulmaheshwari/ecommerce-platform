CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';
/**
 * orders table — the core of the entire platform.
 *
 * Key design decisions:
 *
 * 1. idempotency_key (UUID, UNIQUE index)
 *    The client generates this before submitting the order.
 *    If the network times out and the client retries, the second
 *    request finds the existing order and returns it instead of
 *    creating a duplicate. This prevents double-orders.
 *
 * 2. version (INTEGER) — optimistic locking
 *    Hibernate adds "WHERE version = ?" to every UPDATE.
 *    If two threads try to update the same order simultaneously
 *    (e.g., payment callback + admin cancel), one gets
 *    OptimisticLockException and must retry.
 *    This is safer than pessimistic locks (SELECT FOR UPDATE)
 *    which hold locks and can cause deadlocks.
 *
 * 3. Money in paise (BIGINT, NOT FLOAT/DECIMAL)
 *    ₹79,999 = 7,999,900 paise.
 *    Floating point arithmetic is imprecise. 0.1 + 0.2 ≠ 0.3 in float.
 *    Using integers makes arithmetic exact and avoids rounding bugs
 *    that create financial discrepancies.
 *
 * 4. CHECK constraint on total
 *    total_paise = subtotal - discount + tax
 *    Database enforces this invariant. Even if application code has
 *    a bug, the DB rejects inconsistent totals.
 *
 * 5. shipping_address stored as JSONB snapshot
 *    The user's address might change after placing the order.
 *    We snapshot the address at order time so the delivery address
 *    is immutable and always reflects what was ordered.
 */
CREATE TABLE orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    idempotency_key     UUID NOT NULL,
    currency            CHAR(3) NOT NULL DEFAULT 'INR',
    subtotal_paise      BIGINT NOT NULL,
    discount_paise      BIGINT NOT NULL DEFAULT 0,
    tax_paise           BIGINT NOT NULL DEFAULT 0,
    delivery_paise      BIGINT NOT NULL DEFAULT 0,
    total_paise         BIGINT NOT NULL,
    shipping_address    JSONB NOT NULL,       -- snapshot at order time
    notes               TEXT,
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_order_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_order_status CHECK (status IN (
        'PENDING', 'AWAITING_PAYMENT', 'CONFIRMED',
        'PROCESSING', 'SHIPPED', 'DELIVERED',
        'CANCELLED', 'REFUNDED'
    )),
    CONSTRAINT chk_total_positive CHECK (total_paise > 0),
    CONSTRAINT chk_total_formula  CHECK (
        total_paise = subtotal_paise - discount_paise + tax_paise + delivery_paise
    )
);

-- Most frequent query: "show my orders" — needs user + status + date
CREATE INDEX idx_orders_user_status ON orders(user_id, status, created_at DESC);
-- Idempotency check on every order placement
CREATE UNIQUE INDEX idx_orders_idempotency ON orders(idempotency_key);
-- Admin queries by status
CREATE INDEX idx_orders_status_date ON orders(status, created_at DESC);

CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

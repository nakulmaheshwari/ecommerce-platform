CREATE TABLE reservations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku_id      VARCHAR(100) NOT NULL REFERENCES inventory(sku_id),
    order_id    UUID NOT NULL,
    quantity    INTEGER NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'HELD',
    expires_at  TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '15 minutes',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- One reservation per SKU per order
    CONSTRAINT uq_reservation UNIQUE (sku_id, order_id),
    CONSTRAINT chk_reservation_status CHECK (
        status IN ('HELD', 'CONFIRMED', 'RELEASED', 'EXPIRED')
    ),
    CONSTRAINT chk_reservation_qty CHECK (quantity > 0)
);

CREATE INDEX idx_reservations_order  ON reservations(order_id);
CREATE INDEX idx_reservations_sku    ON reservations(sku_id, status);
-- For the expiry job
CREATE INDEX idx_reservations_expiry ON reservations(expires_at)
    WHERE status = 'HELD';

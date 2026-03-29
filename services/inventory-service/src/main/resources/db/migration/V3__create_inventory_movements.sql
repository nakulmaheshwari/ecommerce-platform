-- Audit log of every stock change — required for finance reconciliation
CREATE TABLE inventory_movements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku_id          VARCHAR(100) NOT NULL,
    movement_type   VARCHAR(30) NOT NULL,  -- INBOUND, RESERVATION, RELEASE, CONFIRMATION, ADJUSTMENT
    quantity_delta  INTEGER NOT NULL,       -- positive = stock in, negative = stock out
    reference_id    UUID,                  -- orderId, purchaseOrderId, etc.
    reference_type  VARCHAR(50),           -- ORDER, PURCHASE_ORDER, MANUAL_ADJUSTMENT
    before_qty      INTEGER NOT NULL,
    after_qty       INTEGER NOT NULL,
    notes           TEXT,
    created_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_movements_sku       ON inventory_movements(sku_id, created_at DESC);
CREATE INDEX idx_movements_reference ON inventory_movements(reference_id);

-- Outbox for async event publishing
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_outbox_unpublished ON outbox_events(created_at ASC)
    WHERE published = FALSE;

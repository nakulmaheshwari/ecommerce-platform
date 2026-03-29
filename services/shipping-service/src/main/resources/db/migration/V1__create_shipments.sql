CREATE TABLE shipments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL UNIQUE,
    user_id         UUID NOT NULL,
    tracking_number VARCHAR(100) NOT NULL UNIQUE,
    carrier         VARCHAR(100) NOT NULL DEFAULT 'BlueDart',
    status          VARCHAR(30)  NOT NULL DEFAULT 'CREATED',
    shipping_address JSONB NOT NULL,
    estimated_delivery_date DATE,
    actual_delivery_date    DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    shipped_at      TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,

    CONSTRAINT chk_shipment_status CHECK (status IN (
        'CREATED', 'PICKED_UP', 'IN_TRANSIT',
        'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED_DELIVERY', 'RETURNED'
    ))
);

CREATE INDEX idx_shipments_order    ON shipments(order_id);
CREATE INDEX idx_shipments_tracking ON shipments(tracking_number);

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shipping_outbox_unpublished ON outbox_events(created_at ASC)
    WHERE published = FALSE;

-- Product variants: iPhone 15 in 128GB Black, 256GB Blue, etc.
CREATE TABLE product_variants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku             VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,       -- "128GB - Black"
    price_paise     BIGINT NOT NULL,
    attributes      JSONB NOT NULL DEFAULT '{}', -- {"color":"Black","storage":"128GB"}
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_variant_price CHECK (price_paise > 0)
);

CREATE INDEX idx_variants_product    ON product_variants(product_id);
CREATE INDEX idx_variants_sku        ON product_variants(sku);
CREATE INDEX idx_variants_attributes ON product_variants USING GIN(attributes);

-- Product images
CREATE TABLE product_images (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    variant_id  UUID REFERENCES product_variants(id) ON DELETE SET NULL,
    url         VARCHAR(1000) NOT NULL,
    alt_text    VARCHAR(300),
    is_primary  BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_images_product  ON product_images(product_id, sort_order);
CREATE INDEX idx_images_variant  ON product_images(variant_id);

-- Outbox for catalog events
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_catalog_outbox_unpublished ON outbox_events(created_at ASC)
    WHERE published = FALSE;

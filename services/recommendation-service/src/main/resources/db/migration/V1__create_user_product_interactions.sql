CREATE TABLE IF NOT EXISTS user_product_interactions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    product_id      UUID        NOT NULL,
    session_id      UUID,
    event_type      VARCHAR(50) NOT NULL,
    implicit_score  NUMERIC(4,2) NOT NULL,
    device_type     VARCHAR(20),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_interactions_user_time    ON user_product_interactions (user_id, occurred_at DESC);
CREATE INDEX idx_interactions_product_time ON user_product_interactions (product_id, occurred_at DESC);
CREATE INDEX idx_interactions_time         ON user_product_interactions (occurred_at DESC);

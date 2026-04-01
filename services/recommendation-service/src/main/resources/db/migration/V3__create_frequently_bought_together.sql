CREATE TABLE IF NOT EXISTS frequently_bought_together (
    product_a_id   UUID         NOT NULL,
    product_b_id   UUID         NOT NULL,
    co_occurrence  INTEGER      NOT NULL,
    confidence     NUMERIC(5,4) NOT NULL,
    lift           NUMERIC(6,4) NOT NULL,
    computed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (product_a_id, product_b_id)
);

CREATE INDEX idx_fbt_a ON frequently_bought_together (product_a_id, confidence DESC);

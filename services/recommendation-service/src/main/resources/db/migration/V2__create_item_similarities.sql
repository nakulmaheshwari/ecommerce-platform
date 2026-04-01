CREATE TABLE IF NOT EXISTS item_similarities (
    source_product_id  UUID         NOT NULL,
    target_product_id  UUID         NOT NULL,
    similarity_score   NUMERIC(6,4) NOT NULL,
    co_occurrence      INTEGER      NOT NULL,
    algorithm          VARCHAR(30)  NOT NULL,
    computed_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (source_product_id, target_product_id, algorithm)
);

CREATE INDEX idx_similarities_source ON item_similarities (source_product_id, similarity_score DESC);

CREATE TABLE IF NOT EXISTS user_recommendations (
    user_id     UUID         NOT NULL,
    product_id  UUID         NOT NULL,
    score       NUMERIC(8,4) NOT NULL,
    rank        SMALLINT     NOT NULL,
    algorithm   VARCHAR(30)  NOT NULL DEFAULT 'ALS',
    expires_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, rank)
);

CREATE INDEX idx_user_recs_user    ON user_recommendations (user_id, rank ASC);
CREATE INDEX idx_user_recs_expires ON user_recommendations (expires_at);

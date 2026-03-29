CREATE EXTENSION IF NOT EXISTS "pgcrypto";

/*
 * reviews — the core table.
 *
 * KEY DESIGN DECISIONS:
 *
 * 1. UNIQUE (product_id, user_id)
 *    One review per user per product. Period.
 *    Enforced at the DB level — application code cannot bypass this.
 *
 * 2. verified_purchase (BOOLEAN)
 *    Set by the system when the review is submitted — we check
 *    if the user actually ordered this product by calling Order Service.
 *    Users cannot self-certify. Only our system sets this field.
 *
 * 3. status with moderation states
 *    PENDING   → new review, awaiting auto/manual moderation
 *    APPROVED  → visible to all users
 *    REJECTED  → hidden (spam, abuse, fake)
 *    FLAGGED   → user-reported, in moderation queue
 *
 * 4. helpful_votes + total_votes
 *    "Was this review helpful?" voting system.
 *    Stores raw counts so Wilson score can be applied for ranking.
 *
 * 5. title + body separately
 *    Title is shown in the review summary card.
 *    Body is shown when user clicks "Read more".
 */
CREATE TABLE reviews (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id           UUID NOT NULL,
    user_id              UUID NOT NULL,

    -- The keycloak sub claim UUID — for direct JWT lookups
    reviewer_keycloak_id UUID NOT NULL,

    -- Snapshot of reviewer name at review time
    reviewer_name        VARCHAR(200) NOT NULL,

    rating               SMALLINT NOT NULL,
    title                VARCHAR(200),
    body                 TEXT,

    verified_purchase    BOOLEAN NOT NULL DEFAULT FALSE,

    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    helpful_votes        INTEGER NOT NULL DEFAULT 0,
    total_votes          INTEGER NOT NULL DEFAULT 0,

    rejection_reason     TEXT,
    moderated_by         UUID,
    moderated_at         TIMESTAMPTZ,

    report_count         INTEGER NOT NULL DEFAULT 0,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_one_review_per_user_product UNIQUE (product_id, user_id),
    CONSTRAINT chk_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_status CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'FLAGGED'
    )),
    CONSTRAINT chk_helpful_votes CHECK (helpful_votes <= total_votes),
    CONSTRAINT chk_votes_non_negative CHECK (
        helpful_votes >= 0 AND total_votes >= 0
    )
);

CREATE INDEX idx_reviews_product_status ON reviews(product_id, status, created_at DESC);
CREATE INDEX idx_reviews_user           ON reviews(user_id, created_at DESC);
CREATE INDEX idx_reviews_moderation     ON reviews(status, report_count DESC)
    WHERE status IN ('PENDING', 'FLAGGED');
CREATE INDEX idx_reviews_verified       ON reviews(product_id, verified_purchase)
    WHERE status = 'APPROVED';

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reviews_updated_at
    BEFORE UPDATE ON reviews
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

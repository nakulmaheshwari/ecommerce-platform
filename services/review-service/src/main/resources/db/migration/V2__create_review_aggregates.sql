/*
 * review_aggregates — pre-computed rating statistics per product.
 *
 * WHY THIS TABLE EXISTS:
 * Naive: SELECT AVG(rating), COUNT(*) FROM reviews WHERE product_id = ?
 * At scale with millions of reviews: full scan per request.
 *
 * This table maintains a running aggregate updated atomically with
 * each new review. Reading the average is O(1) — one row lookup.
 *
 * rating_N_count stores the distribution for rendering the histogram:
 *   ⭐⭐⭐⭐⭐  ████████████████████ 60%
 *   ⭐⭐⭐⭐    ████████████ 25%
 *   ...
 */
CREATE TABLE review_aggregates (
    product_id     UUID PRIMARY KEY,
    total_reviews  INTEGER NOT NULL DEFAULT 0,
    total_score    INTEGER NOT NULL DEFAULT 0,
    average_rating NUMERIC(3,2) NOT NULL DEFAULT 0.00,
    rating_1_count INTEGER NOT NULL DEFAULT 0,
    rating_2_count INTEGER NOT NULL DEFAULT 0,
    rating_3_count INTEGER NOT NULL DEFAULT 0,
    rating_4_count INTEGER NOT NULL DEFAULT 0,
    rating_5_count INTEGER NOT NULL DEFAULT 0,
    last_review_at TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_total_reviews_non_negative CHECK (total_reviews >= 0),
    CONSTRAINT chk_average_rating_range CHECK (
        average_rating BETWEEN 0.00 AND 5.00
    )
);

-- "Top rated products" queries (only products with enough reviews)
CREATE INDEX idx_aggregates_rating ON review_aggregates(average_rating DESC)
    WHERE total_reviews >= 5;

/*
 * review_votes — tracks who voted helpful on which review.
 *
 * UNIQUE (review_id, user_id) prevents double voting at DB level.
 * is_helpful = TRUE  → "Yes, this was helpful"
 * is_helpful = FALSE → "No, this was not helpful"
 */
CREATE TABLE review_votes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id   UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL,
    is_helpful  BOOLEAN NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_one_vote_per_user_review UNIQUE (review_id, user_id)
);

CREATE INDEX idx_votes_review ON review_votes(review_id);
CREATE INDEX idx_votes_user   ON review_votes(user_id);

/*
 * review_reports — abuse reports from users.
 *
 * When report_count reaches threshold (5), review is auto-flagged.
 * UNIQUE (review_id, reporter_id) prevents same user reporting twice.
 */
CREATE TABLE review_reports (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id   UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL,
    reason      VARCHAR(50) NOT NULL,
    details     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_one_report_per_user_review UNIQUE (review_id, reporter_id),
    CONSTRAINT chk_report_reason CHECK (reason IN (
        'SPAM', 'FAKE', 'OFFENSIVE', 'IRRELEVANT', 'OTHER'
    ))
);

CREATE INDEX idx_reports_review ON review_reports(review_id);

/*
 * outbox_events — for events Review Service publishes.
 * review.submitted → triggers notification
 * review.approved  → triggers product catalog rating update
 */
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    published      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_review_outbox_unpublished ON outbox_events(created_at ASC)
    WHERE published = FALSE;

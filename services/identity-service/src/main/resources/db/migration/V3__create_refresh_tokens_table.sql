-- Track issued refresh tokens for revocation support
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 hash of the actual token
    device_info     VARCHAR(255),
    ip_address      INET,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN     NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash    ON refresh_tokens(token_hash);
-- Clean up expired tokens via a scheduled job
CREATE INDEX idx_refresh_tokens_expiry  ON refresh_tokens(expires_at)
    WHERE revoked = FALSE;

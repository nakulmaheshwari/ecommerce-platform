CREATE TABLE addresses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    label           VARCHAR(50) NOT NULL DEFAULT 'Home',
    full_name       VARCHAR(200) NOT NULL,
    phone_number    VARCHAR(20) NOT NULL,
    line1           VARCHAR(255) NOT NULL,
    line2           VARCHAR(255),
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(100) NOT NULL,
    pincode         VARCHAR(20) NOT NULL,
    country         VARCHAR(100) NOT NULL DEFAULT 'India',
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_label_length CHECK (char_length(label) >= 1)
);

CREATE INDEX idx_addresses_user    ON addresses(user_id)
    WHERE is_deleted = FALSE;
CREATE INDEX idx_addresses_default ON addresses(user_id, is_default)
    WHERE is_default = TRUE AND is_deleted = FALSE;

CREATE TRIGGER trg_addresses_updated_at
    BEFORE UPDATE ON addresses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

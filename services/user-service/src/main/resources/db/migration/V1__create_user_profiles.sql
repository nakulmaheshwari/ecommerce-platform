CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE user_profiles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id         UUID NOT NULL UNIQUE,
    email               VARCHAR(255) NOT NULL UNIQUE,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    phone_number        VARCHAR(20),
    date_of_birth       DATE,
    gender              VARCHAR(20),
    avatar_url          VARCHAR(1000),
    preferences         JSONB NOT NULL DEFAULT '{}',
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_gender CHECK (
        gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY')
    )
);

CREATE INDEX idx_user_profiles_keycloak ON user_profiles(keycloak_id);
CREATE INDEX idx_user_profiles_email    ON user_profiles(email);
CREATE INDEX idx_user_profiles_phone    ON user_profiles(phone_number)
    WHERE phone_number IS NOT NULL;
CREATE INDEX idx_user_prefs_gin ON user_profiles USING GIN(preferences);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_profiles_updated_at
    BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

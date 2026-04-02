-- Seller profile + KYC status.
-- A seller can be an individual or a business entity.
-- KYC must be APPROVED before a seller can list products.
-- commission_rate_percent overrides the category-level default
-- when set (for negotiated rates with high-volume sellers).

CREATE TYPE seller_status AS ENUM (
    'PENDING_KYC',     -- registered but KYC not submitted
    'KYC_SUBMITTED',   -- documents uploaded, awaiting review
    'KYC_REJECTED',    -- documents rejected, seller must resubmit
    'ACTIVE',          -- KYC approved, can list products and sell
    'SUSPENDED',       -- suspended by admin (policy violation)
    'DEACTIVATED'      -- seller chose to stop selling
);

CREATE TABLE IF NOT EXISTS sellers (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id             VARCHAR(36) UNIQUE NOT NULL,  -- Keycloak user UUID
    email                   VARCHAR(255) UNIQUE NOT NULL,
    phone                   VARCHAR(20),
    business_name           VARCHAR(255) NOT NULL,
    display_name            VARCHAR(255) NOT NULL,        -- shown to buyers
    description             TEXT,
    logo_url                VARCHAR(500),
    
    -- Legal / tax identity
    pan_number              VARCHAR(10),                  -- PAN card
    gstin                   VARCHAR(15),                  -- GST registration
    entity_type             VARCHAR(30) NOT NULL DEFAULT 'INDIVIDUAL',
    -- INDIVIDUAL, SOLE_PROPRIETORSHIP, PARTNERSHIP, PRIVATE_LIMITED, LLP
    
    -- Address
    address_line1           VARCHAR(255),
    address_line2           VARCHAR(255),
    city                    VARCHAR(100),
    state                   VARCHAR(100),
    pincode                 VARCHAR(10),
    country                 VARCHAR(50) DEFAULT 'India',
    
    -- Commission
    -- NULL = use category default from config
    commission_rate_percent NUMERIC(5,2),
    
    -- KYC
    kyc_status              VARCHAR(30) NOT NULL DEFAULT 'PENDING_KYC',
    kyc_reviewed_at         TIMESTAMPTZ,
    kyc_reviewed_by         VARCHAR(255),  -- admin who reviewed
    kyc_rejection_reason    TEXT,
    
    status                  seller_status NOT NULL DEFAULT 'PENDING_KYC',
    
    -- Ratings (aggregated from buyer reviews of this seller)
    average_rating          NUMERIC(3,2) DEFAULT 0.00,
    total_ratings           INTEGER DEFAULT 0,
    
    -- Financial summary (denormalized for dashboard performance)
    total_sales_paise       BIGINT DEFAULT 0,
    total_settled_paise     BIGINT DEFAULT 0,
    pending_settlement_paise BIGINT DEFAULT 0,
    
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0  -- optimistic locking
);

CREATE INDEX idx_sellers_keycloak    ON sellers (keycloak_id);
CREATE INDEX idx_sellers_email       ON sellers (email);
CREATE INDEX idx_sellers_status      ON sellers (status);
CREATE INDEX idx_sellers_kyc_status  ON sellers (kyc_status);
CREATE INDEX idx_sellers_gstin       ON sellers (gstin) WHERE gstin IS NOT NULL;

-- Bank account for settlement payouts
CREATE TABLE IF NOT EXISTS seller_bank_accounts (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id       UUID        NOT NULL REFERENCES sellers(id) ON DELETE CASCADE,
    account_holder  VARCHAR(255) NOT NULL,
    account_number  VARCHAR(50) NOT NULL,    -- encrypted at application layer
    ifsc_code       VARCHAR(11) NOT NULL,
    bank_name       VARCHAR(100) NOT NULL,
    branch_name     VARCHAR(100),
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    is_verified     BOOLEAN NOT NULL DEFAULT false,
    verified_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bank_accounts_seller ON seller_bank_accounts (seller_id);

-- KYC documents uploaded by the seller
CREATE TABLE IF NOT EXISTS seller_kyc_documents (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id       UUID        NOT NULL REFERENCES sellers(id) ON DELETE CASCADE,
    document_type   VARCHAR(50) NOT NULL,  -- PAN_CARD, GST_CERTIFICATE, etc.
    document_url    VARCHAR(500) NOT NULL, -- S3 presigned URL or file path
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'  -- PENDING, APPROVED, REJECTED
);

CREATE INDEX idx_kyc_docs_seller ON seller_kyc_documents (seller_id);

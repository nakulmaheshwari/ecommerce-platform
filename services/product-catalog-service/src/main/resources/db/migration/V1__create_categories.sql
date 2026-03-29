-- Self-referential categories: Electronics > Phones > Smartphones
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    slug        VARCHAR(150) NOT NULL UNIQUE,  -- URL-friendly: "smart-phones"
    parent_id   UUID REFERENCES categories(id) ON DELETE SET NULL,
    description TEXT,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_categories_parent   ON categories(parent_id);
CREATE INDEX idx_categories_slug     ON categories(slug);
CREATE INDEX idx_categories_active   ON categories(is_active);

INSERT INTO categories (id, name, slug, sort_order) VALUES
    ('a1b2c3d4-0001-0001-0001-000000000001', 'Electronics',   'electronics',    1),
    ('a1b2c3d4-0002-0002-0002-000000000002', 'Clothing',      'clothing',       2),
    ('a1b2c3d4-0003-0003-0003-000000000003', 'Books',         'books',          3),
    ('a1b2c3d4-0004-0004-0004-000000000004', 'Home & Kitchen','home-kitchen',   4);

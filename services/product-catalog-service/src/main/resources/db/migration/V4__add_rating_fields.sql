-- Add rating fields to products table
ALTER TABLE products ADD COLUMN average_rating DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE products ADD COLUMN total_reviews INTEGER NOT NULL DEFAULT 0;

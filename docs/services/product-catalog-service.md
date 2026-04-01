# Product Catalog Service

**Port:** `8082` | **Database:** `product_db` (PostgreSQL) | **Base path:** `/api/v1`

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture Decisions](#architecture-decisions)
3. [Database Schema](#database-schema)
4. [API Reference](#api-reference)
5. [Inter-Service Communication](#inter-service-communication)
6. [Events Published](#events-published)
7. [Caching Strategy](#caching-strategy)
8. [Configuration](#configuration)
9. [Running Locally](#running-locally)

---

## Overview

The Product Catalog Service is the core authority for all product and category information. It manages:

- **Hierarchical Categories**: Parent-child relationship for organized navigation.
- **Product Details**: Rich descriptions, brands, pricing (in Paise), and metadata.
- **Variants & Images**: Support for multiple product versions (sizes, colors) and image galleries.
- **SEO Optimization**: Automatic slug generation and management for permanent links.
- **Product Lifecycle**: Draft and Published states for controlled release.
- **Search Enrichment**: Publishing events via the transactional outbox to keep the Search Service in sync.

---

## Architecture Decisions

### 1. Hierarchical Categories

Categories are managed via a nested parent-child structure. This allows for deep hierarchies (e.g., *Electronics > Computers > Laptops*). 
- **Fetch optimization**: The `categoryRepository` uses `JOIN FETCH` to retrieve the entire root hierarchy in a single query when needed for the navigation menu.

### 2. Transactional Outbox Pattern

To ensure that the **Search Service** and other consumers are always consistent with the database, we use the transactional outbox pattern:
1. Product changes are saved to the database.
2. A record is added to the `outbox_events` table **in the same transaction**.
3. A background poller (`OutboxPoller`) picks up unpublished events and sends them to Kafka.

This guarantees "at-least-once" delivery, even if Kafka or the service itself restarts mid-operation.

### 3. SEO-Friendly Slugs

Instead of exposing internal UUIDs, the service generates human-readable "slugs" from product names (e.g., `iphone-15-pro-max`).
- **Uniqueness**: The service automatically appends numerical suffixes (e.g., `-1`, `-2`) if a name collision occurs.
- **Persistence**: Once a slug is set and published, it remains fixed to prevent broken links.

### 4. Reactive Star Ratings

Although the Review Service owns individual ratings, the Product Catalog Service maintains a denormalized `average_rating` and `total_reviews` for O(1) reads on listing pages.
- **Real-time update**: It consumes `review.approved` events from Kafka and updates the local product record atomically.

---

## Database Schema

### `categories`
| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID (PK) | |
| `parent_id` | UUID | FK to self (nullable) |
| `name` | VARCHAR(100) | |
| `slug` | VARCHAR(120) | Unique |
| `is_active` | BOOLEAN | Soft-disable |

### `products`
| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID (PK) | |
| `category_id` | UUID | FK to `categories` |
| `sku` | VARCHAR(50) | Unique indexing |
| `name` | VARCHAR(255) | |
| `slug` | VARCHAR(280) | Unique |
| `price_paise` | BIGINT | Currency-agnostic integer price |
| `average_rating` | NUMERIC(3,2) | Denormalized for listing pages |
| `status` | VARCHAR(20) | `DRAFT`, `PUBLISHED` |

### `product_variants`
| Column | Type | Notes |
|--------|------|-------|
| `product_id` | UUID | FK to `products` |
| `attributes` | JSONB | e.g. `{"color": "silver", "storage": "256GB"}`|

---

## API Reference

### Public Endpoints

#### `GET /api/v1/products/{id}`
Returns full product details, including images and variants.

#### `GET /api/v1/products/slug/{slug}`
Used for SEO-friendly product pages.

#### `GET /api/v1/categories/{categoryId}/products`
Paginated list of products in a specific category.
- **Params**: `page`, `size`, `sortBy` (`price_asc`, `price_desc`, `newest`).

#### `GET /api/v1/categories`
Returns the complete hierarchical category tree.

### Admin Endpoints (`Role: ADMIN`)

#### `POST /api/v1/admin/products`
Create a new product in `DRAFT` status.

#### `POST /api/v1/admin/products/{id}/publish`
Makes a product visible to public read APIs and triggers a `product.published` event.

---

## Inter-Service Communication

### Published Events (Kafka)
| Event | Topic | Purpose |
|-------|-------|---------|
| `product.created` | `product-created` | Analytics, audit |
| `product.published`| `product-published`| Trigger search indexing |
| `product.updated`  | `product-updated`  | Sync search index (price, ratings) |

### Consumed Events (Kafka)
| Topic | Event Type | Action |
|-------|------------|--------|
| `review-approved` | `review.approved` | Update product `average_rating` and `total_reviews` |

---

## Caching Strategy

The service uses **Spring Cache with Redis** for high-frequency read operations:
- **Product Details**: Cached by ID and Slug (TTL: 10 mins).
- **Related Products**: Cached for 30 mins to reduce complex category joins.
- **Category Tree**: Cached globally and invalidated only on admin category changes.

---

## Configuration

The service pulls its configuration from the **Config Server**. Key properties:
- `spring.datasource.url`: Database connection.
- `spring.data.redis.host`: Cache backend.
- `spring.kafka.bootstrap-servers`: Message broker.

---

## Running Locally

### Prerequisites
- PostgreSQL (`product_db` database)
- Redis
- Kafka

### Build & Run
```bash
mvn clean install -pl services/product-catalog-service -am -DskipTests
java -jar services/product-catalog-service/target/product-catalog-service-1.0.0-SNAPSHOT.jar
```

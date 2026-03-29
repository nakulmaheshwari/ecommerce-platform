# Review Service

**Port:** `8090` | **Database:** `review_db` (PostgreSQL) | **Base path:** `/api/v1`

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture Decisions](#architecture-decisions)
3. [Database Schema](#database-schema)
4. [API Reference](#api-reference)
5. [Inter-Service Communication](#inter-service-communication)
6. [Events Published](#events-published)
7. [Configuration](#configuration)
8. [Running Locally](#running-locally)

---

## Overview

The Review Service owns all product review data for the platform. It is responsible for:

- Accepting and moderating product reviews (PENDING → APPROVED / REJECTED)
- Maintaining a **pre-computed running aggregate** (average rating, distribution) per product for O(1) reads
- Verifying that a reviewer is a **genuine purchaser** by querying the Order Service
- Managing **helpfulness voting** per review
- Tracking **abuse reports** and auto-flagging reviews at a threshold
- Publishing review events to Kafka via the **transactional outbox pattern**

---

## Architecture Decisions

### 1. Running Aggregate vs. Compute-on-Read

**Wrong approach for production:**
```sql
SELECT AVG(rating), COUNT(*) FROM reviews WHERE product_id = ?;
```
At 10,000 product page loads/second, this is 10,000 full index scans/second.

**This service's approach:** a `review_aggregates` table stores `total_reviews`, `total_score`, and `average_rating` (precomputed). Updated atomically on every approved review. Reading is a single PK lookup — O(1) regardless of review count.

### 2. Pessimistic Locking on Aggregate

The aggregate update is a read-modify-write operation. Two concurrent review submissions for the same product would cause a lost-update without locking:

```
Thread A reads total=100 → Thread B reads total=100
Thread A writes 101      → Thread B writes 101  ← WRONG, should be 102
```

`ReviewAggregateRepository.findByProductIdForUpdate()` uses `LockModeType.PESSIMISTIC_WRITE` (SELECT FOR UPDATE). Thread B waits until Thread A commits.

### 3. Verified Purchase Badge

When a review is submitted, we call Order Service:
```
GET /api/v1/internal/orders/verified-purchase?userId=&productId=
```
If the user ordered the product → `verifiedPurchase = true` (badge shown).

**Fallback:** if Order Service is down (circuit breaker open), we allow the review with `verifiedPurchase = false`. We never block review submission due to an unrelated outage.

### 4. Moderation State Machine

```
PENDING ──→ APPROVED ──→ FLAGGED (at 5 reports)
   │              │           │
   │              └───────────┴──→ REJECTED
   └─────────────────────────────→ REJECTED
```

- All reviews start **PENDING** (ready to insert content moderation pipeline)
- Current implementation auto-approves (simulating instant moderation pass)
- At **5 user reports**, review is auto-moved to **FLAGGED** and removed from public view, pending human review

### 5. Transactional Outbox Pattern

Review events are never published to Kafka directly. Instead:
1. The event is saved to `outbox_events` **in the same DB transaction** as the review
2. `OutboxPoller` (every 500ms) publishes unpublished events to Kafka and marks them `published = true`

This guarantees at-least-once delivery. If Kafka is down, events accumulate in the DB and are published when Kafka recovers.

---

## Database Schema

### `reviews` (core table)

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID (PK) | Auto-generated |
| `product_id` | UUID | FK reference (not enforced cross-service) |
| `user_id` | UUID | Internal user ID |
| `reviewer_keycloak_id` | UUID | JWT `sub` claim — for auth without calling User Service |
| `reviewer_name` | VARCHAR(200) | **Snapshot at review time** — name changes don't affect old reviews |
| `rating` | SMALLINT | 1–5, DB CHECK enforced |
| `title` | VARCHAR(200) | Optional headline |
| `body` | TEXT | Min 10 chars (API validation) |
| `verified_purchase` | BOOLEAN | Set by system only (never user input) |
| `status` | VARCHAR(20) | `PENDING`, `APPROVED`, `REJECTED`, `FLAGGED` |
| `helpful_votes` | INTEGER | Denormalized for fast sorts |
| `total_votes` | INTEGER | Used to compute helpfulness ratio |
| `report_count` | INTEGER | Auto-flags at 5 |
| `moderated_by` | UUID | Admin user ID (nullable = system-moderated) |
| `rejection_reason` | TEXT | Set when status = REJECTED |

**Unique constraint:** `(product_id, user_id)` — one review per user per product.

**Indexes:**
- `(product_id, status, created_at DESC)` — main product reviews query
- `(user_id, created_at DESC)` — "My reviews" page
- `(status, report_count DESC) WHERE status IN ('PENDING','FLAGGED')` — moderation queue

### `review_aggregates` (pre-computed stats)

| Column | Type | Notes |
|--------|------|-------|
| `product_id` | UUID (PK) | One row per product |
| `total_reviews` | INTEGER | Count of approved reviews |
| `total_score` | INTEGER | Sum of all ratings |
| `average_rating` | NUMERIC(3,2) | Precomputed: `total_score / total_reviews` |
| `rating_1_count` – `rating_5_count` | INTEGER | Distribution for histogram |
| `last_review_at` | TIMESTAMPTZ | Latest approved review timestamp |

### `review_votes` (helpfulness voting)

| Column | Notes |
|--------|-------|
| `(review_id, user_id)` UNIQUE | One vote per user per review |
| `is_helpful` | TRUE = helpful, FALSE = not helpful |

### `review_reports` (abuse tracking)

| Column | Notes |
|--------|-------|
| `(review_id, reporter_id)` UNIQUE | Can't report the same review twice |
| `reason` | `SPAM`, `FAKE`, `OFFENSIVE`, `IRRELEVANT`, `OTHER` |

### `outbox_events` (transactional outbox)

| Column | Notes |
|--------|-------|
| `event_type` | `review.submitted`, `review.approved` |
| `payload` | JSONB |
| `published` | FALSE until OutboxPoller publishes to Kafka |

---

## API Reference

### Public Endpoints (no authentication required)

---

#### `GET /api/v1/products/{productId}/reviews`

Returns paginated reviews AND the aggregate in a single response. Used by the product detail page.

**Query parameters:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | `0` | Zero-based page number |
| `size` | `10` | Max 50 per request |
| `verifiedOnly` | `false` | Show only verified purchase reviews |

**Response `200 OK`:**
```json
{
  "reviews": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "productId": "a1b2c3d4-...",
      "userId": "u1u2u3u4-...",
      "reviewerName": "john_doe",
      "rating": 5,
      "title": "Excellent product!",
      "body": "Bought last month, very happy with quality.",
      "verifiedPurchase": true,
      "status": "APPROVED",
      "helpfulVotes": 42,
      "totalVotes": 50,
      "helpfulnessRatio": 0.84,
      "createdAt": "2026-03-15T10:30:00Z",
      "updatedAt": "2026-03-15T10:30:00Z"
    }
  ],
  "aggregate": {
    "productId": "a1b2c3d4-...",
    "totalReviews": 427,
    "averageRating": 4.37,
    "rating5Count": 220, "rating5Percent": 52,
    "rating4Count": 130, "rating4Percent": 30,
    "rating3Count": 50,  "rating3Percent": 12,
    "rating2Count": 20,  "rating2Percent": 5,
    "rating1Count": 7,   "rating1Percent": 2
  },
  "page": 0,
  "size": 10,
  "totalElements": 427,
  "totalPages": 43,
  "hasNext": true
}
```

---

#### `GET /api/v1/products/{productId}/reviews/aggregate`

Lightweight endpoint for just the star rating summary. Used by product listing pages (no full review text needed).

**Response `200 OK`:** `ReviewAggregateResponse` (same `aggregate` object as above).

---

### Authenticated Endpoints (`Authorization: Bearer <token>`)

---

#### `POST /api/v1/reviews`

Submit a new review. Requires `CUSTOMER` or `ADMIN` role.

**Request body:**
```json
{
  "productId": "a1b2c3d4-0001-0001-0001-000000000001",
  "rating": 5,
  "title": "Excellent product!",
  "body": "Really loved this, would buy again. Great value for money."
}
```

| Field | Type | Constraints |
|-------|------|-------------|
| `productId` | UUID | Required |
| `rating` | Integer | Required, 1–5 |
| `title` | String | Optional, max 200 chars |
| `body` | String | Optional, 10–5000 chars |

**Response `201 Created`:** `ReviewResponse`

**Errors:**
- `409 Conflict` — User already reviewed this product
- `400 Bad Request` — Validation failure

---

#### `GET /api/v1/users/me/reviews`

Returns the authenticated user's own reviews (all statuses — including PENDING and REJECTED, visible only to the owner).

**Query params:** `page` (default 0), `size` (default 10)

**Response `200 OK`:** `ReviewPageResponse` (aggregate is null)

---

#### `POST /api/v1/reviews/{reviewId}/vote`

Mark a review as helpful or not helpful.

**Request body:**
```json
{ "helpful": true }
```

**Business rules:**
- You cannot vote on your own review → `400 Bad Request`
- Voting the same way again is idempotent (no change)
- Changing your vote (helpful → not helpful) adjusts `helpfulVotes` but not `totalVotes`

**Response `200 OK`:** Updated `ReviewResponse`

---

#### `POST /api/v1/reviews/{reviewId}/report`

Report an abusive/fake review.

**Request body:**
```json
{
  "reason": "FAKE",
  "details": "This reviewer never bought this product, account created yesterday."
}
```

| reason | Description |
|--------|-------------|
| `SPAM` | Promotional content |
| `FAKE` | Fabricated review |
| `OFFENSIVE` | Hate speech, harassment |
| `IRRELEVANT` | Unrelated to the product |
| `OTHER` | Other |

**Business rules:**
- One report per user per review → `409 Conflict` on second attempt
- At `report_count >= 5`: review auto-moves to `FLAGGED` (hidden from public)

**Response `202 Accepted`**

---

### Admin Moderation Endpoints (requires `ADMIN` role)

---

#### `POST /api/v1/admin/reviews/{reviewId}/moderate`

Approve or reject a review. Only works on `PENDING` or `FLAGGED` reviews.

**Request body:**
```json
{
  "action": "REJECT",
  "rejectionReason": "Spam: contains affiliate links and promotional content."
}
```

| action | Effect |
|--------|--------|
| `APPROVE` | Status → `APPROVED`, added to aggregate |
| `REJECT` | Status → `REJECTED`, removed from aggregate if was approved |

**Note:** `rejectionReason` is required when `action = REJECT`.

**Errors:**
- `400 Bad Request` — Invalid action or missing rejection reason
- `409 Conflict` — Review not in a moderatable state

**Response `200 OK`:** Updated `ReviewResponse`

---

#### `GET /api/v1/admin/reviews/moderation-queue`

Lists reviews in a given status for human moderation.

**Query params:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `status` | `PENDING` | `PENDING` or `FLAGGED` |
| `page` | `0` | Zero-based page number |
| `size` | `20` | Reviews per page |

Sorted by: `report_count DESC, created_at ASC` (most-reported first).

**Response `200 OK`:** `ReviewPageResponse`

---

## Inter-Service Communication

### Synchronous (Feign + Circuit Breaker)

| Caller | Direction | Target | Endpoint | Purpose |
|--------|-----------|--------|----------|---------|
| Review Service | → | Order Service | `GET /api/v1/internal/orders/verified-purchase?userId=&productId=` | Check if user purchased the product |

**Circuit Breaker config (`orderService`):**
- Sliding window: 10 calls
- Failure threshold: 50%
- Open state wait: 20s
- **Fallback:** return `false` (allow review without verified badge)

### Asynchronous (Kafka — produced by Review Service)

| Topic | Event Type | Payload | Consumers |
|-------|------------|---------|-----------|
| `review-submitted` | `review.submitted` | `{ reviewId, productId, userId, rating, verified }` | Notification Service |
| `review-approved` | `review.approved` | `{ reviewId, productId, averageRating, totalReviews }` | Product Catalog Service (for search index update) |

---

## Events Published

### `review.submitted`
Published when a new review is submitted (status moves to APPROVED in current implementation).

```json
{
  "reviewId": "3fa85f64-...",
  "productId": "a1b2c3d4-...",
  "userId": "u1u2u3u4-...",
  "rating": 5,
  "verified": true
}
```

### `review.approved`
Published when a moderator explicitly approves a review that was in PENDING or FLAGGED state.

```json
{
  "reviewId": "3fa85f64-...",
  "productId": "a1b2c3d4-...",
  "averageRating": 4.37,
  "totalReviews": 428
}
```

---

## Configuration

`services/review-service/src/main/resources/application.yml`

```yaml
server:
  port: 8090

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/review_db
    username: ecom_admin
    password: ecom_secret_123

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
      properties:
        enable.idempotence: true   # Exactly-once producer semantics

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8180/realms/ecommerce

resilience4j:
  circuitbreaker:
    instances:
      orderService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 20s
```

---

## Running Locally

### Prerequisites
- PostgreSQL running (`review_db` database must exist)
- Kafka running on port 9092
- Keycloak running on port 8180
- Eureka running on port 8761

### Create database
```bash
psql -U ecom_admin -d postgres -c "CREATE DATABASE review_db;"
```

### Build
```bash
cd ecommerce-platform
mvn clean install -pl services/review-service -am -DskipTests
```

### Run
```bash
java -jar services/review-service/target/review-service-1.0.0-SNAPSHOT.jar
```

### Verify
```bash
curl http://localhost:8090/actuator/health
# Expected: {"status":"UP"}
```

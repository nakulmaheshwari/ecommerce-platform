# User Service — Technical Documentation

**Port:** 8087  
**Database:** PostgreSQL (`user_db`)  
**Context Path:** `/`  
**Security:** OAuth2 (Keycloak) + JWT  
**Kafka Topics:** `user-registered` (Consumer), `user-profile-updated` (Producer)

---

## Overview

The User Service is responsible for managing user profiles, address books, and preferences. It is decoupled from the Identity Service (auth/credentials) to ensure separation of concerns and data compliance (PII management).

### Key Features
- **Automatic Profile Creation**: Consumes `user.registered` events from Identity Service to bootstrap profiles.
- **Address Book**: Manages multiple shipping addresses with soft-delete and default address logic.
- **Flexible Preferences**: Uses PostgreSQL `JSONB` to store user-defined preferences without schema changes.
- **Transactional Outbox**: Ensures profile updates are reliably published to Kafka for downstream services (Search, Recommendations).

---

## Domain Model

### UserProfile
Stored in `user_profiles` table:
- `id` (UUID): Internal unique identifier.
- `keycloak_id` (UUID): Link to Keycloak user (extracted from JWT `sub`).
- `email` (String): Denormalized from Identity Service for easier access.
- `preferences` (JSONB): Schema-free settings (e.g., `{"newsletter": true}`).

### Address
Stored in `addresses` table:
- `user_id` (FK): Links to `UserProfile`.
- `is_default` (Boolean): Only one default per user.
- `is_deleted` (Boolean): Soft-delete flag to maintain historical order integrity.

---

## Event Flows

### 1. Profile Creation (Async)
1. User registers via Identity Service.
2. Identity Service publishes `user-registered` event.
3. User Service consumes event → Creates `UserProfile` (idempotent check by `keycloak_id`).

### 2. Profile Update (Outbox Pattern)
1. User updates profile info via REST API.
2. Database update and `OutboxEvent` creation happen in one transaction.
3. `OutboxPoller` picks up unpublished events → Publishes `user-profile-updated` to Kafka.

---

## Internal API

### Get Default Address (for Order Service)
- **Endpoint**: `GET /api/v1/internal/users/{userId}/default-address`
- **Security**: Requires `ROLE_INTERNAL_SERVICE` or `ROLE_ADMIN`.
- **Response**: Flat JSON map of address fields for snapshotting in orders.

---

## DB State Inspection

```sql
-- View profile and preferences
SELECT email, first_name, preferences FROM user_profiles WHERE email = 'test@example.com';

-- View address book (including soft-deleted)
SELECT label, full_name, is_default, is_deleted FROM addresses WHERE user_id = 'user-uuid';

-- Check outbox status
SELECT event_type, published, created_at FROM outbox_events ORDER BY created_at DESC;
```

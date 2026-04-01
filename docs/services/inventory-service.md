# Inventory Service

**Port:** `8083` | **Database:** `inventory_db` (PostgreSQL) | **Base path:** `/api/v1`

---

## Table of Contents

1. [Overview](#overview)
2. [Deep-Dive: High-Concurrency Resilience](#deep-dive-high-concurrency-resilience)
3. [Deep-Dive: Transactional Integrity](#deep-dive-transactional-integrity)
4. [Deep-Dive: Point-in-Time Audit Logging](#deep-dive-point-in-time-audit-logging)
5. [Database Schema](#database-schema)
6. [API Reference](#api-reference)
7. [Inter-Service Communication](#inter-service-communication)
8. [Scheduled Tasks](#scheduled-tasks)
9. [Configuration](#configuration)
10. [Running Locally](#running-locally)

---

## Overview

The Inventory Service is responsible for the absolute accuracy of stock levels across the entire platform. It is a critical component in the order-to-cash lifecycle, ensuring that we never oversell a product or lose track of a reservation.

### Core Responsibilities:
- **Synchronous Availability Checks**: High-speed SKU queries during the checkout process.
- **Asynchronous Reservations**: Atomic stock "holding" during order placement.
- **Transactional Consistency**: Managing the transition of stock from *Available* to *Reserved* to *Committed/Released*.
- **Audit Trails**: Historical logging of every single stock movement with before/after snapshots.
- **Inbound Logistics**: Admin tools for updating stock levels with descriptive notes.

---

## Deep-Dive: High-Concurrency Resilience

### The "Overselling" Problem
Traditional inventory systems often use a **Select-then-Update** pattern:
1. `SELECT available_qty FROM inventory WHERE sku_id = 'A';`
2. Application checks if `available_qty >= requested_qty`.
3. `UPDATE inventory SET available_qty = ...`

At high scale (e.g., a flash sale), two threads can both see `available_qty = 1` and both succeed in their update, resulting in `-1` stock (overselling).

### Our Strategy: Atomic SQL Updates
The Inventory Service solves this by using a **single atomic SQL statement** as its entire concurrency logic:

```sql
UPDATE inventory
SET available_qty = available_qty - :qty,
    reserved_qty  = reserved_qty  + :qty,
    updated_at    = NOW()
WHERE sku_id = :skuId
  AND available_qty >= :qty;  -- THE CRITICAL LOCK
```

**Why this works:**
1. **Row-Level Locking**: PostgreSQL automatically acquires an exclusive lock on the row before the update.
2. **Atomic Condition**: If two threads try to reserve the last item, the first one succeeds and updates the row. The second thread's `WHERE` clause (`available_qty >= :qty`) will now be false, and the query will return `rows updated = 0`.
3. **No Deadlocks**: Because we only perform a single update per SKU, we avoid complex lock-wait chains that lead to deadlocks.

---

## Deep-Dive: Transactional Integrity

Inventory follows a strict **Reserve ➔ Confirm/Release** state machine for every order.

### Phase 1: Reservation (HELD)
Triggered by `order.placed`. Stock moves from `available_qty` to `reserved_qty`.
- **Status**: `HELD`
- **Result**: `inventory.reserved` (success) OR `inventory.reservation-failed` (insufficient stock).

### Phase 2: Confirmation (CONFIRMED)
Triggered by `payment.succeeded`. 
- Stock is removed from `reserved_qty` (it was already removed from `available_qty` in Phase 1).
- **Status**: `CONFIRMED`
- **Logic**: The stock is now "spent" and no longer exists in any pool.

### Phase 3: Release (RELEASED / EXPIRED)
Triggered by `payment.failed`, `order.cancelled`, or a timeout.
- Stock is moved back from `reserved_qty` to `available_qty`.
- **Status**: `RELEASED` or `EXPIRED`.

---

## Deep-Dive: Point-in-Time Audit Logging

Every change to the `inventory` table triggers a record in `inventory_movements`. This provides a forensic trail of stock history.

| Column | Description |
|--------|-------------|
| `sku_id` | The affected item. |
| `movement_type` | `INBOUND`, `RESERVATION`, `CONFIRMATION`, `RELEASE`, `INITIAL`. |
| `quantity_delta` | The change amount (positive or negative). |
| `before_qty` | Snapshot of available stock **before** the change. |
| `after_qty` | Snapshot of available stock **after** the change. |
| `reference_id` | UID of the Order, Product, or Purchase Order that caused the move. |

---

## Database Schema

### `inventory` (The Master Table)
Includes a DB-level check constraint: `CHECK (available_qty >= 0)`.

### `reservations`
Tracks the lifecycle of stock associated with an order.

### `inventory_movements`
The immutable audit log.

---

## API Reference

### Availability Check (Public-Internal)

#### `GET /api/v1/availability`
Batch check for multiple SKUs. Used by Cart and Order services.
- **Request Body**: `{"SKU-1": 2, "SKU-2": 1}`
- **Response**: `{"isAvailable": true, "unavailableSkus": [], "availableQtys": {"SKU-1": 10, "SKU-2": 5}}`

### Admin Endpoints

#### `POST /api/v1/admin/stock/inbound`
Add new physical stock to the system.
- **Fields**: `skuId`, `quantity`, `notes` (mandatory for audit).

---

## Inter-Service Communication

### Consumed Events (Kafka)
| Topic | Action |
|-------|--------|
| `product-created` | Initializes a zero-stock record for a new SKU. |
| `order-placed` | Triggers a stock reservation. |
| `payment-success` | Triggers stock confirmation. |
| `payment-failure` | Triggers stock release. |

### Published Events (Kafka)
| Topic | Purpose |
|-------|---------|
| `inventory-reserved` | Success signal for the Order saga. |
| `inventory-failed` | Failure signal for the Order saga. |
| `inventory-low` | Trigger for replenishment alerts. |

---

## Scheduled Tasks

### `ReservationExpiryJob`
**Runs every 5 minutes.**
Identifies `HELD` reservations that have exceeded the 15-minute payment window and automatically releases the stock back to the available pool.

---

## Configuration

Standard service configuration applies. Note the `reorder-point` setting which triggers the `inventory-low` event.

---

## Running Locally

```bash
mvn clean install -pl services/inventory-service -am -DskipTests
java -jar services/inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar
```

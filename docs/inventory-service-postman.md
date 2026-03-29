# Inventory Service — Postman Testing Guide

**Base URL:** `http://localhost:8083`  
**Port:** 8083  
**Database:** PostgreSQL (`inventory_db`)  
**Auth:** GET is public. POST availability is public (internal). Add-stock requires ADMIN.

---

## How Inventory Works — Key Concepts

Every SKU has three quantities:

| Field | Meaning |
|---|---|
| `availableQty` | Stock available for new orders right now |
| `reservedQty` | Held for placed orders, awaiting payment confirmation |
| `totalQty` | `available + reserved` — total physical stock |

**Reservation lifecycle:**
```
Order placed    → availableQty -N, reservedQty +N   (HELD)
Payment success → reservedQty -N, stock is consumed  (CONFIRMED)
Payment failed  → reservedQty -N, availableQty +N    (RELEASED)
No payment 15m → reservedQty -N, availableQty +N    (EXPIRED by scheduler)
```

**Oversell prevention** uses atomic SQL — no SELECT then UPDATE:
```sql
UPDATE inventory
SET available_qty = available_qty - ?,
    reserved_qty  = reserved_qty  + ?
WHERE sku_id = ?
  AND available_qty >= ?   -- This WHERE clause is the lock
```
If `available_qty` is 0 and `quantity` is 1, `available_qty >= 1` is false → 0 rows updated → reservation fails automatically.

---

## Postman Environment

Use the **EcomPlatform Local** environment from the Cart/Order guides. Add:

| Variable | Value |
|---|---|
| `base_url_inventory` | `http://localhost:8083` |
| `test_sku` | `SKU-LAPTOP-PRO-16-BLK` |
| `admin_token` | _(JWT with ADMIN role from Keycloak)_ |

---

## 1. Get Inventory for a SKU (Public)

No authentication required — product pages use this to show "In Stock" / "Out of Stock".

### Request
```
GET {{base_url_inventory}}/api/v1/inventory/{{test_sku}}
```

### Response (200 OK)
```json
{
  "skuId": "SKU-LAPTOP-PRO-16-BLK",
  "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "availableQty": 47,
  "reservedQty": 3,
  "totalQty": 50,
  "lowStock": false,
  "outOfStock": false,
  "warehouseId": "WH-MUMBAI-01",
  "updatedAt": "2026-03-28T12:00:00Z"
}
```

### Response — Low Stock
```json
{
  "availableQty": 3,
  "reservedQty": 2,
  "totalQty": 5,
  "lowStock": true,
  "outOfStock": false
}
```
(`lowStock = true` when `availableQty <= lowStockThreshold`, typically 5)

### Response — Out of Stock
```json
{
  "availableQty": 0,
  "reservedQty": 2,
  "totalQty": 2,
  "lowStock": true,
  "outOfStock": true
}
```

### Error Cases
| Scenario | Response |
|----------|---------|
| SKU not in inventory | `404 Not Found` — `"Inventory SKU-XXX not found"` |
| Typo in SKU | `404 Not Found` |

### Tests Script
```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
pm.test("Has inventory fields", () => {
    const j = pm.response.json();
    pm.expect(j).to.have.property("availableQty");
    pm.expect(j).to.have.property("reservedQty");
    pm.expect(j.totalQty).to.equal(j.availableQty + j.reservedQty);
    pm.expect(j.outOfStock).to.equal(j.availableQty === 0);
});
```

---

## 2. Check Availability (Internal — called by Order Service)

Used by Order Service before creating an order. Can also be called directly to simulate the availability check.

### Request
```
POST {{base_url_inventory}}/api/v1/inventory/availability
Content-Type: application/json
```

### Body — Single SKU
```json
{
  "SKU-LAPTOP-PRO-16-BLK": 1
}
```

### Body — Multiple SKUs (realistic cart)
```json
{
  "SKU-LAPTOP-PRO-16-BLK": 1,
  "SKU-MOUSE-WIRELESS-WHT": 2,
  "SKU-HDMI-CABLE-2M": 1
}
```

### Response — All Available (200 OK)
```json
{
  "allAvailable": true,
  "unavailableSkus": [],
  "availableQuantities": {
    "SKU-LAPTOP-PRO-16-BLK": 47,
    "SKU-MOUSE-WIRELESS-WHT": 120,
    "SKU-HDMI-CABLE-2M": 35
  }
}
```

### Response — Some Out of Stock (200 OK, but `allAvailable: false`)
```json
{
  "allAvailable": false,
  "unavailableSkus": ["SKU-MOUSE-WIRELESS-WHT"],
  "availableQuantities": {
    "SKU-LAPTOP-PRO-16-BLK": 47,
    "SKU-MOUSE-WIRELESS-WHT": 1
  }
}
```

Note: `availableQuantities` shows **actual available**, not what was requested. `SKU-MOUSE-WIRELESS-WHT` has only 1 but 2 were requested — so it's in `unavailableSkus`.

### Tests Script
```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
pm.test("All available check", () => {
    const j = pm.response.json();
    pm.expect(j).to.have.property("allAvailable");
    pm.expect(j).to.have.property("unavailableSkus").that.is.an("array");
    pm.expect(j).to.have.property("availableQuantities");
    if (j.allAvailable) {
        pm.expect(j.unavailableSkus.length).to.equal(0);
    }
});
```

---

## 3. Add Stock (Admin Only)

Used when new inventory arrives from a supplier. Adds to `availableQty` and logs an `INBOUND` movement record.

### Request
```
POST {{base_url_inventory}}/api/v1/inventory/{{test_sku}}/add-stock
Authorization: Bearer {{admin_token}}
Content-Type: application/json
```

### Body
```json
{
  "quantity": 100,
  "notes": "Received from Supplier ABC PO-2026-0412"
}
```

### Response (200 OK)
```json
{
  "skuId": "SKU-LAPTOP-PRO-16-BLK",
  "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "availableQty": 147,
  "reservedQty": 3,
  "totalQty": 150,
  "lowStock": false,
  "outOfStock": false,
  "warehouseId": "WH-MUMBAI-01",
  "updatedAt": "2026-03-28T14:00:00Z"
}
```

### Error Cases
| Scenario | Response |
|----------|---------|
| No admin token | `403 Forbidden` |
| Customer token | `403 Forbidden` |
| `quantity < 1` | `400 Bad Request` — `"@Min(1) constraint"` |
| Missing `notes` | `400 Bad Request` — `"notes: must not be blank"` |
| SKU doesn't exist | `404 Not Found` |

### Tests Script
```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
pm.test("Stock increased", () => {
    const j = pm.response.json();
    pm.expect(j.availableQty).to.be.greaterThan(0);
    console.log("New available qty:", j.availableQty);
});
```

---

## 4. Health Check
```
GET {{base_url_inventory}}/actuator/health
```
```json
{ "status": "UP", "components": { "db": { "status": "UP" } } }
```

---

## 5. Kafka-Driven Operations (Observe via DB)

The following operations are **not REST endpoints** — they're triggered by Kafka events and run automatically. Observe their effects via the database.

### Reserve Stock (triggered by `order-placed` event)
Happens automatically when an order is placed. Within ~1 second:
- `available_qty` decreases
- `reserved_qty` increases
- A new `reservations` row with `status=HELD` and `expires_at = NOW() + 15 minutes`

### Release Stock (triggered by `payment-failed` event)
When payment fails, within ~1 second:
- `reserved_qty` decreases
- `available_qty` increases
- Reservation row `status` → `RELEASED`

### Expire Reservation (scheduled, runs every 5 minutes)
If payment never arrives within 15 minutes:
- Reservation `status` → `EXPIRED`
- Stock released back to `available_qty`

---

## 6. DB State Inspection

```
Host: localhost  Port: 5432  DB: inventory_db
User: ecom_admin  Password: ecom_secret_123
```

```sql
-- Current stock levels for a SKU
SELECT sku_id, available_qty, reserved_qty,
       available_qty + reserved_qty AS total_qty,
       low_stock_threshold, updated_at
FROM inventory
WHERE sku_id = 'SKU-LAPTOP-PRO-16-BLK';

-- All reservations for an order
SELECT sku_id, quantity, status, expires_at, created_at
FROM reservations
WHERE order_id = 'your-order-uuid'
ORDER BY created_at;

-- Expired/unreleased reservations (should be empty in normal operation)
SELECT id, sku_id, order_id, quantity, expires_at
FROM reservations
WHERE status = 'HELD'
  AND expires_at < NOW();

-- Movement audit trail for a SKU (full history)
SELECT movement_type, quantity_delta, before_qty, after_qty,
       reference_type, reference_id, created_at
FROM inventory_movements
WHERE sku_id = 'SKU-LAPTOP-PRO-16-BLK'
ORDER BY created_at DESC
LIMIT 20;

-- Outbox events (should all be published=TRUE)
SELECT event_type, published, created_at
FROM outbox_events
ORDER BY created_at DESC LIMIT 10;
```

---

## 7. Race Condition / Oversell Test

**Test that two concurrent orders for the last unit only one succeeds:**

1. Set `available_qty` to 1 for a SKU:
```sql
UPDATE inventory SET available_qty = 1 WHERE sku_id = 'SKU-LAPTOP-PRO-16-BLK';
```

2. Place two orders simultaneously (two terminals, same SKU):
- Both try to reserve qty=1 for the same SKU
- One will succeed (`updated=1`), one will fail (`updated=0`)
- Only 1 reservation row created
- `available_qty` never goes below 0

3. Verify:
```sql
SELECT available_qty, reserved_qty FROM inventory WHERE sku_id = 'SKU-LAPTOP-PRO-16-BLK';
-- available_qty: 0, reserved_qty: 1
SELECT COUNT(*) FROM reservations WHERE sku_id = 'SKU-LAPTOP-PRO-16-BLK' AND status = 'HELD';
-- Should be: 1 (not 2)
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `404` on GET SKU | SKU never seeded in inventory | `INSERT INTO inventory(sku_id, product_id, available_qty, ...)` |
| `409` on place order | availableQty < requested | Add stock via POST /{skuId}/add-stock |
| Order stuck, stock not reserved | Kafka event not consumed | Check `order-placed` topic in Kafka UI |
| `availableQty` went negative | Bug — should not happen | Check CONSTRAINT `available_qty >= 0` in schema |

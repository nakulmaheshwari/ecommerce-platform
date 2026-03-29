# Order Service — Postman Testing Guide

**Base URL:** `http://localhost:8085`  
**Port:** 8085  
**Database:** PostgreSQL (`order_db`)  
**Auth:** All endpoints require a valid Keycloak JWT  
**Depends on:** Cart Service (8084), Inventory Service (8083)

---

## Postman Environment Variables

Use the same **EcomPlatform Local** environment. Additional variables:

| Variable | Description |
|---|---|
| `access_token` | JWT from Keycloak (auto-populated by login request) |
| `order_id` | UUID of the last placed order (auto-populated by test script) |
| `idempotency_key` | UUID client generates before placing order |

---

## Step 0 — Get Auth Token

```
POST http://localhost:8180/realms/ecommerce/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
&client_id=ecommerce-frontend
&username=testcustomer
&password=Test@1234
```

**Tests tab:**
```javascript
if (pm.response.code === 200) {
    pm.environment.set("access_token", pm.response.json().access_token);
}
```

---

## Step 1 — Add Items to Cart (Prerequisite)

The Order Service fetches the cart at checkout time. The cart must have items before placing an order.

**Run these Cart Service requests first:**
```
POST http://localhost:8084/api/v1/cart/items
Authorization: Bearer {{access_token}}
Content-Type: application/json

{"skuId": "SKU-LAPTOP-PRO-16-BLK", "quantity": 1}
```

Then optionally:
```json
{"skuId": "SKU-MOUSE-WIRELESS-WHT", "quantity": 2}
```

Verify cart has items:
```
GET http://localhost:8084/api/v1/cart
Authorization: Bearer {{access_token}}
```

---

## 2. Place Order (POST /api/v1/orders)

This is the most important endpoint in the entire platform.

### Request
```
POST {{base_url_order}}/api/v1/orders
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

### Body
```json
{
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440001",
  "shippingAddress": {
    "fullName": "Nakul Sharma",
    "line1": "Flat 12B, Sunrise Apartments",
    "line2": "Andheri East",
    "city": "Mumbai",
    "state": "Maharashtra",
    "pincode": "400069",
    "country": "India",
    "phone": "+919876543210"
  },
  "notes": "Please leave at the door if nobody answers."
}
```

### idempotencyKey — CRITICAL
**Always generate a fresh UUID v4 for each new order attempt.**  
In Postman, you can auto-generate one:
- Body → use `{{$randomUUID}}` for ad-hoc testing  
- For retry tests, use a hardcoded UUID and send the same request twice

### Response (201 Created)
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "userId": "a1b2c3d4-0000-0000-0000-000000000001",
  "status": "PENDING",
  "currency": "INR",
  "subtotalPaise": 8999900,
  "subtotalRupees": 89999.0,
  "discountPaise": 0,
  "taxPaise": 1619982,
  "deliveryPaise": 0,
  "totalPaise": 10619882,
  "totalRupees": 106198.82,
  "shippingAddress": {
    "fullName": "Nakul Sharma",
    "line1": "Flat 12B, Sunrise Apartments",
    "line2": "Andheri East",
    "city": "Mumbai",
    "state": "Maharashtra",
    "pincode": "400069",
    "country": "India",
    "phone": "+919876543210"
  },
  "items": [
    {
      "id": "item-uuid-here",
      "skuId": "SKU-LAPTOP-PRO-16-BLK",
      "productId": "prod-uuid-here",
      "productName": "Laptop Pro 16",
      "variantName": "Black / 16GB RAM",
      "quantity": 1,
      "unitPricePaise": 8999900,
      "unitPriceRupees": 89999.0,
      "lineTotalPaise": 8999900,
      "lineTotalRupees": 89999.0,
      "imageUrl": "https://cdn.example.com/laptop-black.jpg"
    }
  ],
  "notes": "Please leave at the door if nobody answers.",
  "createdAt": "2026-03-28T12:45:00Z",
  "updatedAt": "2026-03-28T12:45:00Z"
}
```

### Pricing Logic (Server-Side — Never Trust Client)
```
subtotal   = sum(cartItem.pricePaise × quantity)
tax        = subtotal × 18%  (GST — simplified, all categories)
delivery   = ₹49 (4900 paise) if subtotal < ₹500 (50,000 paise), else ₹0
total      = subtotal + tax + delivery
```

**Example with ₹89,999 laptop:**
```
subtotal  = 8,999,900 paise (₹89,999)
tax 18%   = 1,619,982 paise (₹16,199.82)
delivery  = 0 (free — subtotal > ₹500)
total     = 10,619,882 paise (₹1,06,198.82)
```

**Example with ₹299 item:**
```
subtotal  = 29,900 paise (₹299)
tax 18%   = 5,382 paise (₹53.82)
delivery  = 4,900 paise (₹49 — subtotal < ₹500)
total     = 40,182 paise (₹401.82)
```

### What Happens Internally (7 Steps)
1. **Idempotency check** — look up `idempotency_key` in DB. If found, return existing order
2. **Fetch cart** — Feign client calls `GET /api/v1/cart/checkout` on Cart Service, forwarding JWT header
3. **Availability check** — Feign client calls Inventory Service with all SKU→quantity pairs
4. **Calculate totals** — server-side: subtotal, 18% GST, ₹49 delivery if < ₹500
5. **Create order + write outbox event** — ONE database transaction (Transactional Outbox)
6. **Clear cart** — best effort (fails silently; cart expires via TTL anyway)
7. **Return 201** with full order response

### Tests Script
```javascript
pm.test("Status is 201 Created", () => pm.response.to.have.status(201));
pm.test("Order has PENDING status", () => {
    const json = pm.response.json();
    pm.expect(json.status).to.equal("PENDING");
    pm.environment.set("order_id", json.id);
    console.log("Order ID saved:", json.id);
});
pm.test("Totals are calculated server-side", () => {
    const json = pm.response.json();
    pm.expect(json.totalPaise).to.be.greaterThan(json.subtotalPaise);
    pm.expect(json.taxPaise).to.be.greaterThan(0);
});
pm.test("Items are from cart", () => {
    const json = pm.response.json();
    pm.expect(json.items.length).to.be.greaterThan(0);
    pm.expect(json.items[0].skuId).to.be.a("string");
});
```

### Error Cases

| Scenario | Expected Response | Details |
|----------|-----------------|---------|
| Empty cart | `400 Bad Request` | `"Cannot place order with empty cart"` |
| Item out of stock | `409 Conflict` | `"Some items are out of stock: [SKU-XXX]"` |
| Missing idempotencyKey | `400 Bad Request` | `"must not be null"` |
| Missing shippingAddress | `400 Bad Request` | validation error |
| Missing required address field | `400 Bad Request` | e.g. `"city must not be null"` |
| No auth token | `401 Unauthorized` | |
| Same idempotencyKey reused | `201 Created` | **Same existing order returned** (not a duplicate) |

---

## 3. Test Idempotency — Safe Retry

Send **the exact same `placeOrder` request twice** with the same `idempotencyKey`.

**Expected:** Both requests return `201 Created` with the **same `order.id`**.  
No duplicate order created. Check the database to confirm only 1 order row exists for that key.

```sql
-- Connect to order_db
SELECT id, status, idempotency_key, created_at
FROM orders
WHERE idempotency_key = '550e8400-e29b-41d4-a716-446655440001';
-- Should return exactly 1 row
```

---

## 4. Get Order by ID

### Request
```
GET {{base_url_order}}/api/v1/orders/{{order_id}}
Authorization: Bearer {{access_token}}
```

### Response (200 OK)
Same shape as the `placeOrder` response. Status will be `PENDING` initially, then changes as the saga progresses.

### Authorization Rule
Users can **only read their own orders**. If User A tries to access User B's order ID, they get:
```json
{ "status": 403, "message": "Access denied to order {orderId}" }
```

### Tests Script
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Order belongs to current user", () => {
    const json = pm.response.json();
    pm.expect(json.id).to.equal(pm.environment.get("order_id"));
    pm.expect(json.items).to.be.an("array").with.length.above(0);
});
```

### Watch Status Change Through Saga
Poll this endpoint every few seconds after placing an order to watch the status change:

```
PENDING → AWAITING_PAYMENT → CONFIRMED → SHIPPED
```

The full state machine:
```
PENDING          → Created, outbox event written
AWAITING_PAYMENT → Payment Service picked up the event, Razorpay order created
CONFIRMED        → Payment captured (Razorpay webhook confirmed)
PROCESSING       → Being packed (future step)
SHIPPED          → Shipping Service created shipment (order-shipped event consumed)
DELIVERED        → Customer confirmed delivery (future step)
CANCELLED        → Either payment failed OR explicitly cancelled
REFUNDED         → Money returned (after delivered)
```

---

## 5. Get My Orders (Paginated)

### Request
```
GET {{base_url_order}}/api/v1/orders?page=0&size=10
Authorization: Bearer {{access_token}}
```

### Response (200 OK)
```json
{
  "content": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "status": "CONFIRMED",
      "totalPaise": 10619882,
      "totalRupees": 106198.82,
      "items": [...],
      "createdAt": "2026-03-28T12:45:00Z"
    },
    {
      "id": "another-order-uuid",
      "status": "SHIPPED",
      "totalPaise": 40182,
      "totalRupees": 401.82,
      ...
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": { "sorted": true, "direction": "DESC" }
  },
  "totalElements": 5,
  "totalPages": 1,
  "last": true,
  "first": true,
  "size": 10,
  "number": 0
}
```

Orders are sorted by `createdAt DESC` — newest first.

### Pagination Examples
```
GET /api/v1/orders?page=0&size=5   → First 5 orders
GET /api/v1/orders?page=1&size=5   → Orders 6-10
GET /api/v1/orders?page=0&size=20  → First 20 orders
```

### Tests Script
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Response is paginated", () => {
    const json = pm.response.json();
    pm.expect(json).to.have.property("content");
    pm.expect(json).to.have.property("totalElements");
    pm.expect(json.content).to.be.an("array");
});
pm.test("Orders sorted newest first", () => {
    const json = pm.response.json();
    if (json.content.length > 1) {
        const first = new Date(json.content[0].createdAt);
        const second = new Date(json.content[1].createdAt);
        pm.expect(first >= second).to.be.true;
    }
});
```

---

## 6. Health Check

```
GET {{base_url_order}}/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "kafka": { "status": "UP" }
  }
}
```

---

## 7. Full End-to-End Saga Test

Run this complete sequence to see the entire saga play out.

### Step 1 — Login
```
POST http://localhost:8180/realms/ecommerce/protocol/openid-connect/token
grant_type=password&client_id=ecommerce-frontend&username=testcustomer&password=Test@1234
```
Save token.

### Step 2 — Add to Cart
```
POST http://localhost:8084/api/v1/cart/items
{"skuId": "SKU-LAPTOP-PRO-16-BLK", "quantity": 1}
```

### Step 3 — Place Order
```
POST http://localhost:8085/api/v1/orders
{
  "idempotencyKey": "{{$randomUUID}}",
  "shippingAddress": {
    "fullName": "Test User",
    "line1": "123 Test Street",
    "city": "Mumbai",
    "state": "Maharashtra",
    "pincode": "400001",
    "country": "India",
    "phone": "+919999999999"
  }
}
```
Save the `id` as `ORDER_ID`. Also note `status: "PENDING"`.

### Step 4 — Check Order Status (should be AWAITING_PAYMENT within ~2s)
```
GET http://localhost:8085/api/v1/orders/{{ORDER_ID}}
```
Payment Service should have created a Razorpay order and moved status to `AWAITING_PAYMENT`.

### Step 5 — Get Payment Details
```
GET http://localhost:8086/api/v1/payments/orders/{{ORDER_ID}}
Authorization: Bearer {{access_token}}
```
Save `razorpayOrderId` from response.

### Step 6 — Simulate Payment Webhook (Razorpay Test Mode)
Use Razorpay test dashboard OR send mock webhook to Payment Service. After webhook:
- Order status → `CONFIRMED`
- Shipping Service creates a shipment
- Notification Service sends confirmation email (check Mailhog)

### Step 7 — Verify Final State
```
GET http://localhost:8085/api/v1/orders/{{ORDER_ID}}
```
Expected: `status: "SHIPPED"` (after shipping service processes the `order-shipped` event)

```
GET http://localhost:8088/api/v1/shipments/orders/{{ORDER_ID}}
```
Expected: shipment with `trackingNumber`.

```
http://localhost:8025
```
Expected: 2 emails in Mailhog — confirmation + shipping.

---

## 8. DB State Inspection

Connect to PostgreSQL:
```
Host: localhost
Port: 5432
Database: order_db
User: ecom_admin
Password: ecom_secret_123
```

### Useful Queries
```sql
-- All orders for a user
SELECT id, status, total_paise/100.0 as total_rupees, created_at
FROM orders
WHERE user_id = 'a1b2c3d4-0000-0000-0000-000000000001'
ORDER BY created_at DESC;

-- Order items for a specific order
SELECT sku_id, product_name, quantity, unit_price_paise/100.0 as unit_price_rupees
FROM order_items
WHERE order_id = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

-- Full status history of an order (see every state transition)
SELECT from_status, to_status, reason, actor, created_at
FROM order_status_history
WHERE order_id = '3fa85f64-5717-4562-b3fc-2c963f66afa6'
ORDER BY created_at ASC;

-- Orders by status (count how many in each state)
SELECT status, COUNT(*) as count, SUM(total_paise)/100.0 as total_rupees
FROM orders
GROUP BY status;

-- Check outbox events (should all be published=TRUE)
SELECT event_type, published, created_at
FROM outbox_events
ORDER BY created_at DESC
LIMIT 20;

-- Stuck outbox events (should be empty in normal operation)
SELECT id, event_type, created_at
FROM outbox_events
WHERE published = FALSE
ORDER BY created_at ASC;

-- Idempotency test — check no duplicate orders for a key
SELECT id, status, created_at
FROM orders
WHERE idempotency_key = '550e8400-e29b-41d4-a716-446655440001';
-- Should return exactly 1 row
```

---

## 9. Kafka Event Inspection

After placing an order, inspect the `order-placed` topic in Kafka UI (`http://localhost:9093`):

**Expected payload on `order-placed` topic:**
```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "userId": "a1b2c3d4-0000-0000-0000-000000000001",
  "totalPaise": 10619882,
  "currency": "INR",
  "items": [
    {
      "skuId": "SKU-LAPTOP-PRO-16-BLK",
      "productId": "prod-uuid",
      "quantity": 1,
      "pricePaise": 8999900
    }
  ]
}
```

**Expected payload on `notification-triggered` topic (after payment confirmation):**
```json
{
  "userId": "a1b2c3d4-0000-0000-0000-000000000001",
  "channel": "EMAIL",
  "templateId": "order-confirmed-v1",
  "templateVars": {
    "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "totalRupees": "106198.82"
  }
}
```

---

## 10. Metrics

```
GET {{base_url_order}}/actuator/prometheus
```

Look for custom business metrics:
```
# Counter: total orders created
ecom_orders_created_total

# Counter: total orders cancelled
ecom_orders_cancelled_total

# Histogram: end-to-end order placement time
ecom_order_placement_duration_seconds_bucket
ecom_order_placement_duration_seconds_count
ecom_order_placement_duration_seconds_sum
```

**Grafana query to see order throughput:**
```promql
rate(ecom_orders_created_total[5m])
```

---

## Order Status State Machine Reference

```
PENDING           → created by placeOrder()
    ↓ (Payment Service creates Razorpay order)
AWAITING_PAYMENT  → Payment initiated
    ↓ (Razorpay webhook: payment.captured)
CONFIRMED         → Money received
    ↓
PROCESSING        → Being packed at warehouse
    ↓ (Shipping Service creates shipment)
SHIPPED           → Courier has it
    ↓
DELIVERED         → Customer received it
    ↓
REFUNDED          → Money returned (terminal)

PENDING / AWAITING_PAYMENT / CONFIRMED / PROCESSING → CANCELLED (terminal)
```

Invalid transitions throw `InvalidOrderTransitionException`. You cannot set a `DELIVERED` order back to `PENDING`.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `400 Bad Request` "empty cart" | Cart Service is empty | Add items first via Cart Service |
| `409 Conflict` "out of stock" | Inventory shows 0 quantity | Add inventory: `POST http://localhost:8083/api/v1/inventory/{sku}/adjust` |
| Order stays in `PENDING` forever | Outbox poller not running or Kafka down | Check `docker ps`, check outbox_events table |
| Order stays in `AWAITING_PAYMENT` | Payment Service down or Razorpay not called | Check payment-service logs |
| `403 Forbidden` on GET order | Wrong user token | Use same user who placed the order |
| `400` on idempotencyKey | Sending string not UUID format | Must be valid UUID v4 e.g. `550e8400-e29b-41d4-a716-446655440001` |
| Cart not cleared after order | Cart Service unreachable | Non-fatal; cart items stay until TTL expires |

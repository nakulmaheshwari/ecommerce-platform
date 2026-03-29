# Shipping Service — Postman Testing Guide

**Base URL:** `http://localhost:8088`  
**Port:** 8088  
**Database:** `shipping_db`

> **Prerequisites:** Shipping Service running, Payment Service running (for the end-to-end flow), valid JWT token from Keycloak.

---

## Auth Token Setup (Postman Environment)

Create a Postman environment variable `access_token`. Get it by:

```
POST http://localhost:8180/realms/ecommerce/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
&client_id=ecommerce-frontend
&username=testcustomer
&password=Test@1234
```

Copy the `access_token` value.

---

## 1. Health Check

### Request
```
GET http://localhost:8088/actuator/health
```

### Expected Response
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

## 2. Get Shipment by Order ID

This is the main endpoint customers and admins use to see shipment status.

### Request
```
GET http://localhost:8088/api/v1/shipments/orders/{orderId}
Authorization: Bearer {{access_token}}
```

Replace `{orderId}` with a real UUID from an order that completed payment.

### Expected Response (200 OK)
```json
{
  "id": "uuid",
  "orderId": "uuid",
  "userId": "uuid",
  "trackingNumber": "BD1711619423456",
  "carrier": "BlueDart",
  "status": "CREATED",
  "shippingAddress": {
    "note": "Fetch from order service in production"
  },
  "estimatedDeliveryDate": "2026-03-31",
  "createdAt": "2026-03-28T09:00:00Z"
}
```

### Error Cases
| Scenario | Response |
|----------|----------|
| Order ID not found | `404 Not Found` with `{"message": "Shipment not found"}` |
| No auth token | `401 Unauthorized` |

---

## 3. Track Shipment by Tracking Number (Public Endpoint)

This endpoint does NOT require authentication — it's the public tracking URL you'd put in the email.

### Request
```
GET http://localhost:8088/api/v1/shipments/track/{trackingNumber}
```

No Authorization header needed.

Replace `{trackingNumber}` with the value like `BD1711619423456`.

### Expected Response (200 OK)
```json
{
  "id": "uuid",
  "orderId": "uuid",
  "trackingNumber": "BD1711619423456",
  "carrier": "BlueDart",
  "status": "CREATED",
  "estimatedDeliveryDate": "2026-03-31"
}
```

### Test It Without Token
This should work even in an incognito browser tab or without any auth header in Postman.

---

## 4. Mark Shipment as Shipped (Admin Only)

This simulates a carrier picking up the package and moving it to IN_TRANSIT.

### Request
```
POST http://localhost:8088/api/v1/shipments/orders/{orderId}/mark-shipped
Authorization: Bearer {{admin_token}}
```

> ⚠️ Requires ADMIN role. Get admin token from Keycloak using an admin user.

### Expected Response (200 OK)
```json
{
  "id": "uuid",
  "orderId": "uuid",
  "status": "IN_TRANSIT",
  "shippedAt": "2026-03-28T10:00:00Z"
}
```

### Error Cases
| Scenario | Response |
|----------|----------|
| Customer token (not admin) | `403 Forbidden` |
| Order not found | `404 Not Found` |

---

## 5. Trigger Shipping via Kafka (Simulate payment-succeeded)

The Shipping Service creates shipments by consuming `payment-succeeded` from Kafka. You can test this without going through the full saga:

### Step 1 — Open Kafka UI
`http://localhost:9093` → Topics → `payment-succeeded` → Produce Message

### Step 2 — Publish
**Key:** (any UUID)

**Value:**
```json
{
  "paymentId": "33333333-3333-3333-3333-333333333333",
  "orderId": "44444444-4444-4444-4444-444444444444",
  "userId": "11111111-1111-1111-1111-111111111111",
  "amountPaise": 240354,
  "razorpayPaymentId": "pay_test_direct"
}
```

### Step 3 — Verify Shipment Created
```
GET http://localhost:8088/api/v1/shipments/orders/44444444-4444-4444-4444-444444444444
Authorization: Bearer {{access_token}}
```

Expected: `200 OK` with a shipment showing `status: "CREATED"` and a `trackingNumber`.

### Step 4 — Check Kafka for Published Events

In Kafka UI, check these topics:
- `order-shipped` → should have a message with `orderId` and `trackingNumber`
- `notification-triggered` → should have a message with `templateId: "order-shipped-v1"`

---

## 6. Verify Outbox Events

Connect to the database to inspect outbox behavior:

```
Host: localhost
Port: 5432
Database: shipping_db
User: ecom_admin
Password: ecom_secret_123
```

### Useful Queries

```sql
-- View all shipments
SELECT id, order_id, tracking_number, carrier, status, estimated_delivery_date, created_at
FROM shipments
ORDER BY created_at DESC;

-- Check outbox events for a shipment
SELECT id, event_type, published, created_at
FROM outbox_events
ORDER BY created_at DESC;

-- Find any unpublished outbox events (should be empty in normal operation)
SELECT id, event_type, created_at
FROM outbox_events
WHERE published = FALSE;

-- Check all events published for a specific order
SELECT oe.event_type, oe.published, oe.payload->>'orderId' as order_id
FROM outbox_events oe
WHERE oe.payload->>'orderId' = '44444444-4444-4444-4444-444444444444';
```

---

## 7. Full End-to-End Test (Complete Saga)

Run the complete flow and verify shipping is created automatically.

### Step 1 — Add to Cart
```
POST http://localhost:8084/api/v1/cart/items
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "skuId": "TEST-SKU-001",
  "quantity": 1
}
```

### Step 2 — Place Order
```
POST http://localhost:8085/api/v1/orders
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "idempotencyKey": "test-key-1001",
  "shippingAddress": {
    "fullName": "Test User",
    "line1": "123 Main St",
    "city": "Mumbai",
    "state": "Maharashtra",
    "pincode": "400001",
    "country": "India",
    "phone": "+919999999999"
  }
}
```

Save the `id` from response as `ORDER_ID`, and `razorpayOrderId` as `RAZORPAY_ORDER_ID`.

### Step 3 — Simulate Payment Captured Webhook

Get the `razorpayOrderId` from the `GET /api/v1/payments/orders/{orderId}` response on port 8086, then simulate Razorpay calling your webhook. (In dev, use Razorpay test mode or mock the signature verification.)

### Step 4 — Wait ~2 seconds, then check shipping
```
GET http://localhost:8088/api/v1/shipments/orders/{{ORDER_ID}}
Authorization: Bearer {{access_token}}
```

Expected: Shipment exists with `status: "CREATED"` and a `trackingNumber`.

### Step 5 — Check Order Status Updated to SHIPPED
```
GET http://localhost:8085/api/v1/orders/{{ORDER_ID}}
Authorization: Bearer {{access_token}}
```

Expected: `status: "SHIPPED"` (Order Service consumed the `order-shipped` event).

### Step 6 — Check Mailhog for Shipping Email
`http://localhost:8025`  
Expected: Email with subject "Your order #ORDER_ID has shipped!" containing the tracking number.

---

## 8. Metrics Verification

### Check Metrics Endpoint
```
GET http://localhost:8088/actuator/metrics
```

### Prometheus Scrape Format
```
GET http://localhost:8088/actuator/prometheus
```

Look for:
- `http_server_requests_seconds_count` — HTTP traffic
- `hikaricp_connections_active` — DB connection pool
- `jvm_memory_used_bytes` — Memory usage

---

## 9. Idempotency Test

Send the **same `payment-succeeded` event twice** to Kafka with the same `orderId`.

**Expected behavior:**
- First event: shipment created, outbox events written, Kafka messages published
- Second event: log shows `Shipment already exists for orderId=...`, no duplicate shipment

**Verify:**
```sql
SELECT COUNT(*) FROM shipments WHERE order_id = '44444444-4444-4444-4444-444444444444';
-- Should be 1, not 2
```

---

## Shipment Status Reference

| Status | Meaning |
|--------|---------|
| `CREATED` | Shipment record created, waiting for carrier pickup |
| `PICKED_UP` | Carrier has the package |
| `IN_TRANSIT` | Package is moving between hubs |
| `OUT_FOR_DELIVERY` | On the delivery vehicle today |
| `DELIVERED` | Customer received the package |
| `FAILED_DELIVERY` | Delivery attempt failed (nobody home) |
| `RETURNED` | Package sent back to warehouse |

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `404` on get by orderId | Payment not yet succeeded | Trigger `payment-succeeded` Kafka event first |
| `403` on mark-shipped | Using customer token | Get admin token from Keycloak |
| No shipment after payment event | Shipping Service not consuming | Check Service is running and Kafka topic is correct |
| `outbox_events` has `published=FALSE` rows stuck | Kafka down | Check Kafka is running: `docker ps` |
| Tracking endpoint returns `404` | Wrong tracking number | Query DB: `SELECT tracking_number FROM shipments LIMIT 5` |

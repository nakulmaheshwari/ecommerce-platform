# Notification Service — Postman Testing Guide

**Base URL:** `http://localhost:8087`  
**Port:** 8087  
**Database:** `notification_db`

> **Prerequisites:** Notification Service must be running. Mailhog must be running (check `http://localhost:8025`).

---

## 1. Health Check

### Request
```
GET http://localhost:8087/actuator/health
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

**What this verifies:** Service is up, PostgreSQL connection is healthy.

---

## 2. Trigger a Notification via Kafka (Simulate Event)

The Notification Service has no direct REST API for sending notifications — it only consumes Kafka events. The cleanest way to test it in Postman is to publish a message to Kafka UI and observe the result in Mailhog.

### Step 1 — Open Kafka UI
Go to `http://localhost:9093`  
→ Topics → `notification-triggered` → Produce Message

### Step 2 — Publish this message

**Key:** (leave blank or use any UUID)

**Value:**
```json
{
  "userId": "11111111-1111-1111-1111-111111111111",
  "channel": "EMAIL",
  "templateId": "order-confirmed-v1",
  "templateVars": {
    "orderId": "ORD-TEST-001",
    "totalRupees": "2403.54"
  },
  "recipientEmail": "test@example.com"
}
```

### Step 3 — Check Mailhog
Open `http://localhost:8025`  
You should see an email with subject: **"Your order #ORD-TEST-001 is confirmed!"**  
Click it to see the rendered HTML.

---

## 3. Trigger Shipping Notification

Same as above, but use the shipping template:

**Kafka topic:** `notification-triggered`  
**Message:**
```json
{
  "userId": "11111111-1111-1111-1111-111111111111",
  "channel": "EMAIL",
  "templateId": "order-shipped-v1",
  "templateVars": {
    "orderId": "ORD-TEST-001",
    "trackingNumber": "BD1711619000000",
    "carrier": "BlueDart",
    "estimatedDelivery": "2026-03-31"
  },
  "recipientEmail": "test@example.com"
}
```

**Expected:** Email in Mailhog with green tracking box showing the tracking number.

---

## 4. Trigger Welcome Email

**Kafka topic:** `user-registered`  
**Message:**
```json
{
  "userId": "22222222-2222-2222-2222-222222222222",
  "email": "newuser@example.com",
  "firstName": "Nakul"
}
```

**Expected:** Email in Mailhog with subject "Welcome to EcomPlatform!"

---

## 5. Test Idempotency (No Duplicate Send)

Send the **exact same message** twice to `notification-triggered` with the same `templateId` and same `orderId` in templateVars. The idempotency key is `"order-confirmed-v1:ORD-TEST-001"`.

**Expected behavior:**
- First message: email arrives in Mailhog
- Second message: no new email (log shows `Duplicate notification skipped`)

**Verify in DB:**
```sql
-- Connect to notification_db
SELECT idempotency_key, status, retry_count, sent_at 
FROM notifications 
WHERE user_id = '11111111-1111-1111-1111-111111111111'
ORDER BY created_at DESC;
```

Expected: 1 row with `status = 'SENT'`, not 2 rows.

---

## 6. Verify Metrics Endpoint

### Request
```
GET http://localhost:8087/actuator/metrics
```

### Check Specific Metric
```
GET http://localhost:8087/actuator/metrics/http.server.requests
```

### Prometheus Format (what Grafana scrapes)
```
GET http://localhost:8087/actuator/prometheus
```

Look for lines containing `http_server_requests_seconds` in the response.

---

## 7. Check DB State Directly

Connect to PostgreSQL:
```
Host: localhost
Port: 5432
Database: notification_db
User: ecom_admin
Password: ecom_secret_123
```

### Useful Queries

```sql
-- All notifications for a user
SELECT id, template_id, status, retry_count, sent_at, failure_reason
FROM notifications
WHERE user_id = '11111111-1111-1111-1111-111111111111'
ORDER BY created_at DESC;

-- All FAILED notifications waiting for retry
SELECT id, template_id, recipient, retry_count, next_retry_at, failure_reason
FROM notifications
WHERE status = 'FAILED';

-- All PENDING notifications that should be retried
SELECT id, template_id, recipient, retry_count, next_retry_at
FROM notifications
WHERE status = 'PENDING'
AND (next_retry_at IS NULL OR next_retry_at <= NOW());

-- Check all notification templates seeded
SELECT * FROM notification_templates;
```

---

## 8. End-to-End: Full Saga → Email

This is the real test — trigger the complete saga and see the emails appear automatically.

### Step 1 — Get Auth Token
```
POST http://localhost:8180/realms/ecommerce/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
&client_id=ecommerce-frontend
&username=testcustomer
&password=Test@1234
```

Save the `access_token`.

### Step 2 — Add to Cart
```
POST http://localhost:8084/api/v1/cart/items
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "skuId": "TEST-SKU-001",
  "quantity": 1
}
```

### Step 3 — Place Order
```
POST http://localhost:8085/api/v1/orders
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "idempotencyKey": "{{$randomUUID}}",
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

### Step 4 — Simulate Payment Webhook
```
POST http://localhost:8086/api/v1/webhooks/razorpay
Content-Type: application/json
X-Razorpay-Signature: {{computed_signature}}

{
  "event": "payment.captured",
  "payload": {
    "payment": {
      "entity": {
        "id": "pay_test123",
        "order_id": "{{razorpayOrderId}}",
        "amount": 240354,
        "status": "captured"
      }
    }
  }
}
```

### Step 5 — Check Mailhog
`http://localhost:8025`

You should see:
1. **Order confirmation email** (from Payment Service outbox → Notification Service)
2. **Shipping email with tracking number** (from Shipping Service outbox → Notification Service)

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| No email in Mailhog | Mailhog not running | `docker compose up -d mailhog` |
| Service won't start | `notification_db` doesn't exist | Check `docker compose up -d postgres` |
| Duplicate emails | Idempotency key mismatch | Check that templateId AND referenceId match exactly |
| Email send failing | Thymeleaf template not found | Verify `templates/email/` directory has the .html file |
| Kafka messages not consumed | Service not connected to Kafka | Check `spring.kafka.bootstrap-servers` in application.yml |

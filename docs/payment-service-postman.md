# Payment Service — Postman Testing Guide

**Base URL:** `http://localhost:8086`  
**Port:** 8086  
**Database:** PostgreSQL (`payment_db`)  
**Auth:** Initiate/Get require USER or ADMIN. Refund requires ADMIN. Webhook is public (signature-verified).

---

## How Payments Work — Architecture

Payment Service is event-driven. Normally you never call it directly — the saga does it automatically. But you need to understand the flow to test it:

```
1. Order placed → Kafka: order-placed
2. Payment Service consumes order-placed
3. Payment Service calls Razorpay API → creates Razorpay Order
4. Payment Service publishes: payment-initiated
5. Frontend uses razorpayOrderId to open Razorpay checkout UI
6. Customer pays → Razorpay calls your webhook
7. POST /api/v1/payments/webhook/razorpay (signature verified)
8. Webhook handler → updates transaction → Kafka: payment-succeeded or payment-failed
9. Downstream saga continues...
```

**Note on testing:** In test mode, skip Razorpay checkout and call the webhook directly with test data. In Razorpay test mode, no real money moves.

---

## Postman Environment

| Variable | Value |
|---|---|
| `base_url_payment` | `http://localhost:8086` |
| `access_token` | JWT (USER or ADMIN) |
| `admin_token` | JWT with ADMIN role |
| `order_id` | UUID of a placed order |
| `razorpay_order_id` | The `razorpayOrderId` from initiate response |
| `razorpay_webhook_secret` | From `application.yml` — `razorpay.webhookSecret` |

---

## 1. Get Auth Token
```
POST http://localhost:8180/realms/ecommerce/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=ecommerce-frontend&username=testcustomer&password=Test@1234
```

---

## 2. Initiate Payment

In the real saga, this is called automatically by Payment Service when it consumes `order-placed`. You can call it directly to test the Razorpay order creation.

### Request
```
POST {{base_url_payment}}/api/v1/payments/initiate
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

### Body
```json
{
  "orderId": "{{order_id}}",
  "userId": "a1b2c3d4-0000-0000-0000-000000000001",
  "amountPaise": 10619882,
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440099",
  "currency": "INR"
}
```

### Field Descriptions
| Field | Description |
|---|---|
| `orderId` | Your internal order UUID from Order Service |
| `userId` | UUID of the customer (from JWT — must match) |
| `amountPaise` | Total in paise (₹1 = 100 paise). Must match the order total exactly. |
| `idempotencyKey` | Client-generated UUID. Safe to retry the same request. |
| `currency` | Always `"INR"` for now |

### Response (200 OK)
```json
{
  "paymentId": "payment-service-internal-uuid",
  "orderId": "your-order-uuid",
  "razorpayOrderId": "order_MXW3pKFb1234",
  "razorpayKeyId": "rzp_test_XXXXXXXXXX",
  "amountPaise": 10619882,
  "currency": "INR",
  "status": "INITIATED",
  "description": "Payment for order your-order-uuid"
}
```

Save `razorpayOrderId` → `{{razorpay_order_id}}`

### What Happens Internally
1. **Idempotency check** — if `idempotencyKey` exists in DB, return existing transaction
2. **Razorpay API call** — creates Razorpay Order (`POST https://api.razorpay.com/v1/orders`)
3. **Save transaction** — status = `INITIATED`, stores `razorpayOrderId`
4. **Return** — frontend uses `razorpayOrderId` + `razorpayKeyId` to open Razorpay checkout SDK

### Error Cases
| Scenario | Response |
|----------|---------|
| No auth | `401 Unauthorized` |
| `amountPaise < 1` | `400 Bad Request` |
| Missing `orderId` | `400 Bad Request` |
| Razorpay API down | `502 Bad Gateway` |
| Same `idempotencyKey` | `200 OK` — same existing transaction returned |

### Tests Script
```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
pm.test("Has Razorpay Order ID", () => {
    const j = pm.response.json();
    pm.expect(j.razorpayOrderId).to.match(/^order_/);
    pm.expect(j.status).to.equal("INITIATED");
    pm.environment.set("razorpay_order_id", j.razorpayOrderId);
    console.log("Razorpay Order ID:", j.razorpayOrderId);
});
```

---

## 3. Get Payment by Order ID

### Request
```
GET {{base_url_payment}}/api/v1/payments/order/{{order_id}}
Authorization: Bearer {{access_token}}
```

### Response (200 OK) — Before Payment
```json
{
  "id": "payment-uuid",
  "orderId": "your-order-uuid",
  "razorpayOrderId": "order_MXW3pKFb1234",
  "razorpayPaymentId": null,
  "amountPaise": 10619882,
  "amountRupees": 106198.82,
  "currency": "INR",
  "status": "INITIATED",
  "failureCode": null,
  "failureReason": null,
  "createdAt": "2026-03-28T14:00:00Z",
  "capturedAt": null
}
```

### Response (200 OK) — After Successful Payment
```json
{
  "id": "payment-uuid",
  "orderId": "your-order-uuid",
  "razorpayOrderId": "order_MXW3pKFb1234",
  "razorpayPaymentId": "pay_MXW4qLGc5678",
  "amountPaise": 10619882,
  "amountRupees": 106198.82,
  "currency": "INR",
  "status": "CAPTURED",
  "failureCode": null,
  "failureReason": null,
  "createdAt": "2026-03-28T14:00:00Z",
  "capturedAt": "2026-03-28T14:05:23Z"
}
```

### Payment Status Values
| Status | Meaning |
|---|---|
| `INITIATED` | Razorpay order created, waiting for customer to pay |
| `CAPTURED` | Payment received and confirmed |
| `FAILED` | Payment declined/expired |
| `REFUNDED` | Admin initiated refund, money returned |

### Error Cases
| Scenario | Response |
|----------|---------|
| No payment for this order | `404 Not Found` |
| Wrong user's order | `403 Forbidden` _(depends on implementation)_ |
| No auth | `401 Unauthorized` |

### Tests Script
```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
pm.test("Contains payment fields", () => {
    const j = pm.response.json();
    pm.expect(j).to.have.property("razorpayOrderId");
    pm.expect(j).to.have.property("status");
    pm.expect(["INITIATED","CAPTURED","FAILED","REFUNDED"]).to.include(j.status);
    console.log("Payment status:", j.status);
});
```

---

## 4. Razorpay Webhook — Simulate Payment Captured

This is the most important endpoint to test. In production, Razorpay calls this. In dev, you call it.

**⚠️ Important:** The endpoint verifies the `X-Razorpay-Signature` header using HMAC-SHA256.

### How to Generate a Valid Test Signature

In Razorpay test mode, compute:
```
HMAC-SHA256(webhookBody, razorpay.webhookSecret)
```

From `application.yml` in payment-service:
```yaml
razorpay:
  webhookSecret: your_webhook_secret_here
```

**Using curl to compute (bash):**
```bash
BODY='{"event":"payment.captured","id":"evt_TEST001","payload":{"payment":{"entity":{"id":"pay_TEST001","order_id":"order_MXW3pKFb1234","amount":10619882,"status":"captured"}}}}'
SECRET="your_webhook_secret"
echo -n "$BODY" | openssl dgst -sha256 -hmac "$SECRET"
```

**Using Node.js:**
```javascript
const crypto = require('crypto');
const body = JSON.stringify({...});
const sig = crypto.createHmac('sha256', 'your_webhook_secret').update(body).digest('hex');
console.log(sig);
```

### Request — Payment Captured
```
POST {{base_url_payment}}/api/v1/payments/webhook/razorpay
Content-Type: application/json
X-Razorpay-Signature: {{computed_signature}}
```

### Body — `payment.captured`
```json
{
  "event": "payment.captured",
  "id": "evt_TEST001",
  "payload": {
    "payment": {
      "entity": {
        "id": "pay_TEST001",
        "order_id": "{{razorpay_order_id}}",
        "amount": 10619882,
        "currency": "INR",
        "status": "captured"
      }
    }
  }
}
```

### Response (200 OK — Success)
Empty body. HTTP 200 tells Razorpay the webhook was received and processed.

### Response (401 — Invalid Signature)
```json
{}
```
Empty body with status 401. This happens when the HMAC signature doesn't match.

### What Happens After Successful Webhook
1. Signature verified ✓
2. Duplicate check — `razorpayEventId` stored to prevent reprocessing
3. Transaction `status` → `CAPTURED`, `capturedAt` = NOW
4. Outbox event written: `payment.succeeded`
5. Outbox poller publishes `payment-succeeded` to Kafka
6. Order Service consumes → order status `AWAITING_PAYMENT → CONFIRMED`
7. Inventory Service consumes → reservation `HELD → CONFIRMED`
8. Shipping Service consumes → shipment created
9. Notification Service → confirmation email sent

### Body — `payment.failed`
```json
{
  "event": "payment.failed",
  "id": "evt_FAIL001",
  "payload": {
    "payment": {
      "entity": {
        "id": "pay_FAIL001",
        "order_id": "{{razorpay_order_id}}",
        "amount": 10619882,
        "currency": "INR",
        "status": "failed",
        "error": {
          "code": "BAD_REQUEST_ERROR",
          "description": "Your payment was declined by the bank",
          "reason": "payment_failed"
        }
      }
    }
  }
}
```

**After failed webhook:**
- Transaction `status` → `FAILED`, `failureCode` and `failureReason` populated
- Kafka: `payment-failed`
- Order Service: order `AWAITING_PAYMENT → CANCELLED`
- Inventory Service: reservation released, stock returned to `available_qty`

### Tests Script
```javascript
pm.test("Webhook accepted (200)", () => pm.response.to.have.status(200));
```

---

## 5. Dev Shortcut — Skip Signature Verification

In development/testing, if you find signature computation cumbersome, you can temporarily disable signature verification in `RazorpayGateway.java`:

```java
public boolean verifyWebhookSignature(String body, String signature) {
    return true; // TODO: remove before prod
}
```

This lets you post any JSON to the webhook without a valid header. **Never do this in production.**

---

## 6. Refund Order (Admin Only)

Initiates a full or partial refund via Razorpay.

### Request
```
POST {{base_url_payment}}/api/v1/payments/refund/{{order_id}}
Authorization: Bearer {{admin_token}}
Content-Type: application/json
```

### Body — Full Refund
```json
{
  "amountPaise": 10619882,
  "reason": "Customer requested cancellation — order not yet shipped"
}
```

### Body — Partial Refund
```json
{
  "amountPaise": 5000000,
  "reason": "Partial refund for damaged item SKU-LAPTOP-PRO-16-BLK"
}
```

### Response (200 OK)
```json
{
  "id": "payment-uuid",
  "orderId": "your-order-uuid",
  "razorpayOrderId": "order_MXW3pKFb1234",
  "razorpayPaymentId": "pay_TEST001",
  "amountPaise": 10619882,
  "amountRupees": 106198.82,
  "currency": "INR",
  "status": "REFUNDED",
  "capturedAt": "2026-03-28T14:05:23Z"
}
```

### Error Cases
| Scenario | Response |
|----------|---------|
| Payment not CAPTURED | `400 Bad Request` — can't refund an INITIATED or FAILED transaction |
| `amountPaise > original` | `400 Bad Request` — cannot refund more than charged |
| Customer token | `403 Forbidden` |
| Order not found | `404 Not Found` |

---

## 7. DB State Inspection

```
Host: localhost  Port: 5432  DB: payment_db
User: ecom_admin  Password: ecom_secret_123
```

```sql
-- All payment transactions
SELECT id, order_id, razorpay_order_id, razorpay_payment_id,
       amount_paise/100.0 as amount_rupees, status,
       failure_code, failure_reason, created_at, captured_at
FROM payment_transactions
ORDER BY created_at DESC
LIMIT 10;

-- Payment for a specific order
SELECT * FROM payment_transactions WHERE order_id = 'your-order-uuid';

-- All webhook events received (including failed ones)
SELECT razorpay_event_id, event_type, signature_valid, source_ip,
       processed, processed_at, created_at
FROM webhook_events
ORDER BY created_at DESC
LIMIT 20;

-- Duplicate webhook attempts (should be rare)
SELECT razorpay_event_id, COUNT(*) as attempts
FROM webhook_events
GROUP BY razorpay_event_id
HAVING COUNT(*) > 1;

-- Invalid signature attempts (potential attacks)
SELECT razorpay_event_id, source_ip, created_at
FROM webhook_events
WHERE signature_valid = FALSE
ORDER BY created_at DESC;

-- Outbox events (should all be published)
SELECT event_type, published, aggregate_id, created_at
FROM outbox_events
ORDER BY created_at DESC LIMIT 10;

-- Refund records
SELECT id, payment_transaction_id, razorpay_refund_id,
       amount_paise/100.0 as amount_rupees, reason, status
FROM refunds
ORDER BY created_at DESC;
```

---

## 8. Full Payment Test Sequence

```
Step 1: Get auth token
Step 2: Add item to cart (Cart Service port 8084)
Step 3: Place order (Order Service port 8085) → save ORDER_ID
Step 4: Check order status = AWAITING_PAYMENT after ~2s
Step 5: GET /api/v1/payments/order/{{ORDER_ID}} → save razorpay_order_id
Step 6: Compute webhook signature OR disable verification for dev
Step 7: POST /api/v1/payments/webhook/razorpay with payment.captured body
Step 8: GET /api/v1/payments/order/{{ORDER_ID}} → status = CAPTURED
Step 9: GET http://localhost:8085/api/v1/orders/{{ORDER_ID}} → status = CONFIRMED
Step 10: GET http://localhost:8088/api/v1/shipments/orders/{{ORDER_ID}} → shipment created
Step 11: Check Mailhog http://localhost:8025 → confirmation email
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `401` on webhook | Invalid signature | Compute HMAC-SHA256 correctly or disable for dev |
| Webhook returns 200 but order not updated | Outbox not published | Check `outbox_events` table — any `published=FALSE`? |
| `400` on initiate | Missing `amountPaise` or `orderId` | All 4 fields are `@NotNull` |
| `404` on get payment | Order placed but payment not initiated | Payment service may not have consumed `order-placed` yet |
| Duplicate webhook ignored | Same `id` seen twice | Expected behavior — idempotency working correctly |
| Refund `400` | Transaction not `CAPTURED` | Check payment status first |

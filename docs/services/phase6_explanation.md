# Phase 6: Notifications, Shipping & Observability — Deep Dive

## The Big Picture

Before Phase 6, the saga ended at Payment. Money moved, but nothing happened after that:

- The customer received no email
- No shipment was created
- No traces connected the services together
- You had no visibility into what was happening in production

Phase 6 closes all of those gaps. After this phase:

```
Customer clicks "Pay"
    ↓
Razorpay captures payment
    ↓
Kafka: payment-succeeded
    ↓
    ├── Order Service: status = CONFIRMED
    ├── Inventory Service: confirms reservation
    ├── Shipping Service: creates shipment record → Kafka: order-shipped
    │       ↓
    │   Order Service: status = SHIPPED
    │
    └── Notification Service: sends order confirmation email (Mailhog)
            ↓
        Shipping Service outbox → Kafka: notification-triggered
            ↓
        Notification Service: sends shipping email with tracking number

ALL of this is visible as a single trace in Zipkin.
ALL metrics are visible in Grafana.
```

---

## Service 1: Notification Service

### Why Is This Hard?

The naive approach is: consume Kafka event → send email → done.  
The production approach requires solving 5 problems:

**Problem 1: Duplicate sends**  
Kafka guarantees *at-least-once* delivery. The same `payment-succeeded` event might arrive twice if a pod restarts or Kafka rebalances. Without protection, the customer gets two "Your payment was received" emails. That destroys trust.

**Solution:** Every notification has an `idempotency_key` column with a UNIQUE constraint. The key is `"{templateId}:{referenceId}"`. Before sending anything, we check:
```java
if (notificationRepository.existsByIdempotencyKey(idempotencyKey)) {
    log.warn("Duplicate notification skipped key={}", idempotencyKey);
    return;  // No send, no error
}
```
The DB enforces this at the persistence level too — even if two threads race, one INSERT will fail with a constraint violation.

**Problem 2: Provider outages**  
SendGrid goes down. Mailhog has a hiccup. The email send fails. Without retry logic, the customer never gets the email.

**Solution:** We persist the notification record with `status=PENDING` BEFORE attempting the send. If it fails:
```java
public void markFailed(String reason) {
    this.retryCount++;
    this.failureReason = reason;
    
    if (this.retryCount >= this.maxRetries) {
        this.status = NotificationStatus.FAILED;  // Give up after 3 tries
    } else {
        this.status = NotificationStatus.PENDING;
        long backoffMinutes = (long) Math.pow(this.retryCount, 2); // 1, 4, 9 minutes
        this.nextRetryAt = Instant.now().plusSeconds(backoffMinutes * 60);
    }
}
```
A `@Scheduled(fixedDelay = 120_000)` method runs every 2 minutes:
```java
List<Notification> due = notificationRepository.findDueForProcessing(Instant.now());
due.forEach(this::sendNotification);
```
This picks up anything with `status='PENDING' AND nextRetryAt <= NOW`.

**Problem 3: Template management**  
Hardcoding `<html><body>Your order {{orderId}} is confirmed...` in Java is unmaintainable. Every text change requires a code deployment.

**Solution:** Thymeleaf templates are HTML files stored in `resources/templates/email/`. They are rendered at send time:
```java
Context context = new Context();
variables.forEach(context::setVariable);  // inject orderId, totalRupees, etc.
String htmlContent = templateEngine.process("email/" + templateId, context);
```
Template names come from the event payload (`templateId: "order-confirmed-v1"`). The notification service doesn't need to know what variables an order confirmation needs — the producer (Payment Service outbox) puts them in the event.

**Problem 4: Audit trail**  
A customer calls support: "I never got my confirmation email". Without persistence, you have no way to verify.

**Solution:** Every notification attempt is persisted in the `notifications` table with `payload_snapshot` (the exact variables used), `sent_at`, `status`, and `failure_reason`. Support can look up by `user_id` or `idempotency_key`.

**Problem 5: Fan-in from many services**  
Every service that needs to send a notification should not have its own email-sending logic. They should just publish an event.

**Solution:** One `KafkaListener` on the `notification-triggered` topic handles ALL notification events from ALL services. The event schema is standardized:
```json
{
  "userId":        "uuid",
  "channel":       "EMAIL",
  "templateId":    "order-confirmed-v1",
  "templateVars":  { "orderId": "...", "totalRupees": "..." },
  "recipientEmail": "user@example.com"
}
```
A separate listener handles `user-registered` (published by Identity Service) because that topic has a different schema.

---

### Database Schema

```sql
CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,  -- "order-confirmed-v1:ord-123"
    user_id         UUID NOT NULL,
    channel         VARCHAR(20) NOT NULL,           -- EMAIL / SMS / PUSH
    template_id     VARCHAR(100) NOT NULL,
    recipient       VARCHAR(255) NOT NULL,          -- email address or phone
    subject         VARCHAR(500),
    payload_snapshot JSONB,                         -- variables that were used
    status          VARCHAR(20) DEFAULT 'PENDING',  -- PENDING/SENT/FAILED/SKIPPED
    failure_reason  TEXT,
    retry_count     INTEGER DEFAULT 0,
    max_retries     INTEGER DEFAULT 3,
    next_retry_at   TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ
);
```

The `idx_notifications_idempotency` UNIQUE index ensures that even at the DB level, you cannot insert two notifications for the same key. The partial index `WHERE status = 'PENDING'` on [(status, next_retry_at)](file:///Users/nakul/Desktop/ecom/ecommerce-platform/services/product-catalog-service/src/main/java/com/ecommerce/catalog/config/DataLoader.java#26-282) makes the retry query fast — it only scans PENDING rows.

---

### The Email Flow in Detail

```
1. Kafka event arrives at NotificationEventConsumer
        ↓
2. Consumer parses JSON, extracts userId, channel, templateId, templateVars, recipient
        ↓
3. NotificationService.processNotification() is called
        ↓
4. idempotencyKey = templateId + ":" + referenceId
   Check DB: existsByIdempotencyKey → if true, return (DUPLICATE)
        ↓
5. Build Notification entity, status=PENDING
   Save to DB (BEFORE sending — ensures we can retry if we crash)
        ↓
6. EmailSender.send() called:
   a. Thymeleaf renders HTML: process("email/order-confirmed-v1", context)
   b. MimeMessage built with subject, HTML content, from address
   c. mailSender.send(message) → SMTP to Mailhog (port 1025)
        ↓
7. On success: notification.markSent() → status=SENT, sentAt=NOW
   On failure: notification.markFailed(reason) → status=PENDING, nextRetryAt computed
        ↓
8. Save updated notification to DB
        ↓
9. ack.acknowledge() → commit Kafka offset (only on success)
   If exception: DON'T ack → Kafka will redeliver
```

---

### Why Mailhog?

Mailhog is a fake SMTP server. It accepts all emails without sending them anywhere. You can see every email in its web UI at `http://localhost:8025`. This is how you verify email content during development without needing a real email account.

In production, you just change the mail config:
```yaml
# Dev (Mailhog)
spring.mail.host: localhost
spring.mail.port: 1025

# Prod (SendGrid SMTP Relay)
spring.mail.host: smtp.sendgrid.net
spring.mail.port: 587
spring.mail.username: apikey
spring.mail.password: SG.your-sendgrid-api-key
```
The Java code doesn't change at all.

---

## Service 2: Shipping Service

### Why a Separate Service?

Shipping is not part of the Order Service because:
1. It has its own lifecycle (carrier booking, pickup, in-transit, delivered)
2. It calls external APIs (BlueDart, Delhivery) that shouldn't be in Order Service
3. Order Service should be fast; carrier API calls add latency
4. Shipping can be scaled independently during peak seasons

### The Trigger: Only After Payment

```java
@KafkaListener(topics = "payment-succeeded", ...)
public void handlePaymentSucceeded(...) {
    // Parse orderId, userId from event
    shippingService.createShipment(orderId, userId, address);
}
```

We listen to `payment-succeeded`, NOT `order-placed`. This is critical. You never ship before payment is confirmed — that would be a business disaster. The dependency chain is:

```
order-placed → payment-succeeded → shipment-created → order-shipped
```

### createShipment() Logic

```java
@Transactional
public Shipment createShipment(UUID orderId, UUID userId, Map shippingAddress) {
    
    // 1. Idempotency check — if Kafka delivers twice, don't create two shipments
    if (shipmentRepository.existsByOrderId(orderId)) {
        return shipmentRepository.findByOrderId(orderId).orElseThrow();
    }
    
    // 2. Generate tracking number (real: call carrier API)
    String trackingNumber = "BD" + System.currentTimeMillis();
    LocalDate estimatedDelivery = LocalDate.now().plusDays(3);
    
    // 3. Save Shipment
    Shipment shipment = Shipment.builder()
        .orderId(orderId)
        .userId(userId)
        .trackingNumber(trackingNumber)
        .carrier("BlueDart")
        .status("CREATED")
        .shippingAddress(shippingAddress)
        .estimatedDeliveryDate(estimatedDelivery)
        .build();
    shipmentRepository.save(shipment);
    
    // 4. Write TWO outbox events in the SAME TRANSACTION
    //    Event 1: order.shipped → Order Service updates order status to SHIPPED
    outboxRepository.save(OutboxEvent.builder()
        .eventType("order.shipped")
        .payload(Map.of("orderId", orderId.toString(),
                        "trackingNumber", trackingNumber, ...))
        .build());
    
    //    Event 2: notification.triggered → Notification Service sends email
    outboxRepository.save(OutboxEvent.builder()
        .eventType("notification.triggered")
        .payload(Map.of("userId", userId.toString(),
                        "templateId", "order-shipped-v1",
                        "templateVars", Map.of("trackingNumber", trackingNumber, ...)))
        .build());
}
```

The key insight: **both outbox events and the shipment record are written in the same database transaction**. This is the Transactional Outbox Pattern. Either all three records are committed together, or none are. There is no state where the shipment exists but the events don't.

### The Outbox Poller

Every 500ms, [OutboxPoller](file:///Users/nakul/Desktop/ecom/ecommerce-platform/services/inventory-service/src/main/java/com/ecommerce/inventory/event/producer/OutboxPoller.java#16-52) checks for unpublished events and sends them to Kafka:

```java
@Scheduled(fixedDelay = 500)
@Transactional
public void pollAndPublish() {
    var events = outboxRepository.findUnpublished(50);
    for (OutboxEvent event : events) {
        String topic = TOPIC_MAP.get(event.getEventType());
        kafkaTemplate.send(topic, aggregateId, payload).get(5, TimeUnit.SECONDS);
        event.setPublished(true);
        outboxRepository.save(event);
    }
}
```

Topic mapping:
- `order.shipped` → Kafka topic `order-shipped` → consumed by Order Service
- `notification.triggered` → Kafka topic `notification-triggered` → consumed by Notification Service

**The `.get(5, TimeUnit.SECONDS)` call is intentional.** It makes the Kafka send synchronous, ensuring that the `setPublished(true)` only happens if Kafka actually confirmed receipt. If Kafka is down, the event stays unpublished and will be retried on the next poll.

### Shipment Status Machine

```
CREATED
  ↓ (carrier picked up)
PICKED_UP
  ↓ (in transit)
IN_TRANSIT
  ↓ (out for delivery today)
OUT_FOR_DELIVERY
  ↓ (customer received it)
DELIVERED

Alternative paths:
IN_TRANSIT → FAILED_DELIVERY (nobody home)
FAILED_DELIVERY → RETURNED (back to warehouse)
```

The `mark-shipped` admin endpoint moves a shipment to `IN_TRANSIT` — useful for manually triggering status updates in development.

---

## Part 3: Observability Stack

### The Four Golden Signals

Google's SRE book defines four metrics that tell you everything about a production system's health:

| Signal | What It Tells You | Our Alert |
|--------|-------------------|-----------|
| **Latency** | How long requests take | Order p99 > 500ms for 5 min |
| **Traffic** | How many requests per second | Baseline + 3σ anomaly |
| **Errors** | What fraction of requests fail | Any service > 5% errors for 2 min |
| **Saturation** | How "full" the system is | Heap > 85%, DB pool > 80%, Kafka lag > 1000 |

### Prometheus

Prometheus is a time-series database. It works by pulling (*scraping*) metrics from your services every 15 seconds.

Every Spring Boot service exposes metrics at `/actuator/prometheus` automatically when you have `micrometer-registry-prometheus` on the classpath. The output looks like:

```
# HELP http_server_requests_seconds_count  
# TYPE http_server_requests_seconds_count counter
http_server_requests_seconds_count{exception="None",method="POST",
    outcome="SUCCESS",status="200",uri="/api/v1/orders"} 42.0

# HELP jvm_memory_used_bytes
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 1.28974848E8
```

Prometheus stores these time series and lets you query them with PromQL:

```promql
# Orders per second
rate(ecom_orders_created_total[5m])

# p99 order placement latency
histogram_quantile(0.99, ecom_order_placement_duration_seconds_bucket)

# Payment error rate
rate(http_server_requests_seconds_count{job="payment-service",status=~"5.."}[5m])
/ rate(http_server_requests_seconds_count{job="payment-service"}[5m])
```

### Grafana

Grafana connects to Prometheus (and Loki, and Zipkin) and lets you build dashboards. We auto-provision the datasources via [datasources.yml](file:///Users/nakul/Desktop/ecom/ecommerce-platform/infrastructure/observability/grafana/provisioning/datasources/datasources.yml) so you don't need to configure anything manually — the connections are ready when Grafana starts.

Go to `http://localhost:3000` (admin/admin123), then:
- **Explore → Prometheus**: run ad-hoc PromQL queries
- **Explore → Loki**: search logs by service name, traceId, log level
- **Explore → Zipkin**: search distributed traces

### Alert Rules

The alert rules in [alert-rules.yml](file:///Users/nakul/Desktop/ecom/ecommerce-platform/infrastructure/observability/alert-rules.yml) are evaluated every 30 seconds. When a rule fires, Prometheus marks the alert as `PENDING`. After the `for:` duration (e.g., `for: 5m`), it becomes `FIRING`. In production, you'd connect Alertmanager to send PagerDuty, Slack, or email alerts.

The most important alert we added:

```yaml
- alert: StaleOutboxEvents
  expr: time() - max(outbox_last_published_at) by (job) > 300
  for: 2m
  labels:
    severity: critical
  annotations:
    summary: "{{ $labels.job }} has unpublished outbox events > 5 minutes old"
```

This fires if the outbox poller stops publishing events for 5+ minutes. In a financial system, this means orders are stuck and customers aren't getting confirmations. It's your most important infrastructure alert.

### Loki + Promtail

Loki is a log aggregation system (like Elasticsearch but cheaper). Promtail ships logs from the host's `/var/log` to Loki. In Grafana Explore with Loki datasource, you can run:

```
{job="varlogs"} |= "ERROR"
{job="varlogs"} |= "traceId=abc123"
```

### MdcTraceFilter

The `MdcTraceFilter` in `common-security` is the bridge between HTTP requests and log searchability:

```java
MDC.put("traceId", traceId);  // Extracted from traceparent header
```

After this, every `log.info(...)` in the same thread automatically includes `traceId=...` in the output (when `%X{traceId}` is in your logback pattern). You then search Loki for `traceId=abc123` and see every log line across every service that handled that request.

### Custom Business Metrics

Spring Boot auto-exports JVM, HTTP, and HikariCP metrics. But it can't tell you about YOUR business:

```java
@Bean
public Counter ordersCreatedCounter(MeterRegistry registry) {
    return Counter.builder("ecom.orders.created")
        .description("Total orders successfully created")
        .register(registry);
}
```

Inject this counter in `OrderService` and call `ordersCreatedCounter.increment()` every time an order is placed. In Grafana:

```promql
rate(ecom_orders_created_total[5m])  
# = orders per second in real-time — your live sales dashboard
```

---

## How All Three Parts Connect

```
REQUEST                    LOG                    METRIC                 TRACE
────────                   ───────                ──────                 ──────
POST /orders               [traceId=abc123]       ecom.orders.created++  Zipkin: span
  ↓                        OrderService: order    http_requests_total++  started
Order created                created orderId=X
  ↓
Kafka: order-placed         [traceId=abc123]
                            Payment: consuming
  ↓
Kafka: payment-succeeded    [traceId=abc123]       (new spans in         Zipkin: spans
  ↓                         Shipping: shipment     Shipping +            across services
Shipment created            created                Notification)
  ↓
Kafka: order-shipped
  ↓
Kafka: notification-trig    [traceId=abc123]
  ↓                         Notification: email
Email sent (Mailhog)        sent to user@...

Search Loki for traceId=abc123 → see ALL log lines above
Check Zipkin for traceId=abc123 → see full timing of every service
Check Grafana for ecom.orders.created → see sales rate over time
```

This is what production-grade observability looks like.

# Ecommerce Platform — Complete Interservice Communication Architecture

> **Audience**: Engineers who want to understand every service, every Kafka event, every REST call, and every saga in this platform.
> **Last Updated**: 2026-03-28

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Service Responsibilities](#2-service-responsibilities)
3. [Complete Interservice Communication Map](#3-complete-interservice-communication-map)
4. [Synchronous Communication (REST / Feign)](#4-synchronous-communication)
5. [Asynchronous Communication (Kafka)](#5-asynchronous-communication)
6. [Saga Implementations](#6-saga-implementations)
7. [Outbox Pattern](#7-outbox-pattern)
8. [Eventual Consistency](#8-eventual-consistency)
9. [Data Ownership](#9-data-ownership)
10. [Complete User Journey Flows](#10-complete-user-journey-flows)
11. [Failure Scenarios](#11-failure-scenarios)
12. [Performance and Scalability](#12-performance-and-scalability)
13. [Observability](#13-observability)
14. [Final Architecture Summary](#14-final-architecture-summary)

---

## 1. System Overview

### High-Level Architecture

```
                           ┌──────────────────────────────────┐
                           │          External Clients         │
                           │   (Mobile App / Browser / B2B)    │
                           └─────────────┬────────────────────┘
                                         │ HTTPS
                           ┌─────────────▼────────────────────┐
                           │           API Gateway             │
                           │         (Port 8080)               │
                           │   Spring Cloud Gateway            │
                           │   JWT Validation of Routes        │
                           │   Rate Limiting / Load Balancing  │
                           └──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬───┘
                              │  │  │  │  │  │  │  │  │  │
        ┌─────────────────────┘  │  │  │  │  │  │  │  │  └──────────────┐
        │      ┌─────────────────┘  │  │  │  │  │  │  └────────────┐    │
        │      │        ┌───────────┘  │  │  │  │  └──────────┐    │    │
        │      │        │              │  │  │  └────────┐     │    │    │
        ▼      ▼        ▼              ▼  │  ▼           ▼     ▼    ▼    ▼
  Identity  User    Product          Cart │  Order    Payment Ship  Not  Search
  Service  Service  Catalog        (8084) │  Service  Service Svc   Svc  Service
  (8081)  (8087)   (8082)               │  (8085)   (8086)  (8088)(8089)(8090)
                                        ▼
                                  Inventory
                                   (8083)

  ┌─────────────────────────────────────────────────────────────────┐
  │                     Infrastructure Layer                         │
  │                                                                  │
  │  Eureka        Config Server    Kafka Broker    PostgreSQL x9    │
  │  (8761)          (8888)          (9092)         (per service)   │
  │                                                                  │
  │  Redis          Keycloak          Zipkin          Prometheus     │
  │  (6379)         (8180)           (9411)           (9090)        │
  │                                                                  │
  │  Grafana         Loki           Mailhog                         │
  │  (3000)         (3100)          (1025)                          │
  └─────────────────────────────────────────────────────────────────┘
```

### All Microservices

| Service | Port | Database | Responsibility |
|---------|------|----------|----------------|
| **API Gateway** | 8080 | None | Route externl traffic, JWT validation, rate limiting |
| **Identity Service** | 8081 | `identity_db` | User registration, Keycloak auth, JWT tokens |
| **Product Catalog Service** | 8082 | `catalog_db` | Products, categories, variants, slugs |
| **Inventory Service** | 8083 | `inventory_db` | Stock levels, reservations, confirmations |
| **Cart Service** | 8084 | Redis | Session cart with product enrichment |
| **Order Service** | 8085 | `order_db` | Order creation, state machine, saga orchestration |
| **Payment Service** | 8086 | `payment_db` | Razorpay integration, transaction audit, idempotency |
| **User Service** | 8087 | `user_db` | Profiles, address book, preferences |
| **Shipping Service** | 8088 | `shipping_db` | Shipment creation, tracking numbers, carrier integration |
| **Notification Service** | 8089 | `notification_db` | Fan-in email/SMS notifications, template rendering |
| **Search Service** | 8090 | Elasticsearch | Full text product search |
| **Recommendation Service** | — | — | Product recommendations (ML-based) |

### External Infrastructure

| Component | Port | Purpose |
|-----------|------|---------|
| **Keycloak** | 8180 | OpenID Connect / OAuth2 authority. Issues JWT tokens. All services validate against it. |
| **Kafka** | 9092 | Async event bus for all inter-service events. Single broker in dev. |
| **PostgreSQL** | 5432 | Each service owns its own logical database (database-per-service pattern). |
| **Redis** | 6379 | Cart persistence. Session-scoped data. O(1) reads. |
| **Zipkin** | 9411 | Distributed trace collection. Services ship spans via Micrometer Brave. |
| **Prometheus** | 9090 | Metric scraping from `/actuator/prometheus` on all services. |
| **Grafana** | 3000 | Dashboards over Prometheus metrics. |
| **Loki** | 3100 | Log aggregation. Correlated with traces via `traceId` MDC field. |
| **Mailhog** | 1025, 8025 | Local SMTP server. Captures outbound emails for testing. |
| **Eureka** | 8761 | Service registry. All services register here. Feign clients use it for discovery. |
| **Config Server** | 8888 | Centralized configuration. Each service has a `[service-name].yml` in `config-repo`. |

---

## 2. Service Responsibilities

### 2.1 Identity Service (port 8081)

**Domain**: Authentication & Authorization

This service is the sole source of truth for *who a user is*. It owns the relationship between the application's user model and Keycloak. No other service manages passwords or tokens.

**Main Entities**:
- `AppUser` — mirrors a Keycloak user with `keycloakId`, `email`, `role`, `status`
- `OutboxEvent` — pending Kafka events (outbox pattern)

**Key DB Tables**: `app_users`, `outbox_events`

**REST Endpoints**:

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/auth/register` | Public | Creates Keycloak user + AppUser row |
| `POST` | `/api/v1/auth/login` | Public | Returns JWT from Keycloak |
| `GET` | `/api/v1/auth/me` | JWT | Returns current user info |
| `GET` | `/api/v1/auth/validate` | Internal | Token validation for Gateway |
| `GET` | `/api/v1/admin/users` | ADMIN | List all users |
| `PUT` | `/api/v1/admin/users/{id}/status` | ADMIN | Activate/deactivate users |
| `POST` | `/api/v1/admin/users/{id}/roles` | ADMIN | Assign roles |

**Kafka Topics Produced (via Outbox)**:

| Topic | Trigger |
|-------|---------|
| `user-registered` | Every new user registration |

---

### 2.2 User Service (port 8087)

**Domain**: Profile Management

Answers the question "what do we know about this user?" — completely separate from "who are they?" which is Identity Service's job. Profile data changes frequently (address updates at checkout, preference changes). Identity data rarely changes.

**Main Entities**:
- `UserProfile` — `keycloakId` (UUID, FK to Identity), `email`, `firstName`, `lastName`, `preferences` (JSONB)
- `Address` — `line1`, `city`, `state`, `pincode`, soft-delete, `isDefault` invariant
- `OutboxEvent` — outbox pattern

**Key DB Tables**: `user_profiles`, `addresses`, `outbox_events`

**REST Endpoints**:

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/users/me` | CUSTOMER | Get own profile |
| `PUT` | `/api/v1/users/me` | CUSTOMER | Update profile |
| `GET` | `/api/v1/users/me/addresses` | CUSTOMER | List addresses |
| `POST` | `/api/v1/users/me/addresses` | CUSTOMER | Add address (max 10) |
| `PUT` | `/api/v1/users/me/addresses/{id}` | CUSTOMER | Update address |
| `DELETE` | `/api/v1/users/me/addresses/{id}` | CUSTOMER | Soft-delete address |
| `PATCH` | `/api/v1/users/me/addresses/{id}/default` | CUSTOMER | Set default address |
| `GET` | `/api/v1/internal/users/{userId}/default-address` | Internal | Fetch default address for Order Service |
| `GET` | `/api/v1/admin/users/{userId}` | ADMIN | Admin lookup |

**Kafka Consumers**:

| Topic | Group | Action |
|-------|-------|--------|
| `user-registered` | `user-service-group` | Auto-create profile from Identity event |

---

### 2.3 Product Catalog Service (port 8082)

**Domain**: Product Information Management

The merchandise catalog. Manages product content (descriptions, images, pricing, slugs). Standalone entity — does not know about stock (that's Inventory).

**Main Entities**:
- `Product` — name, `slug`, `basePrice`, `status` (DRAFT/PUBLISHED/ARCHIVED)
- `ProductVariant` — SKU, price override, attributes (size, color)
- `Category` — tree structure with `parentId`
- `OutboxEvent`

**REST Endpoints**:

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/products/{id}` | Public | Product detail |
| `GET` | `/api/v1/products/slug/{slug}` | Public | Lookup by URL slug |
| `GET` | `/api/v1/products/{id}/related` | Public | Related products |
| `GET` | `/api/v1/products/{id}/variants` | Public | All SKUs for a product |
| `GET` | `/api/v1/categories` | Public | Category tree |
| `GET` | `/api/v1/categories/{id}/products` | Public | Paginated products by category |
| `POST` | `/api/v1/admin/products` | ADMIN | Create product (starts DRAFT) |
| `POST` | `/api/v1/admin/products/{id}/publish` | ADMIN | Publish (emits `product-published`) |

**Kafka Topics Produced (via Outbox)**:

| Topic | Trigger |
|-------|---------|
| `product-published` | Admin publishes a product (Search/Recommendation index update) |

---

### 2.4 Inventory Service (port 8083)

**Domain**: Stock Management

Manages stock levels and reservations. The critical constraint is: **stock is reserved at order placement and confirmed or released based on payment outcome**. This prevents overselling.

**Main Entities**:
- `Inventory` — `skuId`, `quantity`, `reservedQuantity`, `lowStockThreshold`
- `InventoryReservation` — per-order reservation records, `RESERVED` / `CONFIRMED` / `RELEASED`
- `OutboxEvent`

**Key DB Tables**: `inventory`, `inventory_reservations`, `outbox_events`

**REST Endpoints**:

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/inventory/availability` | Internal | Batch availability check (used by Order Service via Feign) |
| `GET` | `/api/v1/inventory/{skuId}` | Internal | Single SKU stock info |
| `POST` | `/api/v1/inventory/{skuId}/add-stock` | ADMIN | Replenish stock |

**Kafka Consumers**:

| Topic | Group | Action |
|-------|-------|--------|
| `order-placed` | `inventory-service-group` | `reserveStock()` — deducts reservation |
| `payment-succeeded` | `inventory-service-group` | `confirmOrderReservations()` — final deduct |
| `payment-failed` | `inventory-service-group` | `releaseOrderReservations()` — returns stock |

---

### 2.5 Cart Service (port 8084)

**Domain**: Shopping Cart Management

Transient, session-scoped shopping carts stored in Redis. Manages the pre-order state: item selection, quantity, enriched product data.

**Main Entities** (Redis):
- `Cart` — `userId`, list of `CartItem` (skuId, productName, price, quantity, imageUrl)

**REST Endpoints**:

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/cart` | CUSTOMER | Get current cart |
| `POST` | `/api/v1/cart/items` | CUSTOMER | Add/update item |
| `PUT` | `/api/v1/cart/items/{skuId}` | CUSTOMER | Update quantity |
| `DELETE` | `/api/v1/cart/items/{skuId}` | CUSTOMER | Remove item |
| `DELETE` | `/api/v1/cart` | CUSTOMER | Clear cart |

**Feign Clients (Synchronous REST)**:
- **ProductCatalogClient** → `product-catalog-service` — enriches cart items with real-time product data (names, prices, status) to prevent stale data.

---

### 2.6 Order Service (port 8085)

**Domain**: Order Lifecycle Management

The saga orchestrator. Manages the state machine for an order from placement to completion. The most complex service in the platform.

**Order States**: `PLACED` → `PAYMENT_PENDING` → `CONFIRMED` / `PAYMENT_FAILED` → `CANCELLED` → `SHIPPED` → `DELIVERED`

**Main Entities**:
- `Order` — `userId`, `status`, `totalPaise`, `items` (JSON), `paymentId`
- `OrderItem` — `skuId`, `productId`, `quantity`, `unitPricePaise`
- `OutboxEvent`

**REST Endpoints**:

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/orders` | CUSTOMER | Checkout — creates order, initiates saga |
| `GET` | `/api/v1/orders/{orderId}` | CUSTOMER | Order detail |
| `GET` | `/api/v1/orders` | CUSTOMER | My orders paginated |

**Feign Clients (Synchronous REST)**:
- **CartServiceClient** → `cart-service` — fetches and clears cart contents at checkout
- **InventoryServiceClient** → `inventory-service` — checks availability synchronously before order creation

**Kafka Consumers**:

| Topic | Group | Action |
|-------|-------|--------|
| `payment-succeeded` | `order-service-payment-group` | `handlePaymentSucceeded()` → sets status `CONFIRMED` |
| `payment-failed` | `order-service-payment-group` | `handlePaymentFailed()` → sets status `PAYMENT_FAILED` |

**Kafka Topics Produced (via Outbox)**:

| Topic | Trigger |
|-------|---------|
| `order-placed` | New order confirmed |
| `order-cancelled` | Order cancelled |
| `order-shipped` | Order shipped (from Shipping event ingest) |
| `notification-triggered` | Payment confirmed — produces notification event |

---

### 2.7 Payment Service (port 8086)

**Domain**: Financial Transactions

Integrates with Razorpay for payment processing. Uses idempotency keys to prevent double-charging. **Financial audit trail is immutable** — no rows are deleted.

**Main Entities**:
- `PaymentTransaction` — `orderId`, `razorpayOrderId`, `razorpayPaymentId`, `status`, `amountPaise`, `idempotencyKey`
- `Refund` — linked to original transaction
- `OutboxEvent`

**REST Endpoints**:

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/payments/initiate` | CUSTOMER | Create Razorpay order for checkout |
| `GET` | `/api/v1/payments/order/{orderId}` | CUSTOMER | Get payment status |
| `POST` | `/api/v1/payments/refund/{orderId}` | CUSTOMER/ADMIN | Initiate refund |
| `POST` | `/api/v1/payments/webhook/razorpay` | Public (HMAC) | Receive Razorpay webhook events |

**Kafka Consumers**:

| Topic | Group | Action |
|-------|-------|--------|
| `order-placed` | `payment-service-group` | Auto-initiate payment transaction |

**Kafka Topics Produced (via Outbox)**:

| Topic | Trigger |
|-------|---------|
| `payment-succeeded` | Razorpay webhook confirms payment |
| `payment-failed` | Webhook reports failure / timeout |

---

### 2.8 Shipping Service (port 8088)

**Domain**: Shipment Lifecycle

Creates shipments, assigns tracking numbers, and manages carrier integration. Never acts before payment is confirmed.

**Main Entities**:
- `Shipment` — `orderId`, `userId`, `trackingNumber`, `carrier`, `status` (`CREATED`/`DISPATCHED`/`IN_TRANSIT`/`DELIVERED`)

**REST Endpoints**:

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/shipments/orders/{orderId}` | CUSTOMER | Shipment info by order |
| `GET` | `/api/v1/shipments/track/{trackingNumber}` | Public | Track by tracking number |

**Kafka Consumers**:

| Topic | Group | Action |
|-------|-------|--------|
| `payment-succeeded` | `shipping-service-group` | `createShipment()` — generates tracking number |

**Kafka Topics Produced (via Outbox)**:

| Topic | Trigger |
|-------|---------|
| `order-shipped` | Shipment dispatched (picked up by Order Service) |

---

### 2.9 Notification Service (port 8089)

**Domain**: Communication Hub (Fan-in Pattern)

A pure consumer of events. Has no business logic about **why** a notification is sent. Receives a fully-formed notification request with `templateId`, `templateVars`, and `recipient`. Routes to the correct channel (Email/SMS/Push).

**Main Entities**:
- `Notification` — `userId`, `channel`, `templateId`, `status`, `recipient`, `deliveredAt`

**Kafka Consumers** (fan-in):

| Topic | Group | Action |
|-------|-------|--------|
| `notification-triggered` | `notification-service-group` | Generic notification dispatcher |
| `user-registered` | `notification-service-group` | Welcome email to new users |

**Notification Payload Schema**:
```json
{
  "userId":         "uuid",
  "channel":        "EMAIL",
  "templateId":     "order-confirmed-v1",
  "templateVars":   { "orderId": "...", "totalRupees": "..." },
  "recipientEmail": "user@example.com"
}
```

---

## 3. Complete Interservice Communication Map

| # | Source | Destination | Type | Channel | Reason |
|---|--------|-------------|------|---------|--------|
| 1 | Cart Service | Product Catalog | REST (Feign) | `GET /api/v1/products/{id}/variants` | Enrich cart items with real-time prices and status |
| 2 | Order Service | Cart Service | REST (Feign) | `GET /api/v1/cart` + `DELETE /api/v1/cart` | Fetch cart contents, then clear after order placed |
| 3 | Order Service | Inventory Service | REST (Feign) | `POST /api/v1/inventory/availability` | Synchronous pre-check before order creation |
| 4 | API Gateway | Keycloak | REST (OAuth2) | `/.well-known/openid-configuration` | Public key fetch for JWT signature validation |
| 5 | Identity Service | Keycloak | REST (Admin API) | `/admin/realms/ecommerce/users` | Create user in Keycloak during registration |
| 6 | Order Service | `order-placed` (Kafka) | Async | Kafka topic | Notifies Inventory and Payment |
| 7 | Payment Service | `payment-succeeded` (Kafka) | Async | Kafka topic | Notifies Order, Inventory, Shipping |
| 8 | Payment Service | `payment-failed` (Kafka) | Async | Kafka topic | Notifies Order and Inventory for rollback |
| 9 | Shipping Service | `order-shipped` (Kafka) | Async | Kafka topic | Notifies Order Service to update status |
| 10 | Identity Service | `user-registered` (Kafka) | Async | Kafka topic | Notifies User Service and Notification Service |
| 11 | Order Service | `notification-triggered` (Kafka) | Async | Kafka topic | Sends order confirmation email |
| 12 | Product Catalog | `product-published` (Kafka) | Async | Kafka topic | Notifies Search and Recommendation services |
| 13 | All Services | Eureka | REST | `http://localhost:8761` | Service registration and discovery |
| 14 | All Services | Config Server | REST | `http://localhost:8888` | Fetch environment configuration on startup |
| 15 | All Services | Zipkin | HTTP | `http://localhost:9411` | Ship distributed trace spans |
| 16 | Prometheus | All Services | REST (scrape) | `/actuator/prometheus` | Metric collection every 15s |

---

## 4. Synchronous Communication

### 4.1 Cart Service → Product Catalog Service

**Why synchronous?** Cart items must reflect accurate, real-time prices and product status. If a product is archived while in someone's cart, they should not be able to add it. Stale cached data is not acceptable for this.

**Feign Client**: `ProductCatalogClient`
```
GET /api/v1/products/{id}/variants
```
- **Request**: Path variable `productId`
- **Response**: `VariantResponse` with SKU, price, attributes, stock status
- **If Product Catalog is down**: Cart reads/writes fail with 503, protecting data integrity. Retry handled by Resilience4j circuit breaker with fallback.

---

### 4.2 Order Service → Cart Service

**Why synchronous?** Checkout is an **atomic user action**. The order must contain the exact items in the cart at the moment of placement. An async approach would allow the cart to be modified between "get cart" and "create order".

**Feign Client**: `CartServiceClient`
```
GET  /api/v1/cart         → Fetch cart contents
DELETE /api/v1/cart       → Clear cart after order creation
```
- **If Cart Service is down**: Order creation fails. The user sees an error but no state inconsistency occurs. This is correct — prevent "ghost orders" with no items.

---

### 4.3 Order Service → Inventory Service

**Why synchronous?** Inventory availability is the primary business constraint for order creation. You cannot place an order for an out-of-stock item. This check must happen **before** the order row is written.

**Feign Client**: `InventoryServiceClient`
```
POST /api/v1/inventory/availability
Body: [{ "skuId": "uuid", "quantity": 2 }, ...]
```
- **Response**: `AvailabilityResponse` with `allAvailable: boolean` and per-SKU availability
- **If Inventory Service is down**: Order creation fails with 503. A circuit breaker prevents cascading failures. The Resilience4j configuration sets a 2s timeout before tripping.

---

### 4.4 API Gateway → Keycloak (JWT Validation)

All requests entering through the Gateway are validated against Keycloak's public key fetched from the OIDC discovery endpoint. The Gateway does this **per request** using Spring Security's `spring-security-oauth2-resource-server`. No separate REST call happens per request — the RSA public key is cached and the JWT signature is verified locally.

---

### 4.5 Identity Service → Keycloak Admin API

**Why synchronous?** Registration must either fully succeed (user in Keycloak + DB) or fully fail. The Keycloak Admin REST API call is part of the same `@Transactional` unit with the local DB write.

```
POST /admin/realms/ecommerce/users
```
- **If Keycloak is down**: Registration throws `ServiceUnavailableException`. The user never exists in either system. Clean failure state.

---

## 5. Asynchronous Communication

### Kafka Event Reference Table

| Event / Topic | Producer | Consumers | Trigger |
|--------------|----------|-----------|---------|
| `user-registered` | Identity Service | User Service, Notification Service | New user registers |
| `order-placed` | Order Service | Payment Service, Inventory Service | Checkout completes |
| `payment-succeeded` | Payment Service | Order Service, Inventory Service, Shipping Service | Razorpay webhook confirms |
| `payment-failed` | Payment Service | Order Service, Inventory Service | Webhook reports failure |
| `order-shipped` | Shipping Service | Order Service | Carrier dispatched |
| `order-cancelled` | Order Service | (Notification Service listens via `notification-triggered`) | User cancels order |
| `notification-triggered` | Order Service, Payment Service, Shipping Service | Notification Service | Any event needing user communication |
| `product-published` | Product Catalog Service | Search Service, Recommendation Service | Admin publishes product |

---

### Event Payloads

#### `user-registered`
```json
{
  "userId":    "uuid",
  "email":     "user@example.com",
  "firstName": "Nakul",
  "lastName":  "Sharma",
  "role":      "CUSTOMER",
  "createdAt": "2026-03-28T10:00:00Z"
}
```

#### `order-placed`
```json
{
  "orderId":    "uuid",
  "userId":     "uuid",
  "totalPaise": 49900,
  "currency":   "INR",
  "items": [
    { "skuId": "uuid", "quantity": 2, "unitPricePaise": 24950 }
  ],
  "timestamp": "2026-03-28T10:05:00Z"
}
```

#### `payment-succeeded`
```json
{
  "orderId":            "uuid",
  "paymentId":          "uuid",
  "userId":             "uuid",
  "amountPaise":        49900,
  "razorpayPaymentId":  "pay_xxxx",
  "timestamp":          "2026-03-28T10:06:00Z"
}
```

#### `payment-failed`
```json
{
  "orderId":       "uuid",
  "userId":        "uuid",
  "failureReason": "CARD_DECLINED",
  "timestamp":     "2026-03-28T10:06:30Z"
}
```

#### `notification-triggered` (generic notification contract)
```json
{
  "userId":         "uuid",
  "channel":        "EMAIL",
  "templateId":     "order-confirmed-v1",
  "templateVars":   { "orderId": "...", "totalRupees": "499.00", "firstName": "Nakul" },
  "recipientEmail": "user@example.com"
}
```

---

## 6. Saga Implementations

### 6.1 User Registration Saga

This is a **choreography-based saga** (no central orchestrator). Events cascade naturally.

```
[User submits /register]
       │
       ▼
Identity Service
  1. Creates user in Keycloak (synchronous)
  2. Creates AppUser in identity_db (synchronous)
  3. Writes OutboxEvent { type: "user.registered" }
  ─────────────────────────────────────
  OutboxPoller publishes → [user-registered] topic
       │
       ├── User Service
       │     Consumes user-registered
       │     Creates UserProfile row (idempotent check on keycloakId)
       │
       └── Notification Service
             Consumes user-registered
             Sends welcome email via Mailhog/SendGrid
```

**Compensation**: If User Service fails to create profile, it does NOT acknowledge the Kafka message. Kafka redelivers to the consumer group. After 3 retries, goes to `user-registered.DLQ`. An alert fires. Profile creation can be replayed from the DLQ. The Identity record remains intact — only the profile is missing — which is a correctable inconsistency.

---

### 6.2 Order → Inventory → Payment Saga (Checkout Saga)

This is the most critical saga. A **choreography-based saga** that manages stock reservation, payment collection, and order confirmation.

**Happy Path**:
```
[Customer clicks "Place Order"]
       │
       ▼
Order Service (synchronous pre-checks)
  1. Feign → Cart Service: GET /api/v1/cart           (fetch items)
  2. Feign → Inventory Service: POST /availability     (check stock)
  3. Creates Order row (status: PAYMENT_PENDING)
  4. Writes OutboxEvent { type: "order.placed" }
  5. Feign → Cart Service: DELETE /api/v1/cart         (clear cart)
  ─────────────────────────────────────
  OutboxPoller publishes → [order-placed] topic
       │
       ├── Inventory Service (consumer group: inventory-service-group)
       │     reserveStock(orderId, skuQtyMap)
       │     Reduces available quantity atomically
       │     Writes reservation record
       │
       └── Payment Service (consumer group: payment-service-group)
             initiatePayment(orderId, amountPaise)
             Creates Razorpay order
             Creates PaymentTransaction (status: PENDING)
             → Returns Razorpay order ID to frontend

[Razorpay processes payment]
       │
       ▼
Payment Service webhook: POST /api/v1/payments/webhook/razorpay
  HMAC signature verified
  Transaction updated to COMPLETED
  Writes OutboxEvent { type: "payment.succeeded" }
  ─────────────────────────────────────
  OutboxPoller publishes → [payment-succeeded] topic
       │
       ├── Order Service
       │     handlePaymentSucceeded(orderId, paymentId)
       │     Order status → CONFIRMED
       │     Writes OutboxEvent { type: "notification.triggered" }
       │
       ├── Inventory Service
       │     confirmOrderReservations(orderId)
       │     Reservation status → CONFIRMED (stock permanently deducted)
       │
       └── Shipping Service
             createShipment(orderId, userId, address)
             Assigns tracking number
             Writes OutboxEvent { type: "order.shipped" }
```

**Failure Path (Payment Failure)**:
```
Payment fails → Razorpay webhook fires
       │
       ▼
Payment Service
  Transaction status → FAILED
  Writes OutboxEvent { type: "payment.failed" }
  ─────────────────────────────────────
  Publishes → [payment-failed] topic
       │
       ├── Order Service
       │     Order status → PAYMENT_FAILED
       │
       └── Inventory Service
             releaseOrderReservations(orderId)
             Reservation status → RELEASED
             Available quantity restored
```

---

### 6.3 Shipping Saga

```
Shipping Service receives [payment-succeeded]
       │
       ▼
  createShipment(orderId, userId, address)
  Generates UUID tracking number
  Carriers logic (simulated in dev)
  Saves Shipment row
  Writes OutboxEvent { type: "order.shipped" }
  ─────────────────────────────────────
  OutboxPoller publishes → [order-shipped]
       │
       └── Order Service
             Order status → SHIPPED
```

---

## 7. Outbox Pattern

### Which Services Use It

**Every service that produces Kafka events uses the Outbox pattern**:
- Identity Service
- Order Service
- Payment Service
- Inventory Service
- Shipping Service
- Product Catalog Service
- User Service

### How It Works

```
Service Request
     │
     ▼
@Transactional Method
  ┌──────────────────────────────────────┐
  │  1. Write business data (e.g. Order) │
  │  2. Write OutboxEvent row            │  ← Same DB transaction
  └──────────────────────────────────────┘
     │  COMMIT (atomic)
     ▼
OutboxPoller (@Scheduled every 500ms)
  1. SELECT * FROM outbox_events WHERE published = FALSE LIMIT 100
  2. For each event:
       topic = TOPIC_MAP[event.eventType]
       kafkaTemplate.send(topic, aggregateId, payload).get(5, SECONDS)
       SET published = TRUE
       SAVE
```

### Outbox Table Schema
```sql
CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,  -- e.g. "Order"
    aggregate_id   UUID NOT NULL,           -- e.g. orderId (Kafka partition key)
    event_type     VARCHAR(100) NOT NULL,   -- e.g. "order.placed"
    payload        JSONB        NOT NULL,
    published      BOOLEAN DEFAULT FALSE,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMP
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(published) WHERE published = FALSE;
```

### Why It Prevents Lost Messages

Without the Outbox Pattern:
```
Service A writes Order
Service A calls kafkaTemplate.send() ← Kafka is down
Message LOST — Order written, no event emitted
Inventory never reserves stock
```

With the Outbox Pattern:
```
Service A writes Order + OutboxEvent in one DB transaction
If Kafka is down: OutboxPoller retries at next 500ms interval
Order is never committed without the outbox row
Eventual delivery is guaranteed
```

The Kafka partition key is always the `aggregateId` (e.g. `orderId`). This ensures **ordered delivery** per aggregate — `order.placed` is always processed before `order.cancelled` for the same order.

---

## 8. Eventual Consistency

This system is **eventually consistent**, not immediately consistent. The trade-off is deliberately chosen for availability and scalability.

### Inventory Reservation
- At order placement: inventory is **reserved** (not permanently deducted)
- Reserved quantity counts as unavailable to other orders
- On payment success: reservation becomes permanent **confirmation**
- On payment failure: reservation is **released** back to available pool
- **Window of inconsistency**: Between `order-placed` event and `inventory-service` processing it (typically < 100ms on normal load)

### Order Status
- After payment webhook arrives: Order status changes from `PAYMENT_PENDING` → `CONFIRMED`
- This change is driven by asynchronous event processing
- **Window**: Order may appear `PAYMENT_PENDING` for 100–500ms after actual payment

### Notification Delivery
- Notification Service is a pure consumer — it processes events in its own time
- **SLA**: 95% of notifications sent within 30 seconds of triggering event
- Failed notifications go to the DLQ and are processed manually or re-queued

### User Profile Creation
- Profile is created asynchronously after Identity Service emits `user-registered`
- **Window**: Profile may not exist for ~100ms after registration completes
- The profile endpoint returns 404 during this tiny window (idempotent consumer handles duplicate creation attempts)

---

## 9. Data Ownership

**Core principle: Each service owns its data exclusively. No service reads another service's database.**

| Service | Data Owned | Notes |
|---------|-----------|-------|
| **Identity Service** | `app_users` — auth credentials, roles, Keycloak ID | Source of user identity |
| **User Service** | `user_profiles`, `addresses` — PII, preferences | Source of profile/address data |
| **Product Catalog Service** | `products`, `product_variants`, `categories` | Source of truth for product content |
| **Inventory Service** | `inventory`, `inventory_reservations` | Source of truth for stock levels |
| **Cart Service** | Redis keys `cart:{userId}` | Transient, session-scoped |
| **Order Service** | `orders`, `order_items` | Full order history |
| **Payment Service** | `payment_transactions`, `refunds` | Immutable financial audit trail |
| **Shipping Service** | `shipments` | Tracking and delivery state |
| **Notification Service** | `notifications` | Delivery history, deduplication |

### How Services Share Data Without Direct DB Access

- **Real-time queries**: Feign clients call REST endpoints (e.g. Order fetches cart via REST)
- **Event-driven sync**: Downstream services maintain a **local read model** built from events (e.g. User Service replicates user Identity data needed for profile)
- **Event payload enrichment**: Producing services include all data consumers need in the event payload (e.g. `order-placed` contains `totalPaise` so Payment Service doesn't need a REST call)

---

## 10. Complete User Journey Flows

### 10.1 User Registration

```
1. User fills registration form
2. → POST /api/v1/auth/register (Identity Service via Gateway)
3.   Identity Service calls Keycloak Admin API (sync) → Creates Keycloak user
4.   Identity Service creates AppUser row in identity_db
5.   Identity Service writes outbox_event { type: "user.registered" }
6.   OutboxPoller triggers, publishes to [user-registered] Kafka topic
7.   ← User gets 201 with JWT token (returned synchronously)

Async (within ~100ms):
8.   User Service consumes [user-registered] → creates UserProfile
9.   Notification Service consumes [user-registered] → sends welcome email
```

### 10.2 Browsing Products

```
1. → GET /api/v1/products?category=laptops (Product Catalog via Gateway)
2.   Product Catalog queries catalog_db, returns paginated list
3. → GET /api/v1/products/{id} (Product Catalog via Gateway)
4.   Returns full product detail with variants and pricing
5. → GET /api/v1/inventory/{skuId} (Inventory via Gateway, optional)
6.   Returns real-time stock level for the selected SKU
```

### 10.3 Adding to Cart

```
1. → POST /api/v1/cart/items { skuId, quantity } (Cart Service via Gateway)
2.   Cart Service calls Product Catalog via Feign to validate product/SKU
3.   If product valid and published: item added to Redis cart with enriched data
4. ← Cart returned with full item details
```

### 10.4 Checkout (Order Creation)

```
1. → POST /api/v1/orders {} (Order Service via Gateway)
   ┌─ Synchronous phase ─────────────────────────────────────────┐
2. │  Order Service: Feign → Cart Service → getCart()            │
3. │  Order Service: Feign → Inventory Service → checkAvailability│
4. │  If all available: create Order row (status: PAYMENT_PENDING)│
5. │  Write outbox_event { type: "order.placed", items, total }  │
6. │  Feign → Cart Service → clearCart()                         │
   └──────────────────────────────────────────────────────────────┘
7. ← Return { orderId, razorpayOrderId } with 201

Async:
8.   Payment Service: consumes [order-placed] → initiates Razorpay transaction
9.   Inventory Service: consumes [order-placed] → reserves stock
```

### 10.5 Payment

```
[User completes payment on Razorpay checkout]
1.   Razorpay → POST /api/v1/payments/webhook/razorpay
2.   HMAC signature verified
3.   PaymentTransaction status → COMPLETED
4.   Write outbox_event { type: "payment.succeeded" }
5.   OutboxPoller publishes [payment-succeeded]

Async fan-out:
6.   Order Service: Order status → CONFIRMED
7.   Order Service: publishes notification-triggered (order-confirmed email)
8.   Inventory Service: reservation → CONFIRMED (stock permanently deducted)
9.   Shipping Service: Shipment created with tracking number
10.  Notification Service: sends "Your order is confirmed!" email
```

### 10.6 Shipping

```
1.   Shipping Service: creates Shipment on payment-succeeded
2.   Carrier booking logic (simulated: tracking number generated)
3.   Shipment status → DISPATCHED
4.   Write outbox_event { type: "order.shipped" }
5.   OutboxPoller publishes [order-shipped]
6.   Order Service: Order status → SHIPPED
7.   Notification Service: "Your order has shipped" email
8.   User can track via: GET /api/v1/shipments/track/{trackingNumber}
```

### 10.7 Notifications

```
Any service that needs to notify the user does:
 1. Write outbox_event { type: "notification.triggered", payload: {
      userId, channel, templateId, templateVars, recipientEmail
    }}
 2. OutboxPoller publishes [notification-triggered]
 3. Notification Service consumes → processNotification()
 4. Thymeleaf renders template with templateVars
 5. Spring Mail sends via SMTP (Mailhog in dev, SendGrid in prod)
 6. Notification row stored with status: SENT or FAILED
```

---

## 11. Failure Scenarios

### 11.1 Inventory Service Fails During Checkout

```
Scenario: Order Service makes Feign call to Inventory → 503

Circuit Breaker state:  CLOSED → OPEN (after 50% failure rate)
Fallback:               throw ServiceUnavailableException
Order:                  NOT created (clean failure, no state written)
User sees:              "Unable to verify stock. Please try again."
Cart:                   NOT cleared (untouched)
Recovery:               Automatic when Inventory recovers. Circuit re-checks every 30s.
```

### 11.2 Payment Fails

```
Razorpay reports payment failure:
1. Webhook arrives → payment-failed published
2. Order Service: Order → PAYMENT_FAILED (not cancellable further)
3. Inventory Service: Releases reservation (stock restored)
4. User notified via notification-triggered event
5. User can retry payment for the same order (same idempotency key)
```

### 11.3 Kafka Is Unavailable

```
During Kafka outage:
- New events accumulate in outbox_events (published = FALSE)
- Outbox pollers continue running but cannot publish
- Business operations that require only DB writes still succeed:
    ✓ Order row created
    ✓ OutboxEvent row written
    ✗ Inventory not reserved (until Kafka recovers)
    ✗ Payment not initiated
    ✗ Notifications not sent

When Kafka recovers:
- OutboxPollers immediately poll and drain the queue
- Events published in FIFO order per aggregateId
- No data lost — all events were persisted to PostgreSQL
```

### 11.4 Shipping Service Crashes

```
Shipping Service is down:
- payment-succeeded events are not consumed
- Kafka offsets are NOT committed (MANUAL_IMMEDIATE ack-mode)
- When Shipping Service restarts, it resumes from uncommitted offset
- ALL unprocessed events are redelivered (idempotent shipment creation guards against duplicates)
- Order reaches CONFIRMED but not SHIPPED during the downtime window
- SLA impact: shipping delay, but no data loss
```

### 11.5 DLQ (Dead Letter Queue) Scenarios

```
Context: Every consumer retries 3 times on failure, then sends to DLQ.

DLQ Topics:
  payment-succeeded.DLQ  → Manual review of failed payment events
  order-placed.DLQ       → Critical: order placed but neither payment nor inventory acted
  notification-triggered.DLQ → Low severity: notification missed, usually retriable

DLQ handlers log CRITICAL alerts. In production:
  → PagerDuty alert fires
  → On-call engineer reviews dead-letter-orders table
  → Manual replay or compensation performed
```

---

## 12. Performance and Scalability

### Why Async Events Are Used

The Checkout flow has **6 independent downstream actions** after an order is placed:
- Stock reservation
- Payment initiation
- Notification send
- Search index update (if applicable)
- Recommendation model update
- Analytics event

If done synchronously, checkout latency = sum of all calls = potentially 2–5 seconds. With Kafka, checkout latency = **only the synchronous pre-checks + DB write** ≈ 200ms. Everything else happens in parallel, asynchronously.

### Why Some Calls Remain Synchronous

| Call | Reason |
|------|--------|
| Cart → Product Catalog | Price/status validation cannot be stale at checkout |
| Order → Cart | Must fetch exact items at the moment of order creation |
| Order → Inventory | Must confirm availability before committing the order |
| Identity → Keycloak | Registration must be atomic: Keycloak + DB or neither |

### Independent Scaling

Each service scales independently based on its load profile:
- **Product Catalog**: Read-heavy. Multiple replicas behind the Gateway load balancer. Cacheable responses.
- **Order Service**: CPU-intensive (saga coordination). Scale horizontally. Kafka consumer group balances partitions automatically.
- **Payment Service**: Low volume, financial-critical. Scale for reliability, not raw throughput.
- **Notification Service**: Bursty (spikes after flash sales). Kafka naturally buffers events. Consumer lag is acceptable.

### Redis Caching (Cart Service)

- Cart stored as a JSON blob in Redis with TTL of 7 days
- `O(1)` GET/SET operations via `RedisTemplate`
- Eliminates DB reads for every cart interaction
- Cart is **session-scoped**: no joins, no transactions

### Database Connection Pooling

All services use HikariCP:
- `maximum-pool-size: 15`
- `minimum-idle: 3–5`
- Configured per service based on expected concurrency

---

## 13. Observability

### Distributed Tracing (Zipkin)

Every service includes `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`. A `traceId` is generated at the API Gateway when a request arrives and propagated via HTTP headers (`X-B3-TraceId`) and Kafka headers to every downstream service.

```
User Request → API Gateway                    TraceId: abc123
                    → Identity Service        TraceId: abc123, SpanId: 001
                    → (Kafka) order-placed    TraceId: abc123, SpanId: 002
                         → Payment Service   TraceId: abc123, SpanId: 003
                         → Inventory Service TraceId: abc123, SpanId: 004
```

Access at `http://localhost:9411` — search by `traceId` to see the full call graph.

**Sampling**: 100% (`probability: 1.0`) in development. 5–10% in production.

### Metrics (Prometheus)

Prometheus scrapes `GET /actuator/prometheus` from all services every 15 seconds. Key metrics auto-exposed by Spring Boot Actuator + Micrometer:

| Metric | Description |
|--------|-------------|
| `http_server_requests_seconds` | Latency distribution per endpoint |
| `kafka_consumer_fetch_manager_records_consumed_total` | Event throughput |
| `kafka_consumer_fetch_manager_records_lag` | Consumer lag (alertable) |
| `hikaricp_connections_active` | DB connection pool utilization |
| `jvm_memory_used_bytes` | JVM heap/non-heap |
| `outbox_events_pending` | Custom: unpublished outbox size |

### Grafana Dashboards

Access at `http://localhost:3000` (admin/admin).

**Four Golden Signals** (per service):
1. **Latency** — `rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m])`
2. **Traffic** — `rate(http_server_requests_seconds_count[5m])`
3. **Errors** — `rate(http_server_requests_seconds_count{status~="5.."}[5m])`
4. **Saturation** — `hikaricp_connections_active / hikaricp_connections_max`

### Log Aggregation (Loki)

Promtail collects logs from all service processes and ships to Loki. Every log line includes:
- `traceId` (MDC field set by `MdcTraceFilter`)
- `spanId`
- `service` name
- Log level and timestamp

In Grafana Explore (Loki datasource), correlate logs and traces:
```
{service="order-service"} | json | traceId="abc123"
```

---

## 14. Final Architecture Summary

### Complete Communication Diagram

```
                    ┌─────────────┐
                    │  Keycloak   │◄──────── Identity Service (registration)
                    │   (8180)    │
                    └─────┬───────┘
                          │ JWT Public Key
                    ┌─────▼───────┐
User / Client ─────►│ API Gateway │
                    │   (8080)    │
                    └──────┬──────┘
           ┌───────────────┼───────────────────────┐
           │               │                       │
    ┌──────▼──────┐  ┌──────▼──────┐  ┌────────────▼──────┐
    │  Identity   │  │    User     │  │  Product Catalog   │
    │  Service    │  │  Service    │  │    Service         │
    │  (8081)     │  │  (8087)     │  │    (8082)          │
    └──────┬──────┘  └─────────────┘  └────────────┬───────┘
           │                                        │ Feign
    ┌──────▼──────────────────────────────────────────────┐
    │                   Kafka (9092)                      │
    │                                                      │
    │  user-registered ──────────────────────────────────►│
    │  order-placed ──────────────────────────────────────►│
    │  payment-succeeded ────────────────────────────────►│
    │  payment-failed ───────────────────────────────────►│
    │  order-shipped ────────────────────────────────────►│
    │  notification-triggered ───────────────────────────►│
    └──────────────────────────────────────────────────────┘
           │    │         │         │        │        │
    ┌──────▼──┐ │  ┌──────▼──┐ ┌───▼────┐ ┌▼──────┐ │
    │Inventory│ │  │ Payment │ │ Order  │ │Shipping│ │
    │Service  │ │  │ Service │ │Service ◄─► Service│ │
    │ (8083)  │ │  │ (8086)  │ │(8085)  │ │(8088)  │ │
    └─────────┘ │  └─────────┘ └───▲────┘ └────────┘ │
                │      Feign       │                   │
          ┌─────▼──────┐   ┌───────┴────┐    ┌─────────▼──┐
          │    Cart    │◄──│  (checkout)│    │Notification│
          │  Service   │   └────────────┘    │  Service   │
          │  (8084)    │                     │   (8089)   │
          │  (Redis)   │                     └────────────┘
          └────────────┘
```

### Kafka Event Flow Diagram

```
Identity        ──[user-registered]──►  User Service
Service                             └►  Notification Service (welcome email)

Order           ──[order-placed]─────►  Payment Service (initiate payment)
Service                             └►  Inventory Service (reserve stock)

Payment         ──[payment-succeeded]►  Order Service (CONFIRMED)
Service                             ├►  Inventory Service (CONFIRM reservation)
                                    └►  Shipping Service (create shipment)

Payment         ──[payment-failed]───►  Order Service (PAYMENT_FAILED)
Service                             └►  Inventory Service (RELEASE reservation)

Shipping        ──[order-shipped]────►  Order Service (SHIPPED)
Service

Order           ──[notification-triggered]►  Notification Service
Service / Any                            (order confirmed email, etc.)

Product         ──[product-published]──►  Search Service
Catalog                              └►  Recommendation Service
```

### Saga Flow Diagram

```
  CHECKOUT SAGA (Choreography-Based)

  [Customer Places Order]
         │
  ┌──────▼──────────────────────────────────────────────────────┐
  │ Order Service: Sync checks (Cart Feign + Inventory Feign)   │
  │ Creates Order (PAYMENT_PENDING) + OutboxEvent               │
  └──────┬──────────────────────────────────────────────────────┘
         │ publishes [order-placed]
         ├─────────────────────────────────────────┐
         ▼                                         ▼
  Inventory Service                          Payment Service
  reserveStock()                             initiatePayment()
         │                                         │
         ▼                                         ▼ (Razorpay webhook)
  Reservation: RESERVED                    publishes [payment-succeeded]
                                                    │
                          ┌─────────────────────────┼──────────────┐
                          ▼                          ▼              ▼
                   Order Service             Inventory Service  Shipping Service
                   status: CONFIRMED         CONFIRM reservation createShipment()
                   publishes notification         │                    │
                          │                       ▼             publishes [order-shipped]
                          ▼                 Stock Deducted           │
                   Notification Service      (permanent)             ▼
                   delivers email                             Order Service
                                                              status: SHIPPED

  COMPENSATION PATH (payment-failed):
  publishes [payment-failed]
         ├──► Order Service    → status: PAYMENT_FAILED
         └──► Inventory Service → RELEASE reservation (stock restored)
```

---

*This document was generated from code analysis of the ecommerce microservices platform.*
*All Kafka topics, REST endpoints, and entity structures reflect the actual implementation.*

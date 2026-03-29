# Cart Service — Postman Testing Guide

**Base URL:** `http://localhost:8084`  
**Port:** 8084  
**Storage:** Redis only (no database)  
**Auth:** All endpoints require a valid Keycloak JWT

---

## Postman Environment Setup

Create a Postman environment called **EcomPlatform Local** with these variables:

| Variable | Initial Value | Description |
|---|---|---|
| `base_url_cart` | `http://localhost:8084` | Cart service base |
| `base_url_order` | `http://localhost:8085` | Order service base |
| `base_url_auth` | `http://localhost:8180` | Keycloak |
| `realm` | `ecommerce` | Keycloak realm |
| `client_id` | `ecommerce-frontend` | OIDC client |
| `username` | `testcustomer` | Test user |
| `password` | `Test@1234` | Test user password |
| `access_token` | _(auto-populated)_ | JWT — set by login request |
| `sku_id_1` | `SKU-LAPTOP-PRO-16-BLK` | Test SKU |
| `sku_id_2` | `SKU-MOUSE-WIRELESS-WHT` | Another test SKU |

---

## Step 0 — Get Auth Token (Run This First)

Every other request depends on this. Run it once and save the token.

### Request
```
POST {{base_url_auth}}/realms/{{realm}}/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded
```

### Body (x-www-form-urlencoded)
```
grant_type  = password
client_id   = {{client_id}}
username    = {{username}}
password    = {{password}}
```

### Response (200 OK)
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsIn...(very long string)",
  "expires_in": 300,
  "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
  "token_type": "Bearer"
}
```

### Postman Test Script
Paste this in the **Tests** tab to auto-save the token:
```javascript
if (pm.response.code === 200) {
    const json = pm.response.json();
    pm.environment.set("access_token", json.access_token);
    pm.environment.set("refresh_token", json.refresh_token);
    console.log("Token saved. Expires in:", json.expires_in, "seconds");
}
```

All subsequent requests use:
```
Authorization: Bearer {{access_token}}
```

---

## 1. GET Cart

Fetch the current user's cart. The user identity comes **entirely from the JWT** — no userId in the URL.

### Request
```
GET {{base_url_cart}}/api/v1/cart
Authorization: Bearer {{access_token}}
```

### Response — Empty Cart (200 OK)
```json
{
  "userId": "a1b2c3d4-0000-0000-0000-000000000001",
  "items": [],
  "totalItems": 0,
  "subtotalPaise": 0,
  "subtotalRupees": 0.0,
  "totalSavingsPaise": 0,
  "totalSavingsRupees": 0.0
}
```

### Response — Cart With Items (200 OK)
```json
{
  "userId": "a1b2c3d4-0000-0000-0000-000000000001",
  "items": [
    {
      "skuId": "SKU-LAPTOP-PRO-16-BLK",
      "productId": "prod-uuid-here",
      "productName": "Laptop Pro 16",
      "variantName": "Black / 16GB RAM",
      "quantity": 1,
      "pricePaise": 8999900,
      "priceRupees": 89999.0,
      "mrpPaise": 10999900,
      "discountPercent": 18,
      "itemTotalPaise": 8999900,
      "itemTotalRupees": 89999.0,
      "imageUrl": "https://cdn.example.com/laptop-black.jpg",
      "brand": "TechBrand",
      "attributes": {
        "color": "Black",
        "ram": "16GB",
        "storage": "512GB SSD"
      }
    }
  ],
  "totalItems": 1,
  "subtotalPaise": 8999900,
  "subtotalRupees": 89999.0,
  "totalSavingsPaise": 2000000,
  "totalSavingsRupees": 20000.0
}
```

### Notes
- `pricePaise` is the **selling price** (what you pay)
- `mrpPaise` is the **Maximum Retail Price** (before discount)
- `discountPercent` = `(mrp - price) / mrp * 100`
- `totalSavings` = sum of `(mrp - price) * quantity` across all items
- All monetary values are in **paise** (₹1 = 100 paise) to avoid floating point errors

### Tests Script
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Has userId", () => {
    const json = pm.response.json();
    pm.expect(json).to.have.property("userId");
    pm.expect(json).to.have.property("items").that.is.an("array");
    pm.expect(json.subtotalPaise).to.be.a("number");
});
```

---

## 2. Add Item to Cart

### Request
```
POST {{base_url_cart}}/api/v1/cart/items
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

### Body
```json
{
  "skuId": "{{sku_id_1}}",
  "quantity": 1
}
```

### Response (200 OK) — Full Cart State
Returns the **entire cart** after the add (same shape as GET /cart).

### What Happens Internally
1. Cart Service calls **Product Catalog Service** to fetch live price + name + image for the SKU
2. If the SKU is inactive (`status != ACTIVE`), returns `400 Bad Request`
3. If the same SKU is already in the cart → **quantity is added** (not overwritten)
4. Price is **re-snapshotted** from the catalog — so if the product price dropped since last add, the customer pays the new lower price
5. Result stored in **Redis Hash** with key `cart:{userId}` and field `{skuId}`

### Test — Add Two Different Items
**Request 1:**
```json
{ "skuId": "SKU-LAPTOP-PRO-16-BLK", "quantity": 1 }
```
**Request 2:**
```json
{ "skuId": "SKU-MOUSE-WIRELESS-WHT", "quantity": 2 }
```

### Test — Add Same SKU Again (Quantity Accumulates)
Add `SKU-LAPTOP-PRO-16-BLK` with quantity 1, then add it again with quantity 1.
Expected: cart shows quantity **2** for that SKU, not 1.

### Error Cases
| Scenario | Request Body | Expected Response |
|----------|-------------|-------------------|
| Missing skuId | `{"quantity": 1}` | `400 Bad Request` (validation) |
| quantity = 0 | `{"skuId": "...", "quantity": 0}` | `400 Bad Request` (@Min(1)) |
| Negative quantity | `{"skuId": "...", "quantity": -5}` | `400 Bad Request` |
| SKU doesn't exist | `{"skuId": "INVALID-SKU-999"}` | `404` from catalog (circuit breaker returns empty) |
| No auth token | _(no Authorization header)_ | `401 Unauthorized` |

### Tests Script
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Item appears in cart", () => {
    const json = pm.response.json();
    const item = json.items.find(i => i.skuId === pm.environment.get("sku_id_1"));
    pm.expect(item).to.exist;
    pm.expect(item.quantity).to.be.at.least(1);
    pm.expect(item.pricePaise).to.be.greaterThan(0);
});
```

---

## 3. Update Cart Item Quantity

### Request
```
PUT {{base_url_cart}}/api/v1/cart/items/{{sku_id_1}}
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

### Body — Change Quantity to 3
```json
{
  "quantity": 3
}
```

### Response (200 OK)
Returns full cart with updated quantity.

### Special Case — Set Quantity to 0 = Remove
```json
{
  "quantity": 0
}
```
This **removes the item** from the cart entirely. The Cart Service internally calls `removeItem()` when it sees quantity 0. This enables the frontend "–" button to remove items by setting quantity to 0.

### Error Cases
| Scenario | Expected |
|----------|---------|
| SKU not in cart | `404 Not Found` |
| Negative quantity | `400 Bad Request` |

### Tests Script
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Quantity updated to 3", () => {
    const json = pm.response.json();
    const item = json.items.find(i => i.skuId === pm.environment.get("sku_id_1"));
    pm.expect(item.quantity).to.equal(3);
    pm.expect(item.itemTotalPaise).to.equal(item.pricePaise * 3);
});
```

---

## 4. Remove Specific Item

### Request
```
DELETE {{base_url_cart}}/api/v1/cart/items/{{sku_id_1}}
Authorization: Bearer {{access_token}}
```

### Response (200 OK)
Returns cart without the removed item.

### Tests Script
```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Item removed from cart", () => {
    const json = pm.response.json();
    const item = json.items.find(i => i.skuId === pm.environment.get("sku_id_1"));
    pm.expect(item).to.be.undefined;
});
```

---

## 5. Clear Entire Cart

### Request
```
DELETE {{base_url_cart}}/api/v1/cart
Authorization: Bearer {{access_token}}
```

### Response (204 No Content)
Empty body. Cart is entirely cleared from Redis.

### When to Use
- After order is placed (Order Service calls this)
- Customer "empty cart" button
- Session end cleanup

### Verify Cleared
```
GET {{base_url_cart}}/api/v1/cart
```
Expected: `items: []`, `subtotalPaise: 0`

---

## 6. Security Test — Cross-User Cart Access

The Cart Service uses the JWT to identify users — there is no userId parameter. This means it's impossible to access another user's cart.

Get a token for User A, then try to read the cart. You will only see User A's cart regardless of any parameter you pass. There's no endpoint to specify a userId.

---

## 7. Health Check

```
GET {{base_url_cart}}/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "redis": { "status": "UP" }
  }
}
```

---

## 8. Metrics

```
GET {{base_url_cart}}/actuator/prometheus
```
Look for: `http_server_requests_seconds_count`, `redis_commands_duration_seconds`

---

## Complete Cart Flow Test Sequence

Run these in order in a Postman Collection:

1. **GET /auth/token** — save access_token
2. **GET /cart** — assert empty
3. **POST /items** with sku_id_1, qty=1 — assert 1 item
4. **POST /items** with sku_id_2, qty=2 — assert 2 items
5. **POST /items** with sku_id_1, qty=1 again — assert sku_id_1 qty=2 (accumulated)
6. **PUT /items/sku_id_1** qty=5 — assert total items = 7
7. **PUT /items/sku_id_1** qty=0 — assert sku_id_1 removed, only sku_id_2 remains
8. **DELETE /items/sku_id_2** — assert cart has 0 items
9. **DELETE /cart** — 204, cart cleared

---

## Redis Internals (Debugging)

Connect to Redis CLI:
```bash
docker exec -it ecom-redis redis-cli -a redis_secret_123
```

```bash
# List all cart keys
KEYS cart:*

# See all items in a user's cart (replace USER_UUID)
HGETALL cart:a1b2c3d4-0000-0000-0000-000000000001

# Check TTL (cart expires after 7 days of inactivity)
TTL cart:a1b2c3d4-0000-0000-0000-000000000001

# Delete a cart manually
DEL cart:a1b2c3d4-0000-0000-0000-000000000001
```

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `503` on add item | Product Catalog Service down | Start product-catalog-service |
| `400` "Product is not available" | SKU status is not ACTIVE | Check catalog DB: `SELECT status FROM products WHERE ...` |
| Items disappear unexpectedly | Redis TTL expired | Cart has a 7-day TTL; add items again |
| `401 Unauthorized` | Token expired (5 min lifetime) | Re-run the token request |
| `404` on update/remove | SKU was never in cart | Do a GET /cart first to see what's there |

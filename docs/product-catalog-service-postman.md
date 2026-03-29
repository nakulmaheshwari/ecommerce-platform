# Product Catalog Service — Postman Testing Guide

**Base URL:** `http://localhost:8082`  
**Port:** 8082  
**Database:** PostgreSQL (`product_db`) + Redis cache  
**Auth:** All GET endpoints are public. POST (create/publish) requires ADMIN.

---

## Architecture Notes

- Products are **cached in Redis** after first load. TTL is typically 10 minutes. If you create a product and don't see it immediately via GET, Redis may be serving a stale cache.
- Products have a **slug** (URL-friendly name auto-generated from the product name). Use slugs for SEO-friendly URLs.
- A product can have multiple **variants** (e.g., different colours/sizes), each with its own SKU and price.
- Products start as `DRAFT` and must be explicitly **published** before they are active.

**Product Status State Machine:**
```
DRAFT → ACTIVE  (via /admin/products/{id}/publish)
ACTIVE → INACTIVE (admin deactivates)
INACTIVE → ACTIVE
```

---

## Postman Environment

| Variable | Value |
|---|---|
| `base_url_catalog` | `http://localhost:8082` |
| `admin_token` | JWT with ADMIN role |
| `category_id` | _(UUID from GET /categories)_ |
| `product_id` | _(UUID saved from create product)_ |
| `product_slug` | _(slug from create/get product response)_ |

---

## 1. Get All Categories (Public)

Returns root-level categories. Use `categoryId` values here for browsing products.

### Request
```
GET {{base_url_catalog}}/api/v1/categories
```

### Response (200 OK)
```json
[
  {
    "id": "cat-electronics-uuid",
    "name": "Electronics",
    "slug": "electronics",
    "description": "Gadgets and devices",
    "productCount": 142
  },
  {
    "id": "cat-fashion-uuid",
    "name": "Fashion",
    "slug": "fashion",
    "description": "Clothing and accessories",
    "productCount": 89
  },
  {
    "id": "cat-home-uuid",
    "name": "Home & Kitchen",
    "slug": "home-kitchen",
    "description": "Home appliances and kitchenware",
    "productCount": 67
  }
]
```

### Tests Script
```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
pm.test("Returns array of categories", () => {
    const j = pm.response.json();
    pm.expect(j).to.be.an("array");
    if (j.length > 0) {
        pm.environment.set("category_id", j[0].id);
        console.log("Category ID saved:", j[0].id);
    }
});
```

---

## 2. Get Products by Category (Paginated, Public)

### Request
```
GET {{base_url_catalog}}/api/v1/categories/{{category_id}}/products?page=0&size=20&sortBy=newest
```

### Query Parameters
| Param | Default | Options | Description |
|---|---|---|---|
| `page` | `0` | any int | Zero-indexed page number |
| `size` | `20` | `1–100` (capped at 100) | Products per page |
| `sortBy` | `newest` | `newest`, `price-asc`, `price-desc`, `popular` | Sort order |

### Response (200 OK)
```json
{
  "products": [
    {
      "id": "product-uuid",
      "sku": "LAPTOP-PRO-16",
      "name": "Laptop Pro 16",
      "slug": "laptop-pro-16",
      "brand": "TechBrand",
      "category": {
        "id": "cat-electronics-uuid",
        "name": "Electronics",
        "slug": "electronics"
      },
      "pricePaise": 8999900,
      "mrpPaise": 10999900,
      "priceRupees": 89999.0,
      "mrpRupees": 109999.0,
      "discountPercent": 18,
      "taxPercent": 18.0,
      "status": "ACTIVE",
      "variants": [
        {
          "id": "variant-uuid",
          "sku": "SKU-LAPTOP-PRO-16-BLK",
          "name": "Black / 16GB",
          "pricePaise": 8999900,
          "attributes": { "color": "Black", "ram": "16GB", "storage": "512GB" },
          "isActive": true
        },
        {
          "id": "variant-uuid-2",
          "sku": "SKU-LAPTOP-PRO-16-SLV",
          "name": "Silver / 32GB",
          "pricePaise": 12999900,
          "attributes": { "color": "Silver", "ram": "32GB", "storage": "1TB" },
          "isActive": true
        }
      ],
      "images": [
        {
          "id": "img-uuid",
          "url": "https://cdn.example.com/laptop-black.jpg",
          "altText": "Laptop Pro 16 Black",
          "isPrimary": true,
          "sortOrder": 0
        }
      ],
      "publishedAt": "2026-03-01T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 142,
  "totalPages": 8,
  "hasNext": true,
  "hasPrevious": false
}
```

### Tests Script
```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
pm.test("Paginated response", () => {
    const j = pm.response.json();
    pm.expect(j).to.have.property("products").that.is.an("array");
    pm.expect(j).to.have.property("totalElements");
    pm.expect(j).to.have.property("hasNext");
    if (j.products.length > 0) {
        pm.environment.set("product_id", j.products[0].id);
        pm.environment.set("product_slug", j.products[0].slug);
    }
});
```

---

## 3. Get Product by ID (Public)

### Request
```
GET {{base_url_catalog}}/api/v1/products/{{product_id}}
```

### Response (200 OK)
Same as the product object above — full details including all variants and images.

### Error Cases
| Scenario | Response |
|----------|---------|
| Invalid UUID | `400 Bad Request` |
| Product not found | `404 Not Found` |
| Product is DRAFT | `404` (drafts are not publicly visible) |

---

## 4. Get Product by Slug (Public)

Useful for SEO-friendly URLs like `/products/laptop-pro-16`.

### Request
```
GET {{base_url_catalog}}/api/v1/products/slug/{{product_slug}}
```

**Example:**
```
GET http://localhost:8082/api/v1/products/slug/laptop-pro-16
```

### Response (200 OK)
Same as Get by ID response.

---

## 5. Get Product Variants (Public)

Get just the list of variants for a product — useful for the "Select Size/Color" UI.

### Request
```
GET {{base_url_catalog}}/api/v1/products/{{product_id}}/variants
```

### Response (200 OK)
```json
[
  {
    "id": "variant-uuid-1",
    "sku": "SKU-LAPTOP-PRO-16-BLK",
    "name": "Black / 16GB",
    "pricePaise": 8999900,
    "attributes": {
      "color": "Black",
      "ram": "16GB",
      "storage": "512GB SSD"
    },
    "isActive": true
  },
  {
    "id": "variant-uuid-2",
    "sku": "SKU-LAPTOP-PRO-16-SLV",
    "name": "Silver / 32GB",
    "pricePaise": 12999900,
    "attributes": {
      "color": "Silver",
      "ram": "32GB",
      "storage": "1TB SSD"
    },
    "isActive": true
  }
]
```

---

## 6. Get Related Products (Public)

Returns similar products from the same category (typically 4–6 items).

### Request
```
GET {{base_url_catalog}}/api/v1/products/{{product_id}}/related
```

### Response (200 OK)
```json
[
  { "id": "...", "name": "Laptop Air 13", "pricePaise": 6999900, "slug": "laptop-air-13", ... },
  { "id": "...", "name": "Laptop Ultra 14", "pricePaise": 11999900, "slug": "laptop-ultra-14", ... }
]
```

---

## 7. Create Product (Admin Only)

Creates a product in **DRAFT** status. It is not visible to customers until published.

### Request
```
POST {{base_url_catalog}}/api/v1/admin/products
Authorization: Bearer {{admin_token}}
Content-Type: application/json
```

### Body — Minimal (no variants)
```json
{
  "sku": "WIRELESS-KEYBOARD-BLK",
  "name": "Wireless Keyboard Pro",
  "description": "Compact wireless keyboard with backlit keys and 3-month battery life.",
  "categoryId": "{{category_id}}",
  "brand": "KeyTech",
  "pricePaise": 299900,
  "mrpPaise": 399900,
  "taxPercent": 18.0,
  "isDigital": false,
  "weightGrams": 450,
  "imageUrls": [
    "https://cdn.example.com/keyboard-black-front.jpg",
    "https://cdn.example.com/keyboard-black-side.jpg"
  ]
}
```

### Body — With Variants (recommended)
```json
{
  "sku": "GAMING-CHAIR-ERGONOMIC",
  "name": "ErgoPro Gaming Chair",
  "description": "Ergonomic gaming chair with lumbar support and adjustable armrests.",
  "categoryId": "{{category_id}}",
  "brand": "ChairCraft",
  "pricePaise": 1499900,
  "mrpPaise": 1999900,
  "taxPercent": 18.0,
  "isDigital": false,
  "weightGrams": 18000,
  "imageUrls": [
    "https://cdn.example.com/chair-black.jpg"
  ],
  "variants": [
    {
      "sku": "SKU-CHAIR-BLK-M",
      "name": "Black / Medium",
      "pricePaise": 1499900,
      "attributes": {
        "color": "Black",
        "size": "Medium"
      }
    },
    {
      "sku": "SKU-CHAIR-RED-L",
      "name": "Red / Large",
      "pricePaise": 1599900,
      "attributes": {
        "color": "Red",
        "size": "Large"
      }
    }
  ]
}
```

### Response (201 Created)
```json
{
  "id": "new-product-uuid",
  "sku": "GAMING-CHAIR-ERGONOMIC",
  "name": "ErgoPro Gaming Chair",
  "slug": "ergopro-gaming-chair",
  "status": "DRAFT",
  "pricePaise": 1499900,
  "mrpPaise": 1999900,
  "priceRupees": 14999.0,
  "mrpRupees": 19999.0,
  "discountPercent": 25,
  "variants": [...],
  "publishedAt": null
}
```

Save the `id` → `{{product_id}}` for the publish step.

### Validation Rules
| Field | Rule |
|---|---|
| `sku` | Required, max 100 chars, must be unique |
| `name` | Required, max 500 chars |
| `description` | Optional, max 5000 chars |
| `categoryId` | Required, must be existing category UUID |
| `pricePaise` | Required, min 1 (paise) |
| `mrpPaise` | Required, min 1 (paise) |
| `taxPercent` | Required, 0.00–100.00 |
| `weightGrams` | Optional, min 0 |
| `variants[].sku` | Required, must be unique |
| `variants[].attributes` | Required map (e.g., `{"color": "Black"}`) |

### Error Cases
| Scenario | Response |
|----------|---------|
| Duplicate SKU | `409 Conflict` |
| Invalid categoryId | `404 Not Found` |
| `pricePaise` = 0 | `400 Bad Request` |
| Missing required field | `400 Bad Request` with field-level error |
| Customer token | `403 Forbidden` |

### Tests Script
```javascript
pm.test("Status 201", () => pm.response.to.have.status(201));
pm.test("Product created in DRAFT", () => {
    const j = pm.response.json();
    pm.expect(j.status).to.equal("DRAFT");
    pm.expect(j.slug).to.be.a("string");
    pm.environment.set("product_id", j.id);
    pm.environment.set("product_slug", j.slug);
    console.log("Product created:", j.id, "slug:", j.slug);
});
```

---

## 8. Publish Product (Admin Only)

Moves a DRAFT product to ACTIVE. Only after this does the product appear in category listings and GET by slug/id.

### Request
```
POST {{base_url_catalog}}/api/v1/admin/products/{{product_id}}/publish
Authorization: Bearer {{admin_token}}
```

No request body.

### Response (200 OK)
```json
{
  "id": "new-product-uuid",
  "name": "ErgoPro Gaming Chair",
  "slug": "ergopro-gaming-chair",
  "status": "ACTIVE",
  "publishedAt": "2026-03-28T14:30:00Z"
}
```

### After Publishing
The product is now visible via:
- `GET /api/v1/products/{id}` — public
- `GET /api/v1/products/slug/ergopro-gaming-chair` — public
- `GET /api/v1/categories/{categoryId}/products` — included in listing

### Error Cases
| Scenario | Response |
|----------|---------|
| Product already ACTIVE | `200 OK` (idempotent) |
| Product not found | `404 Not Found` |
| Customer token | `403 Forbidden` |

---

## 9. Full Admin Flow Test Sequence

```
1. GET /categories          → save category_id
2. POST /admin/products     → create product, save product_id & product_slug
3. GET /products/{id}       → should be 404 (still DRAFT)
4. POST /admin/products/{id}/publish
5. GET /products/{id}       → now 200, status=ACTIVE
6. GET /products/slug/{slug} → same product, by slug
7. GET /categories/{categoryId}/products → product appears in listing
8. GET /products/{id}/variants → list of variants
9. GET /products/{id}/related  → related products
```

---

## 10. Redis Cache Inspection

```bash
docker exec -it ecom-redis redis-cli -a redis_secret_123
```

```bash
# List all cached product keys
KEYS product:*

# Check if a specific product is cached
EXISTS product:{{product_id}}

# View cached product data
GET product:{{product_id}}

# Check TTL
TTL product:{{product_id}}

# Force cache eviction (product will be re-fetched from DB)
DEL product:{{product_id}}
```

---

## 11. DB Inspection

```
Host: localhost  Port: 5432  DB: product_db
User: ecom_admin  Password: ecom_secret_123
```

```sql
-- All products and their status
SELECT id, sku, name, slug, status, price_paise/100.0 as price_rupees,
       mrp_paise/100.0 as mrp_rupees, published_at
FROM products
ORDER BY created_at DESC;

-- All variants for a product
SELECT id, sku, name, price_paise/100.0 as price_rupees, attributes, is_active
FROM product_variants
WHERE product_id = 'your-product-uuid';

-- Products by category
SELECT p.name, p.status, c.name as category
FROM products p JOIN categories c ON p.category_id = c.id
ORDER BY c.name, p.name;

-- Outbox events — check product-created events published to Kafka
SELECT event_type, published, aggregate_id, created_at
FROM outbox_events
ORDER BY created_at DESC LIMIT 10;
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `404` on GET product slug | Product is DRAFT | Publish it first: POST /admin/products/{id}/publish |
| Old product data returned | Redis cache hit | DEL the cache key or wait for TTL to expire |
| `403` on create/publish | Not ADMIN role | Get admin token from Keycloak |
| `409` on create | Duplicate SKU | Use a unique SKU string |
| Cart add fails for new product | SKU not in Inventory DB | Seed: `INSERT INTO inventory(sku_id, product_id, available_qty, ...)` |

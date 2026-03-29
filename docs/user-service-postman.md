# User Service — Postman Testing Guide

**Base URL:** `http://localhost:8087`  
**Port:** 8087  
**Database:** PostgreSQL (`user_db`)  
**Auth:** Every endpoint requires a Bearer Token (JWT) from Keycloak.

---

## Postman Environment

Add these variables to your **EcomPlatform Local** environment:

| Variable | Value |
|---|---|
| `base_url_user` | `http://localhost:8087` |
| `customer_token` | _(JWT from Keycloak for `testcustomer`)_ |

---

## 1. Get My Profile
Returns the profile of the currently authenticated user.

### Request
```
GET {{base_url_user}}/api/v1/users/me
Authorization: Bearer {{customer_token}}
```

### Response (200 OK)
```json
{
  "id": "7648f5a1-...",
  "email": "test@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "fullName": "John Doe",
  "phoneNumber": "+919876543210",
  "preferences": {},
  "isActive": true
}
```

---

## 2. Update Profile (PATCH)
Updates only the provided fields.

### Request
```
PATCH {{base_url_user}}/api/v1/users/me
Authorization: Bearer {{customer_token}}
Content-Type: application/json
```
```json
{
  "firstName": "Nakul",
  "phoneNumber": "+919999999999"
}
```

---

## 3. Address Management

### Add Address
```
POST {{base_url_user}}/api/v1/users/me/addresses
Authorization: Bearer {{customer_token}}
```
```json
{
  "label": "Home",
  "fullName": "Nakul Dev",
  "phoneNumber": "+919999999999",
  "line1": "123 MG Road",
  "city": "Mumbai",
  "state": "Maharashtra",
  "pincode": "400001",
  "country": "India",
  "makeDefault": true
}
```

### Get Addresses
```
GET {{base_url_user}}/api/v1/users/me/addresses
Authorization: Bearer {{customer_token}}
```

### Delete Address (Soft Delete)
```
DELETE {{base_url_user}}/api/v1/users/me/addresses/{{address_id}}
Authorization: Bearer {{customer_token}}
```

---

## 4. Update Preferences
Updates a single preference key.

### Request
```
PATCH {{base_url_user}}/api/v1/users/me/preferences
Authorization: Bearer {{customer_token}}
```
```json
{
  "key": "newsletter",
  "value": true
}
```

---

## 5. Internal API (Admin/Service Only)
Fetch default address for a specific user ID.

### Request
```
GET {{base_url_user}}/api/v1/internal/users/{{test_user_id}}/default-address
Authorization: Bearer {{admin_token}}
```

---

## Verification Scripts (Tests tab)

### Profile Check
```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
pm.test("Matches email", () => {
    pm.expect(pm.response.json().email).to.equal("test@example.com");
});
```

### Address Creation Check
```javascript
pm.test("Status 201", () => pm.response.to.have.status(201));
pm.test("Is Default", () => {
    pm.expect(pm.response.json().isDefault).to.be.true;
});
pm.environment.set("address_id", pm.response.json().id);
```

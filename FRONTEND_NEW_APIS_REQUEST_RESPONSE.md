# Frontend API Additions: Request/Response Guide

This document covers the newly added APIs:
- `PATCH` partial-update endpoints
- Asset employee assignment endpoints
- Organisation audit-event endpoints
- Billing/Subscription endpoints (Paystack integration)

## Common Request Headers

```http
Authorization: Bearer <jwt_token>
X-Organisation-Id: <organisation_uuid>
Content-Type: application/json
```

Notes:
- `X-Organisation-Id` is required on protected tenant-scoped routes.
- `PATCH` requests should only include fields being changed.

---

## 1) Asset Employee Assignment APIs

### 1.1 Assign Asset To Employee

`POST /api/v1/assets/{assetId}/assign-user/{userId}`

Request body:
```json
{}
```

Response `200 OK`:
```json
{
  "id": "c0e8e2a4-6d25-4c67-a620-c0222f6cb7bf",
  "name": "Dell Latitude 7440",
  "status": "IN_USE",
  "assignedUserId": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
  "departmentId": "f61f7d2f-2a3b-49fc-9a7c-4bb2df0919fd",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165"
}
```

### 1.2 Unassign Asset From Employee

`DELETE /api/v1/assets/{assetId}/assign-user`

Request body:
```json
{}
```

Response `200 OK`:
```json
{
  "id": "c0e8e2a4-6d25-4c67-a620-c0222f6cb7bf",
  "name": "Dell Latitude 7440",
  "status": "IN_USE",
  "assignedUserId": null,
  "departmentId": "f61f7d2f-2a3b-49fc-9a7c-4bb2df0919fd",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165"
}
```

---

## 2) New PATCH Partial-Update APIs

## 2.1 Assets

`PATCH /api/v1/assets/{id}`

Request example:
```json
{
  "name": "Dell Latitude 7450",
  "status": "IN_USE",
  "locationId": "67d0b8a5-cf7f-4f26-ab19-42a6286a8a2a"
}
```

Response `200 OK`:
```json
{
  "id": "c0e8e2a4-6d25-4c67-a620-c0222f6cb7bf",
  "name": "Dell Latitude 7450",
  "status": "IN_USE",
  "locationId": "67d0b8a5-cf7f-4f26-ab19-42a6286a8a2a",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165"
}
```

## 2.2 Categories

`PATCH /api/v1/categories/{id}`

Request:
```json
{
  "name": "End User Devices",
  "assetPrefixCode": "EUD"
}
```

Response:
```json
{
  "id": "2c4a2fef-522a-4fff-a851-11983e0e4e5f",
  "name": "End User Devices",
  "assetPrefixCode": "EUD",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165"
}
```

## 2.3 Departments

`PATCH /api/v1/departments/{id}`

Request:
```json
{
  "budgetLimit": 250000.00,
  "status": "ACTIVE"
}
```

Response:
```json
{
  "id": "f61f7d2f-2a3b-49fc-9a7c-4bb2df0919fd",
  "name": "Finance",
  "budgetLimit": 250000.00,
  "status": "ACTIVE",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165"
}
```

## 2.4 Depreciation Policies

`PATCH /api/v1/depreciation-policies/{id}`

Request:
```json
{
  "description": "Laptop policy (5 years)",
  "usefulLifeMonths": 60
}
```

Response:
```json
{
  "id": "0ce64fca-0662-4930-b5cc-8effee0f89d9",
  "name": "Laptop Straight Line",
  "description": "Laptop policy (5 years)",
  "usefulLifeMonths": 60,
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165"
}
```

## 2.5 Disposal Records

`PATCH /api/v1/disposals/{id}`

Request:
```json
{
  "saleValue": 450.00,
  "reason": "Resale after refresh"
}
```

Response:
```json
{
  "id": "5fdff35f-0c39-4d66-8ae7-f9d54b9cc1d2",
  "assetId": "c0e8e2a4-6d25-4c67-a620-c0222f6cb7bf",
  "saleValue": 450.00,
  "reason": "Resale after refresh",
  "approvedById": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165"
}
```

## 2.6 Locations

`PATCH /api/v1/locations/{id}`

Request:
```json
{
  "building": "HQ Building B",
  "floor": "4"
}
```

Response:
```json
{
  "id": "67d0b8a5-cf7f-4f26-ab19-42a6286a8a2a",
  "name": "HQ Main",
  "building": "HQ Building B",
  "floor": "4",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165"
}
```

## 2.7 Maintenance Records

`PATCH /api/v1/maintenance/{id}`

Request:
```json
{
  "status": "IN_PROGRESS",
  "cost": 125.00
}
```

Response:
```json
{
  "id": "cc7bb8a9-3359-4f0f-a358-29f53593a01d",
  "assetId": "c0e8e2a4-6d25-4c67-a620-c0222f6cb7bf",
  "status": "IN_PROGRESS",
  "cost": 125.00
}
```

## 2.8 Organisations

`PATCH /api/v1/organisations/{id}`

Request:
```json
{
  "contactEmail": "itops@acme.com",
  "timezone": "UTC"
}
```

Response:
```json
{
  "id": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
  "name": "Acme Ltd",
  "contactEmail": "itops@acme.com",
  "timezone": "UTC",
  "status": "ACTIVE"
}
```

## 2.9 Purchase Orders

`PATCH /api/v1/purchase-orders/{id}`

Request:
```json
{
  "remarks": "Change delivery date to next week",
  "currency": "USD"
}
```

Response:
```json
{
  "id": "a28ac8e9-2725-4831-a6ab-0cae3c952c36",
  "poNumber": "PO-2026-0008",
  "currency": "USD",
  "status": "DRAFT",
  "remarks": "Change delivery date to next week",
  "departmentId": "f61f7d2f-2a3b-49fc-9a7c-4bb2df0919fd",
  "supplierId": "8c69db58-f32d-4107-8264-41fba2c0eb22",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165"
}
```

## 2.10 Roles

`PATCH /api/v1/roles/{id}`

Request:
```json
{
  "description": "Updated role description",
  "permissions": "{\"assets.read\":true,\"assets.write\":true}"
}
```

Response:
```json
{
  "id": "8fe6e847-7e0e-4888-bec8-8d2d5dd1b3d7",
  "name": "ROLE_MANAGER",
  "description": "Updated role description",
  "permissions": "{\"assets.read\":true,\"assets.write\":true}",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165"
}
```

## 2.11 Suppliers

`PATCH /api/v1/suppliers/{id}`

Request:
```json
{
  "contactPerson": "Jane Doe",
  "phone": "+1-555-1000"
}
```

Response:
```json
{
  "id": "8c69db58-f32d-4107-8264-41fba2c0eb22",
  "name": "Tech Supplies Inc",
  "contactPerson": "Jane Doe",
  "phone": "+1-555-1000",
  "status": "ACTIVE",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165"
}
```

## 2.12 Users

`PATCH /api/v1/users/{id}`

Request:
```json
{
  "phone": "+1-555-9090",
  "jobTitle": "Senior IT Analyst",
  "status": "ACTIVE"
}
```

Response:
```json
{
  "id": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
  "firstName": "Ama",
  "lastName": "Mensah",
  "email": "ama@acme.com",
  "phone": "+1-555-9090",
  "jobTitle": "Senior IT Analyst",
  "status": "ACTIVE",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
  "departmentId": "f61f7d2f-2a3b-49fc-9a7c-4bb2df0919fd",
  "roleId": "8fe6e847-7e0e-4888-bec8-8d2d5dd1b3d7"
}
```

---

## 3) Audit Event APIs (New)

These APIs expose persisted API activity logs for compliance, tracing, and forensic analysis.

## 3.1 List Audit Events

`GET /api/v1/audit-events?actorId={uuid}&start={isoInstant}&end={isoInstant}&success={true|false}&method={HTTP_METHOD}`

All query params are optional.

Response `200 OK`:
```json
[
  {
    "id": "e8ea1b93-c718-44c9-b7f3-cf790e5b598d",
    "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
    "actorId": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
    "actorEmail": "ama@acme.com",
    "method": "PATCH",
    "path": "/api/v1/assets/c0e8e2a4-6d25-4c67-a620-c0222f6cb7bf",
    "query": null,
    "handler": "AssetController.patch",
    "responseStatus": 200,
    "success": true,
    "message": null,
    "requestId": "7b9890e8-c2cd-4753-a96b-09dc5fc6f369",
    "clientIp": "127.0.0.1",
    "userAgent": "Mozilla/5.0",
    "createdAt": "2026-03-06T14:21:33.111Z"
  }
]
```

## 3.2 Get Audit Event By ID

`GET /api/v1/audit-events/{id}`

Response `200 OK`:
```json
{
  "id": "e8ea1b93-c718-44c9-b7f3-cf790e5b598d",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
  "actorId": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
  "actorEmail": "ama@acme.com",
  "method": "PATCH",
  "path": "/api/v1/assets/c0e8e2a4-6d25-4c67-a620-c0222f6cb7bf",
  "query": null,
  "handler": "AssetController.patch",
  "responseStatus": 200,
  "success": true,
  "message": null,
  "requestId": "7b9890e8-c2cd-4753-a96b-09dc5fc6f369",
  "clientIp": "127.0.0.1",
  "userAgent": "Mozilla/5.0",
  "createdAt": "2026-03-06T14:21:33.111Z"
}
```

---

## 4) Common Error Responses

## 4.1 Validation / Input Error (`400`)
```json
{
  "status": 400,
  "message": "Malformed JSON request"
}
```

or

```json
{
  "status": 400,
  "errors": {
    "email": "Email must be valid"
  }
}
```

## 4.2 Forbidden (`403`)
```json
{
  "status": 403,
  "message": "Access denied"
}
```

## 4.3 Conflict (`409`)
```json
{
  "status": 409,
  "message": "A record with this value already exists"
}
```

---

## 5) Enum Notes For Frontend

- `POStatus` accepted values:
  - `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `DELIVERED`, `CANCELLED`
  - Compatibility alias accepted: `PENDING` (internally mapped to `SUBMITTED`)

Use canonical enum values in UI requests where possible.

---

## 6) Billing & Subscription APIs (New)

## 6.1 List Plans

`GET /api/v1/billing/plans`

Response `200 OK`:
```json
[
  {
    "code": "FREEMIUM",
    "name": "Freemium",
    "tier": "FREEMIUM",
    "interval": "MONTHLY",
    "amountMinor": 0,
    "currency": "NGN",
    "maxAssets": 50,
    "maxEmployees": 5,
    "analyticsEnabled": false,
    "auditRetentionDays": 7
  },
  {
    "code": "BASIC",
    "name": "Basic",
    "tier": "BASIC",
    "interval": "MONTHLY",
    "amountMinor": 2000000,
    "currency": "NGN",
    "maxAssets": 1000,
    "maxEmployees": 50,
    "analyticsEnabled": true,
    "auditRetentionDays": 90
  }
]
```

## 6.2 Get Current Subscription

`GET /api/v1/billing/subscription`

Response `200 OK`:
```json
{
  "id": "311cc749-6f22-4f24-bd57-4b4f5159dc95",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
  "plan": {
    "code": "FREEMIUM",
    "name": "Freemium",
    "tier": "FREEMIUM",
    "interval": "MONTHLY",
    "amountMinor": 0,
    "currency": "NGN",
    "maxAssets": 50,
    "maxEmployees": 5,
    "analyticsEnabled": false,
    "auditRetentionDays": 7
  },
  "status": "ACTIVE",
  "autoRenew": false,
  "currentPeriodStart": "2026-03-06T16:35:10.000Z",
  "currentPeriodEnd": "2027-03-06T16:35:10.000Z",
  "nextBillingAt": null,
  "currentAssetCount": 21,
  "currentEmployeeCount": 4
}
```

## 6.3 Initialize Checkout

`POST /api/v1/billing/checkout`

Request:
```json
{
  "planCode": "BASIC",
  "callbackUrl": "https://app.yourfrontend.com/billing/callback"
}
```

Response `201 Created`:
```json
{
  "authorizationUrl": "https://checkout.paystack.com/abcxyz",
  "accessCode": "ACCESS_xxxxxx",
  "reference": "BILL_94DC0C14_1772826075000"
}
```

## 6.4 Verify Checkout

`POST /api/v1/billing/checkout/verify?reference={reference}`

Response `200 OK`:
```json
{
  "id": "311cc749-6f22-4f24-bd57-4b4f5159dc95",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
  "plan": {
    "code": "BASIC",
    "name": "Basic",
    "tier": "BASIC",
    "interval": "MONTHLY",
    "amountMinor": 2000000,
    "currency": "NGN",
    "maxAssets": 1000,
    "maxEmployees": 50,
    "analyticsEnabled": true,
    "auditRetentionDays": 90
  },
  "status": "ACTIVE",
  "autoRenew": true,
  "currentPeriodStart": "2026-03-06T16:36:12.000Z",
  "currentPeriodEnd": "2026-04-05T16:36:12.000Z",
  "nextBillingAt": "2026-04-05T16:36:12.000Z",
  "currentAssetCount": 21,
  "currentEmployeeCount": 4
}
```

## 6.5 Toggle Auto-Renew

`PATCH /api/v1/billing/subscription/auto-renew`

Request:
```json
{
  "enabled": false
}
```

Response `200 OK`:
```json
{
  "id": "311cc749-6f22-4f24-bd57-4b4f5159dc95",
  "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
  "status": "ACTIVE",
  "autoRenew": false
}
```

## 6.6 Paystack Webhook (Backend-to-Backend)

`POST /api/v1/billing/webhooks/paystack`

Headers required from Paystack:
```http
x-paystack-signature: <sha512_signature>
Content-Type: application/json
```

Response `200 OK`:
```json
{}
```

---

## 7) Frontend Screens to Create

Create these screens and flows to support the new APIs and updates:

1. **Subscription & Billing Page**
- Show current plan, status, usage meters (`assets`, `employees`)
- Show available plans (`GET /billing/plans`)
- Upgrade CTA (initialize checkout)
- Verify payment result page/state (`/billing/callback` + verify endpoint)
- Auto-renew toggle control

2. **Checkout Redirect Callback Screen**
- Read `reference` from query string
- Call verify endpoint
- Show success/failure state + updated plan details

3. **Usage Limits Banner/Modal**
- Global warning when near plan limits
- Blocking modal on `403` plan-limit errors with “Upgrade Plan” action

4. **Asset Details / Edit Screen (Updated)**
- Add “Assign to Employee” action
- Add “Unassign Employee” action
- Keep PATCH-based partial save

5. **Audit Events List Screen**
- Table columns:
  - timestamp, actor, method, path, responseStatus, success, requestId
- Filters:
  - actorId, start, end, success, method
- Row click -> detail view

6. **Audit Event Detail Drawer/Page**
- Full payload display:
  - handler, query, IP, user-agent, error message (if any)

7. **All Edit Forms Converted to PATCH**
- Assets, Categories, Departments, Policies, Disposals, Locations, Maintenance,
  Organisations, Purchase Orders, Roles, Suppliers, Users
- Send changed fields only

8. **Analytics Access Guard**
- If analytics endpoint returns `403`, show paywall card:
  - “Advanced analytics available on paid plans”
  - CTA to Billing screen

---

## 8) Frontend Integration Sequence (Billing)

1. Load current subscription (`GET /billing/subscription`)
2. Load plans (`GET /billing/plans`)
3. User selects plan -> initialize checkout (`POST /billing/checkout`)
4. Redirect user to `authorizationUrl`
5. On callback, call verify (`POST /billing/checkout/verify`)
6. Refresh subscription UI
7. User can toggle auto-renew (`PATCH /billing/subscription/auto-renew`)

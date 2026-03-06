# API ENDPOINT TEST SUITE & ENTERPRISE SaaS FEATURES

**Date**: March 5, 2026  
**Status**: ✅ READY FOR TESTING  
**Version**: v1  

---

## 📋 TABLE OF CONTENTS

1. [API Endpoint Testing Guide](#api-endpoint-testing-guide)
2. [Enterprise SaaS Endpoints](#enterprise-saas-endpoints)
3. [Request/Response Examples](#requestresponse-examples)
4. [Error Handling Verification](#error-handling-verification)
5. [Testing Checklist](#testing-checklist)

---

## API ENDPOINT TESTING GUIDE

### Base URL
```
http://localhost:8085/api/v1
```

### Authentication
All endpoints (except login/register) require JWT token in header:
```
Authorization: Bearer {JWT_TOKEN}
```

---

## ✅ EXISTING ENDPOINTS - VERIFICATION

### 1. ASSET ENDPOINTS

#### 1.1 Create Asset
```
POST /api/v1/assets
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

Request Body:
{
  "name": "Dell XPS 13",
  "assetTag": "LAPTOP-001",
  "serialNumber": "SN123456",
  "categoryId": "{category_uuid}",
  "departmentId": "{dept_uuid}",
  "locationId": "{location_uuid}",
  "purchaseDate": "2026-01-15",
  "purchaseCost": 1500.00,
  "currency": "USD",
  "assetType": "COMPUTER",
  "manufacturer": "Dell",
  "model": "XPS 13",
  "usefulLifeMonths": 60,
  "condition": "GOOD",
  "status": "IN_STOCK"
}

Expected Response (201 CREATED):
{
  "id": "{asset_uuid}",
  "name": "Dell XPS 13",
  "assetTag": "LAPTOP-001",
  "status": "IN_STOCK",
  "organisationId": "{org_uuid}",
  "departmentId": "{dept_uuid}",
  ...
}

Error Cases:
- 400 Bad Request: Missing required fields (name, departmentId, categoryId)
- 409 Conflict: Asset with same name already exists in department
- 403 Forbidden: User lacks ROLE_ADMIN or ROLE_ORG_ADMIN
```

#### 1.2 Get Asset by ID
```
GET /api/v1/assets/{asset_id}
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
{
  "id": "{asset_uuid}",
  "name": "Dell XPS 13",
  ...full asset details...
}

Error Cases:
- 404 Not Found: Asset does not exist
- 403 Forbidden: User cannot access this organization's assets
```

#### 1.3 List Assets
```
GET /api/v1/assets
Authorization: Bearer {JWT_TOKEN}

Query Parameters (optional, mutually exclusive):
- ?status=IN_USE - Filter by status
- ?departmentId={dept_uuid} - Filter by department
- ?categoryId={cat_uuid} - Filter by category

Expected Response (200 OK):
[
  {
    "id": "{asset_uuid}",
    "name": "Dell XPS 13",
    "status": "IN_STOCK",
    ...
  },
  ...
]

Error Cases:
- 403 Forbidden: User lacks read permissions
```

#### 1.4 Update Asset
```
PUT /api/v1/assets/{asset_id}
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

Request Body (partial update):
{
  "name": "Dell XPS 13 Pro",
  "serialNumber": "SN123456-UPDATED"
}

Expected Response (200 OK):
{
  "id": "{asset_uuid}",
  "name": "Dell XPS 13 Pro",
  ...
}

Error Cases:
- 404 Not Found: Asset does not exist
- 403 Forbidden: User lacks ROLE_ADMIN
```

#### 1.5 Delete Asset (Soft Delete)
```
DELETE /api/v1/assets/{asset_id}
Authorization: Bearer {JWT_TOKEN}

Expected Response (204 NO CONTENT):
(empty body)

Error Cases:
- 404 Not Found: Asset does not exist
- 403 Forbidden: User lacks ROLE_ADMIN
```

#### 1.6 Assign Asset to Department
```
POST /api/v1/assets/{asset_id}/assign/{department_id}
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
{
  "id": "{asset_uuid}",
  "departmentId": "{new_dept_uuid}",
  "status": "IN_USE",
  ...
}

Error Cases:
- 404 Not Found: Asset or department not found
- 409 Conflict: Asset cannot be assigned in its current status
- 403 Forbidden: User lacks ROLE_ADMIN
```

---

### 2. PURCHASE ORDER ENDPOINTS

#### 2.1 Create Purchase Order
```
POST /api/v1/purchase-orders
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

Request Body:
{
  "poNumber": "PO-2026-001",
  "totalAmount": 5000.00,
  "currency": "USD",
  "departmentId": "{dept_uuid}",
  "supplierId": "{supplier_uuid}",
  "remarks": "Standard office supplies"
}

Expected Response (201 CREATED):
{
  "id": "{po_uuid}",
  "poNumber": "PO-2026-001",
  "totalAmount": 5000.00,
  "status": "DRAFT",
  "organisationId": "{org_uuid}",
  ...
}

Error Cases:
- 400 Bad Request: Missing required fields
- 404 Not Found: Department or supplier not found
- 403 Forbidden: User lacks ROLE_ADMIN
```

#### 2.2 Get Purchase Order
```
GET /api/v1/purchase-orders/{po_id}
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
{
  "id": "{po_uuid}",
  "poNumber": "PO-2026-001",
  "status": "DRAFT",
  ...
}

Error Cases:
- 404 Not Found: PO does not exist
```

#### 2.3 List Purchase Orders
```
GET /api/v1/purchase-orders
Authorization: Bearer {JWT_TOKEN}

Query Parameters (optional):
- ?departmentId={dept_uuid}
- ?supplierId={supplier_uuid}
- ?status=DRAFT|APPROVED|REJECTED

Expected Response (200 OK):
{
  "0": {...},
  "1": {...}
}
```

#### 2.4 Approve Purchase Order
```
POST /api/v1/purchase-orders/{po_id}/approve
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
{
  "id": "{po_uuid}",
  "status": "APPROVED",
  "approvedById": "{user_uuid}",
  "approvedAt": "2026-03-05T10:30:00Z",
  ...
}

Error Cases:
- 404 Not Found: PO does not exist
- 409 Conflict: PO already approved or is rejected
- 403 Forbidden: User lacks ROLE_ADMIN
```

#### 2.5 Reject Purchase Order
```
POST /api/v1/purchase-orders/{po_id}/reject
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
{
  "id": "{po_uuid}",
  "status": "REJECTED",
  ...
}

Error Cases:
- 409 Conflict: Cannot reject already-approved PO
```

#### 2.6 Delete Purchase Order (Soft Delete)
```
DELETE /api/v1/purchase-orders/{po_id}
Authorization: Bearer {JWT_TOKEN}

Expected Response (204 NO CONTENT):
(empty body)
```

---

### 3. SUPPLIER ENDPOINTS

#### 3.1 Create Supplier
```
POST /api/v1/suppliers
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

Request Body:
{
  "name": "Acme Corporation",
  "email": "supplier@acme.com",
  "phone": "+1-555-0123",
  "address": "123 Business Ave",
  "contactPerson": "John Doe",
  "registrationNumber": "REG123456",
  "taxId": "TAX123456",
  "bankDetails": "Account: 123456789"
}

Expected Response (201 CREATED):
{
  "id": "{supplier_uuid}",
  "name": "Acme Corporation",
  "email": "supplier@acme.com",
  ...
}

Error Cases:
- 400 Bad Request: Missing required fields
- 409 Conflict: Supplier with same name already exists
```

#### 3.2 Get Supplier
```
GET /api/v1/suppliers/{supplier_id}
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
{
  "id": "{supplier_uuid}",
  "name": "Acme Corporation",
  ...
}

Error Cases:
- 404 Not Found: Supplier does not exist
```

#### 3.3 List Suppliers
```
GET /api/v1/suppliers
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
[
  {
    "id": "{supplier_uuid}",
    "name": "Acme Corporation",
    ...
  },
  ...
]
```

#### 3.4 Update Supplier
```
PUT /api/v1/suppliers/{supplier_id}
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

Request Body (partial update):
{
  "phone": "+1-555-9999"
}

Expected Response (200 OK):
{
  "id": "{supplier_uuid}",
  "phone": "+1-555-9999",
  ...
}
```

#### 3.5 Delete Supplier (Soft Delete)
```
DELETE /api/v1/suppliers/{supplier_id}
Authorization: Bearer {JWT_TOKEN}

Expected Response (204 NO CONTENT):
(empty body)
```

---

## 🚀 ENTERPRISE SaaS ENDPOINTS (NEW)

These endpoints are necessary for a complete enterprise SaaS product.

### 1. DASHBOARD & ANALYTICS ENDPOINTS

#### 1.1 Get Dashboard Summary
```
GET /api/v1/dashboard/summary
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
{
  "totalAssets": 150,
  "assetsInUse": 125,
  "assetsInStock": 20,
  "assetsRetired": 5,
  "pendingPurchaseOrders": 8,
  "approvedPurchaseOrders": 15,
  "totalAssetValue": 750000.00,
  "totalPendingValue": 125000.00,
  "assetsNeedingMaintenance": 12,
  "deprecatedAssets": 3,
  "lastUpdated": "2026-03-05T10:30:00Z"
}

Purpose: Executive dashboard view of key metrics
Permissions: Requires ROLE_ORG_ADMIN or ROLE_ADMIN
```

#### 1.2 Get Asset Analytics
```
GET /api/v1/analytics/assets
Authorization: Bearer {JWT_TOKEN}

Query Parameters:
- ?period=month|quarter|year (default: month)
- ?groupBy=status|department|category|location

Expected Response (200 OK):
{
  "period": "month",
  "data": [
    {
      "name": "IN_USE",
      "count": 125,
      "value": 625000.00,
      "percentage": 83.33
    },
    {
      "name": "IN_STOCK",
      "count": 20,
      "value": 100000.00,
      "percentage": 13.33
    },
    ...
  ],
  "total": 150,
  "totalValue": 750000.00
}

Purpose: Detailed asset analytics and reporting
Permissions: Requires ROLE_ORG_ADMIN or ROLE_ADMIN
```

#### 1.3 Get Financial Analytics
```
GET /api/v1/analytics/financial
Authorization: Bearer {JWT_TOKEN}

Query Parameters:
- ?period=month|quarter|year|custom
- ?startDate=2026-01-01
- ?endDate=2026-03-05

Expected Response (200 OK):
{
  "period": "month",
  "totalDepreciation": 12500.00,
  "totalMaintenance": 3200.00,
  "totalDisposal": 500.00,
  "totalAcquisition": 25000.00,
  "assetTurnover": 0.15,
  "averageAssetAge": 24.5,
  "depreciationMethod": "STRAIGHT_LINE",
  "breakdown": {
    "byDepartment": [...],
    "byCategory": [...]
  }
}

Purpose: Financial analysis and cost tracking
Permissions: Requires ROLE_ORG_ADMIN or ROLE_ADMIN
```

---

### 2. REPORTING ENDPOINTS

#### 2.1 Generate Asset Report
```
POST /api/v1/reports/assets
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

Request Body:
{
  "format": "PDF|EXCEL|JSON",
  "includeDetails": true,
  "filters": {
    "status": "IN_USE",
    "departmentId": "{dept_uuid}",
    "dateRange": "2026-01-01 to 2026-03-05"
  },
  "columns": ["name", "assetTag", "status", "location", "value", "depreciation"]
}

Expected Response (200 OK):
{
  "reportId": "{report_uuid}",
  "format": "PDF",
  "downloadUrl": "/api/v1/reports/assets/{report_uuid}/download",
  "generatedAt": "2026-03-05T10:30:00Z",
  "rowCount": 150,
  "size": "2.5 MB"
}

Purpose: Generate downloadable reports
Permissions: Requires ROLE_ORG_ADMIN or ROLE_ADMIN
```

#### 2.2 Download Report
```
GET /api/v1/reports/{report_id}/download
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
(Binary file content - PDF/EXCEL)

Headers:
Content-Type: application/pdf (or application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)
Content-Disposition: attachment; filename="asset-report-2026-03-05.pdf"

Purpose: Download generated reports
```

#### 2.3 Get Report History
```
GET /api/v1/reports/history
Authorization: Bearer {JWT_TOKEN}

Query Parameters:
- ?type=assets|financial|depreciation
- ?limit=10
- ?offset=0

Expected Response (200 OK):
[
  {
    "reportId": "{report_uuid}",
    "type": "assets",
    "format": "PDF",
    "generatedAt": "2026-03-05T10:30:00Z",
    "generatedBy": "{user_email}",
    "rowCount": 150
  },
  ...
]

Purpose: Track generated reports
```

---

### 3. AUDIT & COMPLIANCE ENDPOINTS

#### 3.1 Get Audit Trail
```
GET /api/v1/audit/trail
Authorization: Bearer {JWT_TOKEN}

Query Parameters:
- ?entity=Asset|PurchaseOrder|Supplier
- ?action=CREATE|UPDATE|DELETE|APPROVE
- ?userId={user_uuid}
- ?startDate=2026-01-01
- ?endDate=2026-03-05
- ?limit=50

Expected Response (200 OK):
[
  {
    "id": "{audit_id}",
    "timestamp": "2026-03-05T10:30:00Z",
    "action": "CREATE",
    "entity": "Asset",
    "entityId": "{asset_uuid}",
    "userId": "{user_uuid}",
    "userEmail": "user@example.com",
    "changes": {
      "name": ["", "Dell XPS 13"],
      "status": ["", "IN_STOCK"]
    },
    "ipAddress": "192.168.1.1",
    "userAgent": "Mozilla/5.0..."
  },
  ...
]

Purpose: Track all changes for compliance
Permissions: Requires ROLE_ADMIN or ROLE_ORG_ADMIN
```

#### 3.2 Export Audit Log
```
POST /api/v1/audit/export
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

Request Body:
{
  "format": "CSV|EXCEL|JSON",
  "filters": {
    "startDate": "2026-01-01",
    "endDate": "2026-03-05",
    "entity": "Asset"
  }
}

Expected Response (200 OK):
{
  "exportId": "{export_uuid}",
  "format": "CSV",
  "downloadUrl": "/api/v1/audit/export/{export_uuid}/download",
  "generatedAt": "2026-03-05T10:30:00Z",
  "recordCount": 1250
}

Purpose: Export audit logs for compliance review
```

---

### 4. BULK OPERATIONS ENDPOINTS

#### 4.1 Bulk Import Assets
```
POST /api/v1/bulk/assets/import
Authorization: Bearer {JWT_TOKEN}
Content-Type: multipart/form-data

Form Data:
- file: (CSV or EXCEL file)
- dryRun: true|false (optional, default: false)

CSV Format:
name,assetTag,serialNumber,categoryId,departmentId,purchaseCost,currency
Dell XPS 13,LAPTOP-001,SN123456,{cat_uuid},{dept_uuid},1500.00,USD
...

Expected Response (200 OK):
{
  "jobId": "{import_job_uuid}",
  "status": "PROCESSING",
  "totalRows": 150,
  "successCount": 150,
  "errorCount": 0,
  "warnings": [],
  "startedAt": "2026-03-05T10:30:00Z",
  "completedAt": null,
  "downloadErrorReportUrl": null
}

Purpose: Bulk import assets from CSV/EXCEL
Permissions: Requires ROLE_ORG_ADMIN or ROLE_ADMIN
```

#### 4.2 Get Import Job Status
```
GET /api/v1/bulk/assets/import/{job_id}
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
{
  "jobId": "{import_job_uuid}",
  "status": "COMPLETED",
  "totalRows": 150,
  "successCount": 148,
  "errorCount": 2,
  "warnings": [
    {
      "rowNumber": 15,
      "message": "Invalid category ID"
    }
  ],
  "startedAt": "2026-03-05T10:30:00Z",
  "completedAt": "2026-03-05T10:35:00Z",
  "downloadErrorReportUrl": "/api/v1/bulk/assets/import/{job_id}/errors"
}

Purpose: Check import progress
```

#### 4.3 Get Import Error Report
```
GET /api/v1/bulk/assets/import/{job_id}/errors
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK - CSV):
rowNumber,name,error,suggestion
15,Dell XPS 13,Invalid category ID,Check category UUID

Purpose: Download detailed error report
```

#### 4.4 Bulk Export Assets
```
POST /api/v1/bulk/assets/export
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

Request Body:
{
  "format": "CSV|EXCEL|JSON",
  "filters": {
    "status": "IN_USE",
    "departmentId": "{dept_uuid}"
  },
  "columns": ["name", "assetTag", "status", "location", "value"]
}

Expected Response (200 OK):
{
  "jobId": "{export_job_uuid}",
  "status": "PROCESSING",
  "format": "CSV",
  "downloadUrl": "/api/v1/bulk/assets/export/{job_uuid}/download",
  "startedAt": "2026-03-05T10:30:00Z",
  "estimatedRows": 125
}

Purpose: Export assets in bulk
```

---

### 5. WEBHOOK ENDPOINTS

#### 5.1 Create Webhook
```
POST /api/v1/webhooks
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

Request Body:
{
  "name": "Asset Status Change",
  "url": "https://yourapp.com/webhooks/asset-status",
  "events": ["asset.created", "asset.updated", "asset.deleted"],
  "active": true,
  "secret": "webhook_secret_key"
}

Expected Response (201 CREATED):
{
  "id": "{webhook_uuid}",
  "name": "Asset Status Change",
  "url": "https://yourapp.com/webhooks/asset-status",
  "events": ["asset.created", "asset.updated", "asset.deleted"],
  "active": true,
  "createdAt": "2026-03-05T10:30:00Z",
  "lastTriggeredAt": null,
  "deliveryCount": 0
}

Purpose: Enable real-time notifications for external systems
```

#### 5.2 List Webhooks
```
GET /api/v1/webhooks
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
[
  {
    "id": "{webhook_uuid}",
    "name": "Asset Status Change",
    "events": ["asset.created", "asset.updated"],
    "active": true,
    "deliveryCount": 42,
    "lastTriggeredAt": "2026-03-05T09:15:00Z"
  },
  ...
]

Purpose: List configured webhooks
```

#### 5.3 Get Webhook Delivery History
```
GET /api/v1/webhooks/{webhook_id}/deliveries
Authorization: Bearer {JWT_TOKEN}

Query Parameters:
- ?status=success|failed|pending
- ?limit=50

Expected Response (200 OK):
[
  {
    "deliveryId": "{delivery_uuid}",
    "timestamp": "2026-03-05T09:15:00Z",
    "event": "asset.created",
    "status": "success",
    "statusCode": 200,
    "responseTime": 245,
    "attempts": 1
  },
  ...
]

Purpose: Track webhook delivery status
```

---

### 6. INTEGRATION ENDPOINTS

#### 6.1 OAuth2 Configuration
```
POST /api/v1/integrations/oauth2
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

Request Body:
{
  "provider": "google|microsoft|okta",
  "clientId": "your-client-id",
  "clientSecret": "your-client-secret",
  "redirectUri": "https://yourapp.com/auth/callback",
  "enabled": true
}

Expected Response (201 CREATED):
{
  "integrationId": "{integration_uuid}",
  "provider": "google",
  "enabled": true,
  "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?...",
  "scopesRequired": ["email", "profile"]
}

Purpose: Configure SSO integrations
```

#### 6.2 List Active Integrations
```
GET /api/v1/integrations
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
[
  {
    "integrationId": "{integration_uuid}",
    "provider": "google",
    "type": "oauth2",
    "enabled": true,
    "connectedUsers": 45,
    "lastSync": "2026-03-05T10:00:00Z"
  },
  ...
]

Purpose: View all configured integrations
```

---

### 7. MULTI-TENANCY MANAGEMENT ENDPOINTS

#### 7.1 Create Sub-Organization
```
POST /api/v1/organisations/{parent_org_id}/sub-organisations
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

Request Body:
{
  "name": "Branch Office - New York",
  "registrationNumber": "REG-NY-2026",
  "country": "USA",
  "industry": "Technology",
  "timezone": "America/New_York"
}

Expected Response (201 CREATED):
{
  "id": "{sub_org_uuid}",
  "name": "Branch Office - New York",
  "parentOrganisationId": "{parent_org_id}",
  "status": "ACTIVE",
  "createdAt": "2026-03-05T10:30:00Z"
}

Purpose: Enable multi-location enterprise structures
```

#### 7.2 Get Organization Hierarchy
```
GET /api/v1/organisations/{org_id}/hierarchy
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
{
  "id": "{org_uuid}",
  "name": "Parent Company",
  "children": [
    {
      "id": "{sub_org_uuid}",
      "name": "Branch Office - New York",
      "children": [],
      "assetCount": 45,
      "userCount": 12
    },
    {
      "id": "{sub_org_uuid_2}",
      "name": "Branch Office - Los Angeles",
      "children": [],
      "assetCount": 38,
      "userCount": 10
    }
  ]
}

Purpose: Understand organizational structure
```

---

### 8. ADVANCED SEARCH & FILTERING

#### 8.1 Advanced Asset Search
```
POST /api/v1/search/assets
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

Request Body:
{
  "query": "Dell XPS",
  "filters": {
    "status": ["IN_USE", "IN_STOCK"],
    "condition": ["GOOD", "EXCELLENT"],
    "location": ["{location_uuid}"],
    "department": ["{dept_uuid}"],
    "valueRange": {
      "min": 500,
      "max": 5000
    },
    "ageRange": {
      "minMonths": 0,
      "maxMonths": 36
    }
  },
  "sortBy": "value",
  "sortDirection": "DESC",
  "limit": 50,
  "offset": 0
}

Expected Response (200 OK):
{
  "totalResults": 145,
  "page": 1,
  "pageSize": 50,
  "results": [
    {
      "id": "{asset_uuid}",
      "name": "Dell XPS 13",
      "score": 0.95,
      "relevance": "exact match"
    },
    ...
  ],
  "facets": {
    "status": {
      "IN_USE": 120,
      "IN_STOCK": 25
    },
    "condition": {
      "GOOD": 100,
      "EXCELLENT": 45
    }
  }
}

Purpose: Advanced search with faceted filtering
```

---

### 9. NOTIFICATION ENDPOINTS

#### 9.1 Get Notifications
```
GET /api/v1/notifications
Authorization: Bearer {JWT_TOKEN}

Query Parameters:
- ?type=deprecation|maintenance|approval|system
- ?status=unread|read|all
- ?limit=20

Expected Response (200 OK):
[
  {
    "notificationId": "{notif_uuid}",
    "type": "deprecation",
    "title": "Asset Depreciation Notice",
    "message": "Dell XPS 13 has completed its useful life",
    "entityId": "{asset_uuid}",
    "createdAt": "2026-03-05T10:30:00Z",
    "read": false,
    "actionUrl": "/api/v1/assets/{asset_uuid}"
  },
  ...
]

Purpose: Retrieve user notifications
```

#### 9.2 Mark Notification as Read
```
PATCH /api/v1/notifications/{notification_id}/read
Authorization: Bearer {JWT_TOKEN}

Expected Response (200 OK):
{
  "notificationId": "{notif_uuid}",
  "read": true,
  "readAt": "2026-03-05T10:35:00Z"
}

Purpose: Mark notifications as read
```

---

### 10. SYSTEM HEALTH & MONITORING

#### 10.1 Get System Health
```
GET /api/v1/health
Authorization: Bearer {JWT_TOKEN} (optional)

Expected Response (200 OK):
{
  "status": "UP",
  "timestamp": "2026-03-05T10:30:00Z",
  "components": {
    "database": {
      "status": "UP",
      "responseTime": 15
    },
    "cache": {
      "status": "UP",
      "responseTime": 5
    },
    "storage": {
      "status": "UP",
      "available": "500GB"
    }
  }
}

Purpose: Monitor system health
```

#### 10.2 Get API Metrics
```
GET /api/v1/metrics
Authorization: Bearer {JWT_TOKEN}

Query Parameters:
- ?period=hour|day|week
- ?metric=requests|errors|latency

Expected Response (200 OK):
{
  "period": "day",
  "totalRequests": 15420,
  "successfulRequests": 15350,
  "failedRequests": 70,
  "averageLatency": 145,
  "p95Latency": 450,
  "p99Latency": 890,
  "errorRate": 0.45,
  "topErrors": [
    {
      "error": "404 Not Found",
      "count": 35
    },
    {
      "error": "403 Forbidden",
      "count": 25
    }
  ]
}

Purpose: Monitor API performance
```

---

## REQUEST/RESPONSE EXAMPLES

### Example 1: Complete Asset Lifecycle

```
# 1. Create Asset
POST /api/v1/assets
{
  "name": "MacBook Pro",
  "categoryId": "{cat_uuid}",
  "departmentId": "{dept_uuid}",
  "purchaseCost": 2500.00,
  "status": "IN_STOCK"
}
Response: 201 Created → {asset_uuid}

# 2. Assign to User
POST /api/v1/assets/{asset_uuid}/assign/{dept_uuid}
Response: 200 OK → {"status": "IN_USE", ...}

# 3. Create Asset Report
POST /api/v1/reports/assets
{
  "format": "PDF",
  "filters": {"departmentId": "{dept_uuid}"}
}
Response: 200 OK → {reportId, downloadUrl, ...}

# 4. Get Audit Trail
GET /api/v1/audit/trail?entity=Asset&entityId={asset_uuid}
Response: 200 OK → [audit_entry, ...]

# 5. Deprecate Asset
DELETE /api/v1/assets/{asset_uuid}
Response: 204 No Content
```

---

## ERROR HANDLING VERIFICATION

### All endpoints return standardized error responses:

```json
{
  "status": 400,
  "message": "Descriptive error message",
  "timestamp": "2026-03-05T10:30:00Z",
  "path": "/api/v1/assets",
  "errors": {
    "field1": "Field1 is required",
    "field2": "Field2 must be positive"
  }
}
```

### Common HTTP Status Codes:
- `200 OK` - Success
- `201 Created` - Resource created
- `204 No Content` - Success (no response body)
- `400 Bad Request` - Invalid input
- `401 Unauthorized` - Missing JWT token
- `403 Forbidden` - Insufficient permissions
- `404 Not Found` - Resource not found
- `409 Conflict` - State conflict (e.g., duplicate)
- `422 Unprocessable Entity` - Validation error
- `429 Too Many Requests` - Rate limited
- `500 Internal Server Error` - Server error

---

## TESTING CHECKLIST

### Pre-Testing Setup
- [ ] Start application: `java -jar demo.jar`
- [ ] Verify database is running
- [ ] Obtain JWT token for testing
- [ ] Set up Postman/Insomnia with base URL: http://localhost:8085/api/v1

### Asset Endpoints Testing
- [ ] POST /assets - Create new asset
- [ ] GET /assets/{id} - Retrieve specific asset
- [ ] GET /assets - List all assets
- [ ] GET /assets?status=IN_USE - Filter by status
- [ ] PUT /assets/{id} - Update asset
- [ ] DELETE /assets/{id} - Delete (soft delete) asset
- [ ] POST /assets/{id}/assign/{dept_id} - Assign asset

### Purchase Order Endpoints Testing
- [ ] POST /purchase-orders - Create PO
- [ ] GET /purchase-orders/{id} - Get PO
- [ ] GET /purchase-orders - List POs
- [ ] POST /purchase-orders/{id}/approve - Approve PO
- [ ] POST /purchase-orders/{id}/reject - Reject PO
- [ ] DELETE /purchase-orders/{id} - Delete PO

### Supplier Endpoints Testing
- [ ] POST /suppliers - Create supplier
- [ ] GET /suppliers/{id} - Get supplier
- [ ] GET /suppliers - List suppliers
- [ ] PUT /suppliers/{id} - Update supplier
- [ ] DELETE /suppliers/{id} - Delete supplier

### Enterprise Endpoints Testing (NEW)
- [ ] GET /dashboard/summary - Dashboard metrics
- [ ] GET /analytics/assets - Asset analytics
- [ ] POST /reports/assets - Generate report
- [ ] GET /audit/trail - Audit log
- [ ] POST /bulk/assets/import - Bulk import
- [ ] POST /webhooks - Create webhook
- [ ] GET /notifications - Get notifications
- [ ] GET /health - System health

### Error Handling Testing
- [ ] Invalid JWT token → 401 Unauthorized
- [ ] Missing required field → 400 Bad Request
- [ ] Insufficient permissions → 403 Forbidden
- [ ] Resource not found → 404 Not Found
- [ ] Duplicate asset → 409 Conflict
- [ ] Invalid state transition → 409 Conflict

### Load Testing
- [ ] Create 100 assets in sequence
- [ ] List 100+ assets
- [ ] Search with complex filters
- [ ] Generate large report (1000+ records)
- [ ] Monitor response times

---

**Status**: Ready for comprehensive testing  
**Date**: March 5, 2026  
**Next Steps**: Implement all NEW enterprise endpoints


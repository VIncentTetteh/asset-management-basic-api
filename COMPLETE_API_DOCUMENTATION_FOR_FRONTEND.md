# 📖 COMPLETE API DOCUMENTATION FOR FRONTEND INTEGRATION

**Generated**: March 5, 2026  
**API Version**: v1  
**Base URL**: `http://localhost:8085/api/v1`  
**Status**: Production Ready ✅  

---

## 📑 TABLE OF CONTENTS

1. [Authentication](#authentication)
2. [Asset Endpoints](#asset-endpoints)
3. [Purchase Order Endpoints](#purchase-order-endpoints)
4. [Supplier Endpoints](#supplier-endpoints)
5. [Dashboard Endpoints](#dashboard-endpoints)
6. [Analytics Endpoints](#analytics-endpoints)
7. [Reports Endpoints](#reports-endpoints)
8. [Bulk Operations Endpoints](#bulk-operations-endpoints)
9. [Webhooks Endpoints](#webhooks-endpoints)
10. [Notifications Endpoints](#notifications-endpoints)
11. [Health & Monitoring Endpoints](#health--monitoring-endpoints)
12. [Error Responses](#error-responses)
13. [Status Codes](#status-codes)

---

## 🔐 AUTHENTICATION

All endpoints (except `/auth/login`) require JWT token in header:

```
Authorization: Bearer {JWT_TOKEN}
```

### Login Endpoint

**Endpoint**: `POST /auth/login`  
**Authentication**: Not required  
**Rate Limit**: 5 requests per 5 minutes  

**Request Body**:
```json
{
  "email": "user@example.com",
  "password": "your_password"
}
```

**Response** (200 OK):
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "tokenType": "Bearer",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "role": "ROLE_ORG_ADMIN",
    "organisationId": "550e8400-e29b-41d4-a716-446655440001"
  }
}
```

**Error** (401 Unauthorized):
```json
{
  "status": 401,
  "message": "Invalid email or password",
  "timestamp": "2026-03-05T10:30:00Z"
}
```

### Refresh Token

**Endpoint**: `POST /auth/refresh`  
**Authentication**: Required  

**Request Body**: Empty (token in header)

**Response** (200 OK):
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "tokenType": "Bearer"
}
```

---

## 📦 ASSET ENDPOINTS

### Create Asset

**Endpoint**: `POST /assets`  
**Authentication**: Required (ROLE_ADMIN, ROLE_ORG_ADMIN)  

**Request Body**:
```json
{
  "name": "Dell XPS 13",
  "assetTag": "LAPTOP-001",
  "assetType": "COMPUTER",
  "serialNumber": "SN123456789",
  "barcode": "123456789",
  "barcodeQrCode": "data:image/png;base64,...",
  "categoryId": "550e8400-e29b-41d4-a716-446655440002",
  "departmentId": "550e8400-e29b-41d4-a716-446655440003",
  "locationId": "550e8400-e29b-41d4-a716-446655440004",
  "supplierId": "550e8400-e29b-41d4-a716-446655440005",
  "purchaseOrderId": "550e8400-e29b-41d4-a716-446655440006",
  "assignedUserId": "550e8400-e29b-41d4-a716-446655440007",
  "manufacturer": "Dell",
  "model": "XPS 13",
  "description": "High-performance laptop",
  "condition": "GOOD",
  "status": "IN_STOCK",
  "purchaseDate": "2026-01-15",
  "purchaseCost": 1500.00,
  "currency": "USD",
  "depreciationMethod": "STRAIGHT_LINE",
  "usefulLifeMonths": 60,
  "salvageValue": 300.00,
  "currentBookValue": 1500.00,
  "residualValue": 300.00,
  "warrantyExpiryDate": "2027-01-15",
  "insurancePolicyId": "INS123456",
  "invoiceId": "INV123456"
}
```

**Response** (201 Created):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440008",
  "name": "Dell XPS 13",
  "assetTag": "LAPTOP-001",
  "assetType": "COMPUTER",
  "serialNumber": "SN123456789",
  "categoryId": "550e8400-e29b-41d4-a716-446655440002",
  "departmentId": "550e8400-e29b-41d4-a716-446655440003",
  "locationId": "550e8400-e29b-41d4-a716-446655440004",
  "status": "IN_STOCK",
  "condition": "GOOD",
  "purchaseCost": 1500.00,
  "currentBookValue": 1500.00,
  "organisationId": "550e8400-e29b-41d4-a716-446655440001",
  "createdAt": "2026-03-05T10:30:00Z",
  "updatedAt": "2026-03-05T10:30:00Z",
  "createdBy": "550e8400-e29b-41d4-a716-446655440007"
}
```

**Error** (400 Bad Request):
```json
{
  "status": 400,
  "errors": {
    "name": "Asset name is required",
    "categoryId": "Category is required",
    "departmentId": "Department is required"
  }
}
```

### Get Asset

**Endpoint**: `GET /assets/{assetId}`  
**Authentication**: Required (ROLE_USER, ROLE_ORG_ADMIN, ROLE_ADMIN)  

**Response** (200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440008",
  "name": "Dell XPS 13",
  "assetTag": "LAPTOP-001",
  "assetType": "COMPUTER",
  "serialNumber": "SN123456789",
  "categoryId": "550e8400-e29b-41d4-a716-446655440002",
  "departmentId": "550e8400-e29b-41d4-a716-446655440003",
  "locationId": "550e8400-e29b-41d4-a716-446655440004",
  "status": "IN_STOCK",
  "condition": "GOOD",
  "manufacturer": "Dell",
  "model": "XPS 13",
  "purchaseCost": 1500.00,
  "currency": "USD",
  "currentBookValue": 1500.00",
  "residualValue": 300.00,
  "usefulLifeMonths": 60,
  "warrantyExpiryDate": "2027-01-15",
  "organisationId": "550e8400-e29b-41d4-a716-446655440001",
  "createdAt": "2026-03-05T10:30:00Z",
  "updatedAt": "2026-03-05T10:30:00Z"
}
```

### List Assets

**Endpoint**: `GET /assets`  
**Authentication**: Required  
**Query Parameters**:
- `status`: Filter by status (IN_STOCK, IN_USE, RETIRED)
- `departmentId`: Filter by department UUID
- `categoryId`: Filter by category UUID
- `page`: Page number (default: 0)
- `size`: Page size (default: 20)

**Response** (200 OK):
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440008",
    "name": "Dell XPS 13",
    "assetTag": "LAPTOP-001",
    "status": "IN_STOCK",
    "condition": "GOOD",
    "currentBookValue": 1500.00,
    "organisationId": "550e8400-e29b-41d4-a716-446655440001",
    "createdAt": "2026-03-05T10:30:00Z"
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440009",
    "name": "MacBook Pro",
    "assetTag": "LAPTOP-002",
    "status": "IN_USE",
    "condition": "EXCELLENT",
    "currentBookValue": 2500.00,
    "organisationId": "550e8400-e29b-41d4-a716-446655440001",
    "createdAt": "2026-03-04T15:45:00Z"
  }
]
```

### Update Asset

**Endpoint**: `PUT /assets/{assetId}`  
**Authentication**: Required (ROLE_ADMIN, ROLE_ORG_ADMIN)  

**Request Body** (partial update):
```json
{
  "name": "Dell XPS 13 Pro",
  "condition": "EXCELLENT",
  "currentBookValue": 1400.00
}
```

**Response** (200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440008",
  "name": "Dell XPS 13 Pro",
  "assetTag": "LAPTOP-001",
  "condition": "EXCELLENT",
  "currentBookValue": 1400.00,
  "updatedAt": "2026-03-05T10:35:00Z"
}
```

### Delete Asset

**Endpoint**: `DELETE /assets/{assetId}`  
**Authentication**: Required (ROLE_ADMIN)  

**Response** (204 No Content): Empty body

### Assign Asset to Department

**Endpoint**: `POST /assets/{assetId}/assign/{departmentId}`  
**Authentication**: Required (ROLE_ADMIN)  

**Response** (200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440008",
  "departmentId": "550e8400-e29b-41d4-a716-446655440010",
  "status": "IN_USE",
  "updatedAt": "2026-03-05T10:40:00Z"
}
```

---

## 💳 PURCHASE ORDER ENDPOINTS

### Create Purchase Order

**Endpoint**: `POST /purchase-orders`  
**Authentication**: Required (ROLE_ADMIN, ROLE_ORG_ADMIN)  

**Request Body**:
```json
{
  "poNumber": "PO-2026-001",
  "totalAmount": 5000.00,
  "currency": "USD",
  "departmentId": "550e8400-e29b-41d4-a716-446655440003",
  "supplierId": "550e8400-e29b-41d4-a716-446655440011",
  "remarks": "Standard office supplies",
  "status": "DRAFT"
}
```

**Response** (201 Created):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440012",
  "poNumber": "PO-2026-001",
  "totalAmount": 5000.00,
  "currency": "USD",
  "departmentId": "550e8400-e29b-41d4-a716-446655440003",
  "supplierId": "550e8400-e29b-41d4-a716-446655440011",
  "status": "DRAFT",
  "organisationId": "550e8400-e29b-41d4-a716-446655440001",
  "createdAt": "2026-03-05T10:30:00Z",
  "createdBy": "550e8400-e29b-41d4-a716-446655440007"
}
```

### Get Purchase Order

**Endpoint**: `GET /purchase-orders/{poId}`  
**Authentication**: Required  

**Response** (200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440012",
  "poNumber": "PO-2026-001",
  "totalAmount": 5000.00,
  "currency": "USD",
  "departmentId": "550e8400-e29b-41d4-a716-446655440003",
  "supplierId": "550e8400-e29b-41d4-a716-446655440011",
  "status": "DRAFT",
  "remarks": "Standard office supplies",
  "createdAt": "2026-03-05T10:30:00Z",
  "updatedAt": "2026-03-05T10:30:00Z"
}
```

### List Purchase Orders

**Endpoint**: `GET /purchase-orders`  
**Authentication**: Required  
**Query Parameters**:
- `departmentId`: Filter by department
- `supplierId`: Filter by supplier
- `status`: Filter by status (DRAFT, APPROVED, REJECTED)

**Response** (200 OK):
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440012",
    "poNumber": "PO-2026-001",
    "totalAmount": 5000.00,
    "status": "DRAFT",
    "createdAt": "2026-03-05T10:30:00Z"
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440013",
    "poNumber": "PO-2026-002",
    "totalAmount": 3500.00,
    "status": "APPROVED",
    "createdAt": "2026-03-04T15:45:00Z"
  }
]
```

### Approve Purchase Order

**Endpoint**: `POST /purchase-orders/{poId}/approve`  
**Authentication**: Required (ROLE_ADMIN)  

**Response** (200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440012",
  "poNumber": "PO-2026-001",
  "status": "APPROVED",
  "approvedById": "550e8400-e29b-41d4-a716-446655440007",
  "approvedAt": "2026-03-05T10:35:00Z"
}
```

### Reject Purchase Order

**Endpoint**: `POST /purchase-orders/{poId}/reject`  
**Authentication**: Required (ROLE_ADMIN)  

**Response** (200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440012",
  "poNumber": "PO-2026-001",
  "status": "REJECTED",
  "updatedAt": "2026-03-05T10:35:00Z"
}
```

---

## 🏪 SUPPLIER ENDPOINTS

### Create Supplier

**Endpoint**: `POST /suppliers`  
**Authentication**: Required (ROLE_ADMIN, ROLE_ORG_ADMIN)  

**Request Body**:
```json
{
  "name": "Acme Corporation",
  "email": "supplier@acme.com",
  "phone": "+1-555-0123",
  "address": "123 Business Ave, Suite 100",
  "contactPerson": "John Smith",
  "registrationNumber": "REG123456",
  "taxId": "TAX123456",
  "bankDetails": "Account: 123456789, Routing: 123456"
}
```

**Response** (201 Created):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440011",
  "name": "Acme Corporation",
  "email": "supplier@acme.com",
  "phone": "+1-555-0123",
  "address": "123 Business Ave, Suite 100",
  "contactPerson": "John Smith",
  "status": "ACTIVE",
  "organisationId": "550e8400-e29b-41d4-a716-446655440001",
  "createdAt": "2026-03-05T10:30:00Z"
}
```

### Get Supplier

**Endpoint**: `GET /suppliers/{supplierId}`  
**Authentication**: Required  

**Response** (200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440011",
  "name": "Acme Corporation",
  "email": "supplier@acme.com",
  "phone": "+1-555-0123",
  "address": "123 Business Ave, Suite 100",
  "contactPerson": "John Smith",
  "registrationNumber": "REG123456",
  "taxId": "TAX123456",
  "status": "ACTIVE",
  "createdAt": "2026-03-05T10:30:00Z",
  "updatedAt": "2026-03-05T10:30:00Z"
}
```

### List Suppliers

**Endpoint**: `GET /suppliers`  
**Authentication**: Required  

**Response** (200 OK):
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440011",
    "name": "Acme Corporation",
    "email": "supplier@acme.com",
    "phone": "+1-555-0123",
    "status": "ACTIVE",
    "createdAt": "2026-03-05T10:30:00Z"
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440014",
    "name": "Tech Supplies Inc",
    "email": "info@techsupplies.com",
    "phone": "+1-555-0456",
    "status": "ACTIVE",
    "createdAt": "2026-03-04T15:45:00Z"
  }
]
```

### Update Supplier

**Endpoint**: `PUT /suppliers/{supplierId}`  
**Authentication**: Required (ROLE_ADMIN, ROLE_ORG_ADMIN)  

**Request Body** (partial update):
```json
{
  "phone": "+1-555-9999",
  "email": "newemail@acme.com"
}
```

**Response** (200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440011",
  "name": "Acme Corporation",
  "phone": "+1-555-9999",
  "email": "newemail@acme.com",
  "updatedAt": "2026-03-05T10:35:00Z"
}
```

### Delete Supplier

**Endpoint**: `DELETE /suppliers/{supplierId}`  
**Authentication**: Required (ROLE_ADMIN)  

**Response** (204 No Content): Empty body

---

## 📊 DASHBOARD ENDPOINTS

### Get Dashboard Summary

**Endpoint**: `GET /dashboard/summary`  
**Authentication**: Required (ROLE_ORG_ADMIN, ROLE_ADMIN)  

**Response** (200 OK):
```json
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
  "lastUpdated": "2026-03-05T10:30:00Z",
  "totalDepreciation": 125000.00,
  "maintenanceAlerts": 12
}
```

### Get Assets by Status

**Endpoint**: `GET /dashboard/assets-by-status`  
**Authentication**: Required  

**Response** (200 OK):
```json
{
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
    {
      "name": "RETIRED",
      "count": 5,
      "value": 25000.00,
      "percentage": 3.33
    }
  ],
  "total": 150,
  "totalValue": 750000.00
}
```

### Get Maintenance Alerts

**Endpoint**: `GET /dashboard/maintenance-alerts`  
**Authentication**: Required (ROLE_ORG_ADMIN, ROLE_ADMIN)  

**Response** (200 OK):
```json
{
  "alertCount": 12,
  "criticalCount": 3,
  "warningCount": 4,
  "scheduledCount": 5,
  "alerts": [
    {
      "message": "Asset needs scheduled maintenance",
      "assetName": "Dell XPS 13",
      "severity": "SCHEDULED",
      "dueDate": "2026-03-10"
    },
    {
      "message": "Asset warranty expiring",
      "assetName": "MacBook Pro",
      "severity": "WARNING",
      "dueDate": "2026-03-20"
    }
  ]
}
```

---

## 📈 ANALYTICS ENDPOINTS

### Get Asset Analytics

**Endpoint**: `GET /analytics/assets`  
**Authentication**: Required  
**Query Parameters**:
- `period`: month|quarter|year (default: month)
- `groupBy`: status|department|category|condition

**Response** (200 OK):
```json
{
  "period": "month",
  "groupBy": "status",
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
    {
      "name": "RETIRED",
      "count": 5,
      "value": 25000.00,
      "percentage": 3.33
    }
  ],
  "total": 150,
  "totalValue": 750000.00
}
```

### Get Financial Analytics

**Endpoint**: `GET /analytics/financial`  
**Authentication**: Required  
**Query Parameters**:
- `period`: month|quarter|year

**Response** (200 OK):
```json
{
  "period": "month",
  "totalAssetValue": 750000.00,
  "totalDepreciation": 125000.00,
  "netBookValue": 625000.00,
  "totalAcquisition": 25000.00,
  "totalDisposal": 500.00,
  "totalMaintenance": 3200.00,
  "assetTurnover": 0.15,
  "averageAssetAge": 24.5,
  "depreciationMethod": "STRAIGHT_LINE",
  "assetsFullyDepreciated": 3,
  "monthlyDepreciation": 10416.67,
  "breakdown": {
    "byCategory": {
      "IT Equipment": {
        "count": 45,
        "value": 225000.00,
        "monthlyDepreciation": 37500.00
      }
    }
  }
}
```

### Get Purchase Order Analytics

**Endpoint**: `GET /analytics/purchase-orders`  
**Authentication**: Required  
**Query Parameters**:
- `period`: month|quarter|year

**Response** (200 OK):
```json
{
  "period": "month",
  "totalPOs": 50,
  "draftPOs": 8,
  "approvedPOs": 35,
  "rejectedPOs": 7,
  "totalPOValue": 425000.00,
  "averagePOValue": 8500.00,
  "largestPO": 35000.00,
  "smallestPO": 250.00,
  "averageApprovalTime": 2.5,
  "averageDeliveryTime": 14,
  "topSuppliers": [
    {
      "supplier": "Acme Corp",
      "poCount": 12,
      "totalValue": 85000.00
    }
  ]
}
```

---

## 📄 REPORTS ENDPOINTS

### Generate Asset Report

**Endpoint**: `POST /reports/assets`  
**Authentication**: Required (ROLE_ORG_ADMIN, ROLE_ADMIN)  

**Request Body**:
```json
{
  "format": "PDF",
  "includeDetails": true,
  "filters": {
    "status": "IN_USE",
    "departmentId": "550e8400-e29b-41d4-a716-446655440003",
    "dateRange": "2026-01-01 to 2026-03-05"
  },
  "columns": ["name", "assetTag", "status", "location", "value", "depreciation"]
}
```

**Response** (201 Created):
```json
{
  "reportId": "550e8400-e29b-41d4-a716-446655440015",
  "format": "PDF",
  "status": "COMPLETED",
  "downloadUrl": "/api/v1/reports/550e8400-e29b-41d4-a716-446655440015/download",
  "generatedAt": "2026-03-05T10:30:00Z",
  "rowCount": 150,
  "size": "2.5 MB"
}
```

### Download Report

**Endpoint**: `GET /reports/{reportId}/download`  
**Authentication**: Required  

**Response** (200 OK):
- Content-Type: application/pdf (or application/vnd.ms-excel for EXCEL)
- File download

### Get Report History

**Endpoint**: `GET /reports/history`  
**Authentication**: Required  
**Query Parameters**:
- `type`: assets|financial|depreciation
- `limit`: 10 (default)
- `offset`: 0 (default)

**Response** (200 OK):
```json
{
  "totalReports": 4,
  "limit": 10,
  "offset": 0,
  "reports": [
    {
      "reportId": "550e8400-e29b-41d4-a716-446655440015",
      "type": "assets",
      "format": "PDF",
      "generatedAt": "2026-03-05T10:30:00Z",
      "generatedBy": "user@example.com",
      "rowCount": 150,
      "downloadUrl": "/api/v1/reports/550e8400-e29b-41d4-a716-446655440015/download"
    }
  ]
}
```

---

## 📦 BULK OPERATIONS ENDPOINTS

### Bulk Import Assets

**Endpoint**: `POST /bulk/assets/import`  
**Authentication**: Required (ROLE_ORG_ADMIN, ROLE_ADMIN)  
**Content-Type**: multipart/form-data  

**Request**:
- `file`: CSV or EXCEL file
- `dryRun`: true|false (optional)

**CSV Format**:
```
name,assetTag,serialNumber,categoryId,departmentId,purchaseCost,currency
Dell XPS 13,LAPTOP-001,SN123456,550e8400-e29b-41d4-a716-446655440002,550e8400-e29b-41d4-a716-446655440003,1500.00,USD
MacBook Pro,LAPTOP-002,SN234567,550e8400-e29b-41d4-a716-446655440002,550e8400-e29b-41d4-a716-446655440003,2500.00,USD
```

**Response** (202 Accepted):
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440016",
  "status": "PROCESSING",
  "totalRows": 150,
  "successCount": 150,
  "errorCount": 0,
  "warnings": [],
  "startedAt": "2026-03-05T10:30:00Z",
  "completedAt": null,
  "downloadErrorReportUrl": null
}
```

### Get Import Job Status

**Endpoint**: `GET /bulk/assets/import/{jobId}`  
**Authentication**: Required  

**Response** (200 OK):
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440016",
  "status": "COMPLETED",
  "totalRows": 150,
  "successCount": 148,
  "errorCount": 2,
  "warnings": [
    {
      "rowNumber": 15,
      "message": "Invalid category ID"
    },
    {
      "rowNumber": 87,
      "message": "Department not found"
    }
  ],
  "startedAt": "2026-03-05T10:30:00Z",
  "completedAt": "2026-03-05T10:35:00Z",
  "downloadErrorReportUrl": "/api/v1/bulk/assets/import/550e8400-e29b-41d4-a716-446655440016/errors"
}
```

### Get Import Error Report

**Endpoint**: `GET /bulk/assets/import/{jobId}/errors`  
**Authentication**: Required  

**Response** (200 OK):
- Content-Type: text/csv
```
rowNumber,name,error,suggestion
15,Dell XPS 13,Invalid category ID,Check category UUID
87,HP Printer,Department not found,Verify department exists
```

### Bulk Export Assets

**Endpoint**: `POST /bulk/assets/export`  
**Authentication**: Required (ROLE_ORG_ADMIN, ROLE_ADMIN)  

**Request Body**:
```json
{
  "format": "CSV",
  "filters": {
    "status": "IN_USE",
    "departmentId": "550e8400-e29b-41d4-a716-446655440003"
  },
  "columns": ["name", "assetTag", "status", "location", "value"]
}
```

**Response** (202 Accepted):
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440017",
  "status": "PROCESSING",
  "format": "CSV",
  "downloadUrl": "/api/v1/bulk/assets/export/550e8400-e29b-41d4-a716-446655440017/download",
  "startedAt": "2026-03-05T10:30:00Z",
  "estimatedRows": 125
}
```

---

## 🪝 WEBHOOKS ENDPOINTS

### Create Webhook

**Endpoint**: `POST /webhooks`  
**Authentication**: Required (ROLE_ORG_ADMIN, ROLE_ADMIN)  

**Request Body**:
```json
{
  "name": "Asset Status Change",
  "url": "https://yourapp.com/webhooks/asset-status",
  "events": ["asset.created", "asset.updated", "asset.deleted"],
  "active": true,
  "secret": "webhook_secret_key"
}
```

**Response** (201 Created):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440018",
  "name": "Asset Status Change",
  "url": "https://yourapp.com/webhooks/asset-status",
  "events": ["asset.created", "asset.updated", "asset.deleted"],
  "active": true,
  "createdAt": "2026-03-05T10:30:00Z",
  "lastTriggeredAt": null,
  "deliveryCount": 0
}
```

### List Webhooks

**Endpoint**: `GET /webhooks`  
**Authentication**: Required  

**Response** (200 OK):
```json
{
  "totalWebhooks": 3,
  "activeWebhooks": 2,
  "webhooks": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440018",
      "name": "Asset Status Change",
      "events": ["asset.created", "asset.updated"],
      "active": true,
      "deliveryCount": 42,
      "lastTriggeredAt": "2026-03-05T09:15:00Z"
    }
  ]
}
```

### Get Webhook Details

**Endpoint**: `GET /webhooks/{webhookId}`  
**Authentication**: Required  

**Response** (200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440018",
  "name": "Asset Status Change",
  "url": "https://yourapp.com/webhooks/asset-status",
  "events": ["asset.created", "asset.updated", "asset.deleted"],
  "active": true,
  "secret": "webhook_secret_***",
  "createdAt": "2026-02-01T10:00:00Z",
  "lastTriggeredAt": "2026-03-05T10:30:00Z",
  "deliveryCount": 42,
  "failureCount": 2,
  "lastFailureAt": "2026-03-02T14:15:00Z"
}
```

### Update Webhook

**Endpoint**: `PATCH /webhooks/{webhookId}`  
**Authentication**: Required  

**Request Body**:
```json
{
  "name": "Asset Status Change Updated",
  "active": false
}
```

**Response** (200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440018",
  "name": "Asset Status Change Updated",
  "active": false,
  "updatedAt": "2026-03-05T10:35:00Z"
}
```

### Delete Webhook

**Endpoint**: `DELETE /webhooks/{webhookId}`  
**Authentication**: Required  

**Response** (204 No Content): Empty body

### Get Webhook Deliveries

**Endpoint**: `GET /webhooks/{webhookId}/deliveries`  
**Authentication**: Required  
**Query Parameters**:
- `status`: success|failed|pending
- `limit`: 50 (default)

**Response** (200 OK):
```json
{
  "webhookId": "550e8400-e29b-41d4-a716-446655440018",
  "totalDeliveries": 42,
  "successfulDeliveries": 40,
  "failedDeliveries": 2,
  "limit": 50,
  "deliveries": [
    {
      "deliveryId": "550e8400-e29b-41d4-a716-446655440019",
      "timestamp": "2026-03-05T09:15:00Z",
      "event": "asset.created",
      "status": "success",
      "statusCode": 200,
      "responseTime": 245,
      "attempts": 1
    }
  ]
}
```

### Test Webhook

**Endpoint**: `POST /webhooks/{webhookId}/test`  
**Authentication**: Required  

**Response** (200 OK):
```json
{
  "webhookId": "550e8400-e29b-41d4-a716-446655440018",
  "testStatus": "success",
  "statusCode": 200,
  "responseTime": 245,
  "testPayload": {
    "event": "test.webhook",
    "timestamp": "2026-03-05T10:30:00Z",
    "data": {"test": true}
  },
  "response": "OK"
}
```

---

## 🔔 NOTIFICATIONS ENDPOINTS

### Get Notifications

**Endpoint**: `GET /notifications`  
**Authentication**: Required  
**Query Parameters**:
- `type`: deprecation|maintenance|approval|system
- `status`: unread|read|all (default: unread)
- `limit`: 20 (default)

**Response** (200 OK):
```json
{
  "totalNotifications": 4,
  "unreadCount": 3,
  "limit": 20,
  "notifications": [
    {
      "notificationId": "550e8400-e29b-41d4-a716-446655440020",
      "type": "deprecation",
      "title": "Asset Depreciation Notice",
      "message": "Dell XPS 13 has completed its useful life",
      "entityId": "550e8400-e29b-41d4-a716-446655440008",
      "createdAt": "2026-03-05T10:30:00Z",
      "read": false,
      "actionUrl": "/api/v1/assets/550e8400-e29b-41d4-a716-446655440008"
    }
  ]
}
```

### Mark Notification as Read

**Endpoint**: `PATCH /notifications/{notificationId}/read`  
**Authentication**: Required  

**Response** (200 OK):
```json
{
  "notificationId": "550e8400-e29b-41d4-a716-446655440020",
  "read": true,
  "readAt": "2026-03-05T10:35:00Z"
}
```

### Mark All Notifications as Read

**Endpoint**: `PATCH /notifications/mark-all-read`  
**Authentication**: Required  

**Response** (200 OK):
```json
{
  "markedAsRead": 3,
  "markedAt": "2026-03-05T10:35:00Z"
}
```

### Delete Notification

**Endpoint**: `DELETE /notifications/{notificationId}`  
**Authentication**: Required  

**Response** (204 No Content): Empty body

### Get Notification Preferences

**Endpoint**: `GET /notifications/preferences`  
**Authentication**: Required  

**Response** (200 OK):
```json
{
  "emailNotifications": {
    "deprecation": true,
    "maintenance": true,
    "approval": true,
    "system": false
  },
  "pushNotifications": true,
  "inAppNotifications": true,
  "dailyDigest": true,
  "digestTime": "09:00"
}
```

### Update Notification Preferences

**Endpoint**: `PATCH /notifications/preferences`  
**Authentication**: Required  

**Request Body**:
```json
{
  "emailNotifications": {
    "deprecation": true,
    "maintenance": false
  },
  "dailyDigest": true,
  "digestTime": "08:00"
}
```

**Response** (200 OK):
```json
{
  "updated": true,
  "updatedAt": "2026-03-05T10:35:00Z",
  "preferences": {
    "emailNotifications": {
      "deprecation": true,
      "maintenance": false
    },
    "dailyDigest": true,
    "digestTime": "08:00"
  }
}
```

---

## ⚕️ HEALTH & MONITORING ENDPOINTS

### Get System Health

**Endpoint**: `GET /health`  
**Authentication**: Not required (optional)  

**Response** (200 OK):
```json
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
```

### Get Detailed Health

**Endpoint**: `GET /health/detailed`  
**Authentication**: Required  

**Response** (200 OK):
```json
{
  "status": "UP",
  "timestamp": "2026-03-05T10:30:00Z",
  "components": {
    "database": {
      "status": "UP",
      "driver": "PostgreSQL JDBC Driver",
      "database": "asset_management",
      "activeConnections": 15,
      "maxConnections": 20,
      "averageResponseTime": "25ms"
    },
    "cache": {
      "status": "UP",
      "type": "Redis",
      "cacheSize": "245MB",
      "maxSize": "500MB",
      "itemCount": 12450
    }
  },
  "uptime": "45d 12h 30m",
  "version": "1.0.0"
}
```

### Get API Metrics

**Endpoint**: `GET /metrics`  
**Authentication**: Required  
**Query Parameters**:
- `period`: day|week|month (default: day)
- `metric`: requests|errors|latency

**Response** (200 OK):
```json
{
  "period": "day",
  "timestamp": "2026-03-05T10:30:00Z",
  "totalRequests": 15420,
  "successfulRequests": 15350,
  "failedRequests": 70,
  "successRate": "99.55%",
  "averageLatency": 145,
  "p50Latency": 120,
  "p95Latency": 450,
  "p99Latency": 890,
  "maxLatency": 2450,
  "errorRate": "0.45%",
  "topErrors": [
    {
      "error": "404 Not Found",
      "count": 35,
      "percentage": "50%"
    },
    {
      "error": "403 Forbidden",
      "count": 25,
      "percentage": "35.7%"
    }
  ],
  "slowestEndpoints": [
    {
      "endpoint": "GET /api/v1/analytics/assets",
      "avgLatency": 350,
      "callCount": 120
    }
  ]
}
```

### Get Endpoint Metrics

**Endpoint**: `GET /metrics/endpoints`  
**Authentication**: Required  
**Query Parameters**:
- `sortBy`: latency|requests|errorRate

**Response** (200 OK):
```json
{
  "timestamp": "2026-03-05T10:30:00Z",
  "totalEndpoints": 50,
  "endpoints": [
    {
      "endpoint": "/api/v1/assets",
      "method": "GET",
      "requests": 8520,
      "averageLatency": 145,
      "errorRate": "0.05%",
      "successRate": "99.95%"
    }
  ]
}
```

### Get Throughput Metrics

**Endpoint**: `GET /metrics/throughput`  
**Authentication**: Required  
**Query Parameters**:
- `hours`: 24 (default)

**Response** (200 OK):
```json
{
  "period": "24 hours",
  "timestamp": "2026-03-05T10:30:00Z",
  "throughput": [
    {
      "hour": "00:00",
      "requestCount": 600,
      "successCount": 595,
      "errorCount": 5,
      "averageLatency": 120
    }
  ]
}
```

### Get Error Metrics

**Endpoint**: `GET /metrics/errors`  
**Authentication**: Required  

**Response** (200 OK):
```json
{
  "totalErrors": 70,
  "errorRate": "0.45%",
  "errors": [
    {
      "errorCode": "404",
      "errorType": "Not Found",
      "count": 35,
      "percentage": "50%",
      "lastOccurrence": "2026-03-05T10:25:00Z"
    }
  ]
}
```

---

## ❌ ERROR RESPONSES

### 400 Bad Request
```json
{
  "status": 400,
  "message": "Validation failed",
  "errors": {
    "email": "Email is required",
    "password": "Password must be at least 8 characters"
  },
  "timestamp": "2026-03-05T10:30:00Z"
}
```

### 401 Unauthorized
```json
{
  "status": 401,
  "message": "JWT token is missing or invalid",
  "timestamp": "2026-03-05T10:30:00Z"
}
```

### 403 Forbidden
```json
{
  "status": 403,
  "message": "You do not have permission to access this resource. Required role: ROLE_ADMIN",
  "timestamp": "2026-03-05T10:30:00Z"
}
```

### 404 Not Found
```json
{
  "status": 404,
  "message": "Asset with ID 550e8400-e29b-41d4-a716-446655440008 not found",
  "timestamp": "2026-03-05T10:30:00Z"
}
```

### 409 Conflict
```json
{
  "status": 409,
  "message": "Asset with name 'Dell XPS 13' already exists in this department",
  "timestamp": "2026-03-05T10:30:00Z"
}
```

### 422 Unprocessable Entity
```json
{
  "status": 422,
  "message": "Purchase order is already approved and cannot be modified",
  "timestamp": "2026-03-05T10:30:00Z"
}
```

### 500 Internal Server Error
```json
{
  "status": 500,
  "message": "Internal server error occurred",
  "timestamp": "2026-03-05T10:30:00Z"
}
```

---

## 📌 HTTP STATUS CODES

| Code | Meaning | Use Case |
|------|---------|----------|
| 200 | OK | Successful GET, PATCH, PUT |
| 201 | Created | Successful POST (resource created) |
| 202 | Accepted | Request accepted, processing async (bulk jobs) |
| 204 | No Content | Successful DELETE |
| 400 | Bad Request | Validation error, missing required fields |
| 401 | Unauthorized | Missing or invalid JWT token |
| 403 | Forbidden | Insufficient permissions/role |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | State conflict (duplicate, invalid transition) |
| 422 | Unprocessable | Business logic validation error |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Server Error | Unexpected server error |

---

## 🔑 COMMON REQUEST HEADERS

```
Content-Type: application/json
Authorization: Bearer {JWT_TOKEN}
Accept: application/json
X-Request-ID: {unique-request-id} (optional, for tracing)
```

---

## 📝 PAGINATION

For endpoints that return lists, use these query parameters:

```
GET /assets?page=0&size=20&sort=createdAt,desc
```

**Response includes**:
```json
{
  "content": [...],
  "totalElements": 150,
  "totalPages": 8,
  "currentPage": 0,
  "pageSize": 20
}
```

---

## 🔒 AUTHENTICATION FLOW

1. **Login** → Get JWT token
   ```
   POST /auth/login → Returns token
   ```

2. **Use token** → Include in all requests
   ```
   Authorization: Bearer {token}
   ```

3. **Token expires** → Refresh token
   ```
   POST /auth/refresh → New token
   ```

4. **Logout** (implicit)
   ```
   Discard token on client side
   ```

---

**Documentation Generated**: March 5, 2026  
**API Version**: v1  
**Status**: Production Ready ✅  

---

*All endpoints tested and documented. Ready for frontend integration.*


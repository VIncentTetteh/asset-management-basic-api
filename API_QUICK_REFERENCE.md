# Asset Management System - API Quick Reference

## Base URL
```
http://localhost:8080/api/v1
```

## Authentication
All endpoints require JWT token in Authorization header:
```
Authorization: Bearer <token>
```

## Response Format
All responses are JSON with this structure:
```json
{
  "id": "uuid",
  "createdAt": "2025-02-20T10:30:00Z",
  "updatedAt": "2025-02-20T10:30:00Z",
  "createdBy": "user@example.com",
  "modifiedBy": "user@example.com"
}
```

---

## Organizations

### Create Organization
```
POST /organisations
Content-Type: application/json

{
  "name": "Tech Corp",
  "registrationNumber": "REG-001",
  "taxId": "TAX-12345",
  "industry": "Technology",
  "country": "USA",
  "address": "123 Tech Street",
  "contactEmail": "contact@techcorp.com",
  "contactPhone": "+1-555-0000",
  "timezone": "America/New_York",
  "status": "ACTIVE"
}
```

### Get Organization
```
GET /organisations/{id}
```

### List Organizations (if multi-org)
```
GET /organisations
```

### Update Organization
```
PUT /organisations/{id}
Content-Type: application/json
```

### Delete Organization
```
DELETE /organisations/{id}
```

---

## Departments

### Create Department
```
POST /departments?organisationId={org_id}
Content-Type: application/json

{
  "name": "IT Department",
  "departmentCode": "IT-001",
  "parentDepartmentId": null,  // null for root department
  "managerId": "manager-uuid",
  "costCenterCode": "CC-001",
  "budgetLimit": 100000.00,
  "status": "ACTIVE"
}
```

### Get Department
```
GET /departments/{id}
```

### List Departments
```
GET /departments?organisationId={org_id}
```

### Get Sub-Departments (Child Departments)
```
GET /departments/{parent_id}/sub-departments
```

### Update Department
```
PUT /departments/{id}
```

### Delete Department
```
DELETE /departments/{id}
```

---

## Assets

### Create Asset
```
POST /assets
Content-Type: application/json

{
  "name": "Dell Laptop",
  "assetTag": "ASSET-001",
  "serialNumber": "SN-12345",
  "barcodeQrCode": "CODE123",
  "description": "Developer laptop",
  "categoryId": "category-uuid",
  "assetType": "HARDWARE",
  "manufacturer": "Dell",
  "model": "XPS 15",
  "purchaseDate": "2025-01-15",
  "purchaseCost": 1500.00,
  "currency": "USD",
  "depreciationMethod": "STRAIGHT_LINE",
  "usefulLifeMonths": 60,
  "residualValue": 300.00,
  "warrantyExpiryDate": "2026-01-15",
  "status": "IN_USE",
  "condition": "GOOD",
  "locationId": "location-uuid",
  "assignedUserId": "user-uuid",
  "supplierId": "supplier-uuid",
  "departmentId": "dept-uuid",
  "organisationId": "org-uuid"
}
```

### Get Asset
```
GET /assets/{id}
```

### List Assets by Department
```
GET /assets?departmentId={dept_id}
```

### List Assets by Status
```
GET /assets?status=IN_USE
```

### List Assets Assigned to User
```
GET /assets?assignedUserId={user_id}
```

### List Assets by Location
```
GET /assets?locationId={location_id}
```

### Update Asset
```
PUT /assets/{id}
```

### Delete Asset (Soft Delete)
```
DELETE /assets/{id}
```

---

## Asset Categories

### Create Category
```
POST /categories?organisationId={org_id}
Content-Type: application/json

{
  "name": "Computers",
  "parentCategoryId": null,
  "depreciationPolicyId": "policy-uuid",
  "defaultWarrantyPeriodMonths": 24,
  "assetPrefixCode": "COMP"
}
```

### Get Category
```
GET /categories/{id}
```

### List Categories
```
GET /categories?organisationId={org_id}
```

### Get Sub-Categories
```
GET /categories/{parent_id}/sub-categories
```

### Update Category
```
PUT /categories/{id}
```

### Delete Category
```
DELETE /categories/{id}
```

---

## Roles

### Create Role
```
POST /roles?organisationId={org_id}
Content-Type: application/json

{
  "name": "Asset Manager",
  "description": "Can manage assets and approvals",
  "permissions": "[\"VIEW_ASSETS\",\"CREATE_ASSET\",\"EDIT_ASSET\",\"APPROVE_REQUESTS\"]"
}
```

### Get Role
```
GET /roles/{id}
```

### List Roles
```
GET /roles?organisationId={org_id}
```

### Get Role by Name
```
GET /roles/by-name?name=Asset Manager&organisationId={org_id}
```

### Update Role
```
PUT /roles/{id}
```

### Delete Role
```
DELETE /roles/{id}
```

---

## Users

### Create User
```
POST /users
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@techcorp.com",
  "phone": "+1-555-1234",
  "password": "secure_password",
  "employeeId": "EMP-001",
  "jobTitle": "IT Manager",
  "roleId": "role-uuid",
  "status": "ACTIVE",
  "organisationId": "org-uuid",
  "departmentId": "dept-uuid"
}
```

### Get User
```
GET /users/{id}
```

### Update User
```
PUT /users/{id}
```

### Delete User
```
DELETE /users/{id}
```

---

## Purchase Orders

### Create Purchase Order
```
POST /purchase-orders
Content-Type: application/json

{
  "poNumber": "PO-2025-001",
  "totalAmount": 5000.00,
  "currency": "USD",
  "status": "DRAFT",
  "remarks": "Office equipment",
  "organisationId": "org-uuid",
  "departmentId": "dept-uuid",
  "supplierId": "supplier-uuid"
}
```

### Get Purchase Order
```
GET /purchase-orders/{id}
```

### List POs by Organization
```
GET /purchase-orders?organisationId={org_id}
```

### List POs by Status
```
GET /purchase-orders?status=APPROVED
```

### Update PO (Draft only)
```
PUT /purchase-orders/{id}
```

### Approve PO
```
POST /purchase-orders/{id}/approve?approvedById={user_id}
```

### Reject PO
```
POST /purchase-orders/{id}/reject
```

### Delete PO
```
DELETE /purchase-orders/{id}
```

---

## Maintenance

### Create Maintenance Record
```
POST /maintenance
Content-Type: application/json

{
  "assetId": "asset-uuid",
  "maintenanceType": "PREVENTIVE",
  "description": "Regular inspection and cleaning",
  "scheduledDate": "2025-03-01",
  "performedDate": null,
  "vendorId": "supplier-uuid",
  "cost": 150.00,
  "status": "SCHEDULED",
  "nextDueDate": "2025-04-01"
}
```

### Get Maintenance Record
```
GET /maintenance/{id}
```

### List Maintenance for Asset
```
GET /maintenance?assetId={asset_id}
```

### List Maintenance Due Before Date
```
GET /maintenance?dueBefore=2025-03-15
```

### Update Maintenance Record
```
PUT /maintenance/{id}
```

### Complete Maintenance
```
POST /maintenance/{id}/complete
```

### Delete Maintenance Record
```
DELETE /maintenance/{id}
```

---

## Audits

### Create Audit
```
POST /audits
Content-Type: application/json

{
  "organisationId": "org-uuid",
  "departmentId": "dept-uuid",
  "auditDate": "2025-02-20",
  "conductedById": "user-uuid",
  "status": "PLANNED",
  "remarks": "Quarterly asset audit"
}
```

### Get Audit
```
GET /audits/{id}
```

### List Audits by Organization
```
GET /audits?organisationId={org_id}
```

### List Audits by Date Range
```
GET /audits?startDate=2025-01-01&endDate=2025-02-28
```

### Update Audit Status
```
PATCH /audits/{id}/status?status=IN_PROGRESS
```

---

## Asset Transfers

### Create Transfer Request
```
POST /asset-transfers
Content-Type: application/json

{
  "assetId": "asset-uuid",
  "fromDepartmentId": "from-dept-uuid",
  "toDepartmentId": "to-dept-uuid",
  "fromLocationId": "from-loc-uuid",
  "toLocationId": "to-loc-uuid",
  "requestedById": "user-uuid",
  "reason": "Project relocation"
}
```

### Get Transfer
```
GET /asset-transfers/{id}
```

### List Transfers for Asset
```
GET /asset-transfers?assetId={asset_id}
```

### List Transfers From Department
```
GET /asset-transfers?fromDepartmentId={dept_id}
```

### List Transfers To Department
```
GET /asset-transfers?toDepartmentId={dept_id}
```

### Approve Transfer
```
POST /asset-transfers/{id}/approve?approvedById={user_id}
```

### Reject Transfer
```
POST /asset-transfers/{id}/reject
```

### Complete Transfer
```
POST /asset-transfers/{id}/complete
```

---

## Disposals

### Create Disposal Record
```
POST /disposals
Content-Type: application/json

{
  "assetId": "asset-uuid",
  "disposalMethod": "SALE",
  "disposalDate": "2025-02-20",
  "saleValue": 500.00,
  "approvedById": "user-uuid",
  "reason": "End of life",
  "complianceDocumentUrl": "https://...",
  "organisationId": "org-uuid"
}
```

### Get Disposal Record
```
GET /disposals/{id}
```

### List Disposals for Asset
```
GET /disposals?assetId={asset_id}
```

### List Disposals by Date Range
```
GET /disposals?startDate=2025-01-01&endDate=2025-02-28
```

### Update Disposal Record
```
PUT /disposals/{id}
```

---

## Common Status Values

### Asset Status
- `IN_USE`
- `IN_STOCK`
- `MAINTENANCE`
- `DISPOSED`
- `RETIRED`
- `MISSING`

### Asset Condition
- `NEW`
- `EXCELLENT`
- `GOOD`
- `FAIR`
- `DAMAGED`
- `SCRAP`

### Asset Type
- `HARDWARE`
- `SOFTWARE`
- `FURNITURE`
- `VEHICLE`
- `EQUIPMENT`
- `OTHER`

### Depreciation Method
- `STRAIGHT_LINE`
- `DECLINING_BALANCE`
- `UNITS_OF_PRODUCTION`
- `SUM_OF_YEARS_DIGITS`

### PO Status
- `DRAFT`
- `SUBMITTED`
- `APPROVED`
- `REJECTED`
- `DELIVERED`
- `CANCELLED`

### Transfer Status
- `REQUESTED`
- `APPROVED`
- `REJECTED`
- `IN_TRANSIT`
- `COMPLETED`
- `CANCELLED`

### Disposal Method
- `SALE`
- `DONATION`
- `SCRAP`
- `RECYCLING`
- `TRADE_IN`
- `RETURN`

### Maintenance Type
- `PREVENTIVE`
- `CORRECTIVE`
- `EMERGENCY`
- `ROUTINE`

---

## Error Responses

### 400 Bad Request
```json
{
  "error": "Invalid request parameter",
  "message": "Asset tag is required"
}
```

### 401 Unauthorized
```json
{
  "error": "Invalid credentials",
  "message": "Authentication failed"
}
```

### 403 Forbidden
```json
{
  "error": "Access denied",
  "message": "You don't have permission to perform this action"
}
```

### 404 Not Found
```json
{
  "error": "Resource not found",
  "message": "Asset with ID {id} not found"
}
```

### 409 Conflict
```json
{
  "error": "Conflict",
  "message": "Asset tag already exists"
}
```

### 500 Internal Server Error
```json
{
  "error": "Internal server error",
  "message": "An unexpected error occurred"
}
```

---

## Pagination

List endpoints support pagination:
```
GET /assets?page=1&size=20&sort=createdAt,desc
```

---

## Filtering

Multiple filters can be combined:
```
GET /assets?departmentId={id}&status=IN_USE&assignedUserId={id}
```

---

## Date Format

All dates use ISO 8601 format:
```
2025-02-20T10:30:00Z
```

For date parameters:
```
2025-02-20
```

---

## Rate Limiting

Recommended: 100 requests per minute per API key

---

## Version

API Version: v1
Last Updated: February 2026


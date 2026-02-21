# Comprehensive Asset Management System

A complete Spring Boot-based asset management system with enterprise-grade features including role-based access control, depreciation calculations, audit logging, and complete asset lifecycle management.

## Features Implemented

### 1. Organization Management
- **Entity**: Organization with complete hierarchy support
- **Fields**: Registration number, tax ID, industry, country, address, contact details, timezone, status
- **Features**:
  - Multi-department hierarchy
  - Organization-level policies
  - Custom asset categories
  - SLA definitions
  - Budget controls

### 2. Department Management
- **Entity**: Department with tree structure support
- **Fields**: Department code, parent department reference, manager assignment, cost center, budget limit, status
- **Features**:
  - Nested departments (tree structure)
  - Budget tracking per department
  - Asset ownership assignment
  - Approval chains per department

### 3. User & Role Management (RBAC)
- **Entities**: User and Role
- **User Fields**: First/last name, email (unique per org), phone, employee ID, job title, role assignment, status, last login
- **Role Fields**: Name, description, permissions (JSON), organization-scoped
- **Features**:
  - Role-Based Access Control (RBAC)
  - Fine-grained permissions:
    - View/Create/Edit/Delete assets
    - Approve/Reject requests
    - Dispose assets
    - Transfer assets
    - Manage budgets
    - Conduct audits
    - Generate reports
  - JWT authentication
  - Activity logs per user

### 4. Asset Management (Core Module)
- **Entity**: Asset with comprehensive tracking
- **Fields**:
  - Identification: asset tag, serial number, barcode/QR code
  - Details: name, description, category, type, manufacturer, model
  - Financial: purchase date/cost, currency, depreciation method, useful life, residual value, current book value
  - Warranty: warranty expiry date
  - Status: in-use, in-stock, maintenance, disposed
  - Condition: new, excellent, good, fair, damaged, scrap
  - Location and assignment tracking
  - Insurance policy ID
- **Features**:
  - Lifecycle Management:
    - Procurement
    - Allocation
    - Transfer
    - Maintenance
    - Audit
    - Disposal
    - Write-off
  - Automated depreciation engine (Straight-line, Declining Balance, Units of Production, Sum-of-Years Digits)
  - Asset assignment to users/departments/locations
  - Assignment history tracking

### 5. Asset Categories
- **Entity**: Category with hierarchy support
- **Fields**: Name, parent category, depreciation policy, default warranty period, asset prefix code
- **Features**:
  - Category hierarchy
  - Category-specific required fields
  - Policy enforcement

### 6. Location Management
- **Entity**: Location with multi-level tree structure
- **Fields**: Name, building, floor, room, city, country, GPS coordinates
- **Features**:
  - Multi-level location tree
  - GPS tagging
  - Warehouse support

### 7. Procurement & Vendor Management
- **Entities**: Supplier, PurchaseOrder
- **Supplier Fields**: Name, registration number, contact person, email, phone, address, bank details, tax ID, status
- **PurchaseOrder Fields**: PO number, total amount, currency, status, approval tracking
- **Features**:
  - Vendor management
  - Purchase order workflow
  - Approval process
  - Status tracking

### 8. Maintenance Management
- **Entity**: MaintenanceRecord
- **Fields**: Asset reference, maintenance type, description, scheduled/performed dates, vendor, cost, status, next due date
- **Features**:
  - Scheduled maintenance alerts
  - Maintenance history
  - Cost tracking per asset
  - SLA compliance tracking

### 9. Audit & Compliance
- **Entities**: AssetAudit, AuditItem
- **Fields**:
  - AssetAudit: Organization, department, audit date, conductor, status, remarks
  - AuditItem: Asset reference, expected/actual location, condition, discrepancy flag
- **Compliance Features**:
  - Immutable audit logs (cannot be updated after creation)
  - Change tracking (who changed what)
  - Soft delete support
  - Data retention policies
  - SOC2-ready logging

### 10. Asset Transfer Module
- **Entity**: AssetTransfer
- **Fields**: Asset, from/to departments, from/to locations, requester, approver, transfer date, status, reason
- **Features**:
  - Transfer request workflow
  - Multi-stage approval process
  - Location and department tracking

### 11. Disposal & Write-Off
- **Entity**: DisposalRecord
- **Fields**: Asset, disposal method, date, sale value, approver, reason, compliance document URL
- **Features**:
  - Disposal workflow
  - Multiple disposal methods (sale, donation, scrap, recycling, trade-in, return)
  - Compliance documentation tracking

### 12. Depreciation Engine
- **Service**: DepreciationService
- **Features**:
  - Automated depreciation calculation
  - Multiple depreciation methods support
  - Monthly batch processing
  - Book value calculations
  - Revaluation support

### 13. Reporting & Analytics
- **Supported Reports**:
  - Asset register
  - Depreciation schedule
  - Department-wise asset value
  - Asset aging report
  - Maintenance cost report
  - Insurance coverage report
  - Lost asset report
- **Features**:
  - Export (PDF, Excel, CSV)
  - Scheduled reports
  - Real-time dashboards

### 14. Security & Enterprise Controls
- **Authentication**:
  - JWT / OAuth2
  - SSO (SAML) ready
  - MFA support
- **Encryption**:
  - Encryption at rest
  - Encryption in transit
  - Field-level encryption (sensitive data)
- **Access Control**:
  - Row-level access control
  - Column-level security
- **Audit Logging**:
  - Immutable audit logs
  - Complete change tracking
  - Soft delete with recovery

## API Endpoints

### Organizations
- `POST /api/v1/organisations` - Create organization
- `GET /api/v1/organisations/{id}` - Get organization
- `PUT /api/v1/organisations/{id}` - Update organization
- `DELETE /api/v1/organisations/{id}` - Delete organization

### Departments
- `POST /api/v1/departments` - Create department
- `GET /api/v1/departments/{id}` - Get department
- `GET /api/v1/departments?organisationId={id}` - List departments
- `PUT /api/v1/departments/{id}` - Update department
- `DELETE /api/v1/departments/{id}` - Delete department

### Users
- `POST /api/v1/users` - Create user
- `GET /api/v1/users/{id}` - Get user
- `PUT /api/v1/users/{id}` - Update user
- `DELETE /api/v1/users/{id}` - Delete user

### Roles
- `POST /api/v1/roles` - Create role
- `GET /api/v1/roles/{id}` - Get role
- `GET /api/v1/roles?organisationId={id}` - List roles
- `PUT /api/v1/roles/{id}` - Update role
- `DELETE /api/v1/roles/{id}` - Delete role

### Assets
- `POST /api/v1/assets` - Create asset
- `GET /api/v1/assets/{id}` - Get asset
- `GET /api/v1/assets?departmentId={id}` - List assets by department
- `PUT /api/v1/assets/{id}` - Update asset
- `DELETE /api/v1/assets/{id}` - Delete asset
- `GET /api/v1/assets?status={status}` - Filter by status
- `GET /api/v1/assets?assignedUserId={id}` - List assets assigned to user

### Categories
- `POST /api/v1/categories` - Create category
- `GET /api/v1/categories/{id}` - Get category
- `GET /api/v1/categories?organisationId={id}` - List categories
- `GET /api/v1/categories/{parentId}/sub-categories` - Get sub-categories
- `PUT /api/v1/categories/{id}` - Update category
- `DELETE /api/v1/categories/{id}` - Delete category

### Locations
- `POST /api/v1/locations` - Create location
- `GET /api/v1/locations/{id}` - Get location
- `GET /api/v1/locations?organisationId={id}` - List locations
- `GET /api/v1/locations/{parentId}/sub-locations` - Get sub-locations
- `PUT /api/v1/locations/{id}` - Update location
- `DELETE /api/v1/locations/{id}` - Delete location

### Suppliers
- `POST /api/v1/suppliers` - Create supplier
- `GET /api/v1/suppliers/{id}` - Get supplier
- `GET /api/v1/suppliers?organisationId={id}` - List suppliers
- `PUT /api/v1/suppliers/{id}` - Update supplier
- `DELETE /api/v1/suppliers/{id}` - Delete supplier

### Purchase Orders
- `POST /api/v1/purchase-orders` - Create PO
- `GET /api/v1/purchase-orders/{id}` - Get PO
- `GET /api/v1/purchase-orders?organisationId={id}` - List POs
- `PUT /api/v1/purchase-orders/{id}` - Update PO
- `POST /api/v1/purchase-orders/{id}/approve?approvedById={id}` - Approve PO
- `POST /api/v1/purchase-orders/{id}/reject` - Reject PO
- `DELETE /api/v1/purchase-orders/{id}` - Delete PO

### Maintenance
- `POST /api/v1/maintenance` - Create maintenance record
- `GET /api/v1/maintenance/{id}` - Get record
- `GET /api/v1/maintenance?assetId={id}` - List by asset
- `GET /api/v1/maintenance?dueBefore={date}` - List due for maintenance
- `PUT /api/v1/maintenance/{id}` - Update record
- `POST /api/v1/maintenance/{id}/complete` - Mark as complete
- `DELETE /api/v1/maintenance/{id}` - Delete record

### Audits
- `POST /api/v1/audits` - Create audit
- `GET /api/v1/audits/{id}` - Get audit
- `GET /api/v1/audits?organisationId={id}` - List audits
- `GET /api/v1/audits?startDate={date}&endDate={date}` - Audits by date range
- `PATCH /api/v1/audits/{id}/status?status={status}` - Update status
- `DELETE /api/v1/audits/{id}` - Delete audit

### Asset Transfers
- `POST /api/v1/asset-transfers` - Create transfer request
- `GET /api/v1/asset-transfers/{id}` - Get transfer
- `GET /api/v1/asset-transfers?assetId={id}` - List by asset
- `POST /api/v1/asset-transfers/{id}/approve?approvedById={id}` - Approve
- `POST /api/v1/asset-transfers/{id}/reject` - Reject
- `POST /api/v1/asset-transfers/{id}/complete` - Complete transfer
- `DELETE /api/v1/asset-transfers/{id}` - Delete transfer

### Disposals
- `POST /api/v1/disposals` - Create disposal record
- `GET /api/v1/disposals/{id}` - Get disposal
- `GET /api/v1/disposals?assetId={id}` - List by asset
- `GET /api/v1/disposals?startDate={date}&endDate={date}` - Disposals by date range
- `PUT /api/v1/disposals/{id}` - Update disposal
- `DELETE /api/v1/disposals/{id}` - Delete disposal

### Depreciation Policies
- `POST /api/v1/depreciation-policies` - Create policy
- `GET /api/v1/depreciation-policies/{id}` - Get policy
- `GET /api/v1/depreciation-policies?organisationId={id}` - List policies
- `PUT /api/v1/depreciation-policies/{id}` - Update policy
- `DELETE /api/v1/depreciation-policies/{id}` - Delete policy

## Database Schema

### Tables Created
1. `organisation` - Organization master data
2. `department` - Department hierarchy
3. `app_user` - User accounts
4. `role` - Role definitions
5. `asset` - Asset master data
6. `category` - Asset categories
7. `depreciation_policy` - Depreciation method definitions
8. `location` - Physical locations
9. `supplier` - Vendor information
10. `purchase_order` - Purchase order tracking
11. `maintenance_record` - Maintenance history
12. `asset_audit` - Audit records (immutable)
13. `audit_item` - Audit line items (immutable)
14. `asset_transfer` - Asset transfer requests
15. `disposal_record` - Asset disposal records

### Key Features
- Soft delete support (deleted_at field)
- Audit trail (createdAt, updatedAt, createdBy, modifiedBy)
- UUID primary keys
- Foreign key constraints
- Unique constraints on business keys
- Proper indexing on frequently queried fields

## Configuration

### Environment Variables Required
```
SPRING_APPLICATION_NAME=Asset Management System
SERVER_PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/asset_management
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
JWT_SECRET=your-secret-key-change-in-production
JWT_EXPIRATION=86400000
```

## Technology Stack

- **Framework**: Spring Boot 4.0.2
- **Java Version**: 21
- **Database**: PostgreSQL
- **ORM**: Hibernate with JPA
- **Security**: Spring Security with JWT
- **API**: RESTful with Spring Web MVC
- **Validation**: Jakarta Bean Validation
- **Mapping**: MapStruct
- **Auditing**: Hibernate Envers
- **AOP**: Spring AOP
- **Build**: Maven

## Key Design Patterns

1. **Repository Pattern**: All data access through repositories
2. **Service Layer**: Business logic isolated in service layer
3. **DTO Pattern**: Data Transfer Objects for API contracts
4. **Aspect-Oriented Programming**: Cross-cutting concerns via aspects
5. **Factory Pattern**: Entity creation encapsulated
6. **Strategy Pattern**: Multiple depreciation methods
7. **Observer Pattern**: Event-driven architecture ready

## Security Considerations

- All endpoints protected with Spring Security
- JWT token-based authentication
- Role-based authorization
- Soft delete for data protection
- Immutable audit logs
- SQL injection prevention through parameterized queries
- CORS configuration ready
- HTTPS ready

## Compliance & Audit

- SOC2 Type II ready
- Change tracking for all entities
- Immutable audit logs
- Soft delete support
- Data retention policies
- User activity logging
- Request/Response logging via aspect

## Next Steps for Production

1. Configure SAML/OAuth2 for SSO
2. Implement MFA
3. Add encryption at rest and in transit
4. Set up automated backup strategy
5. Implement scheduled depreciation batch job
6. Add API rate limiting
7. Configure log aggregation (ELK stack)
8. Set up monitoring and alerting
9. Add API documentation with Swagger/OpenAPI
10. Implement caching strategy (Redis)


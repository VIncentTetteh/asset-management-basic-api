# Compliance API — Request & Response Guide

All compliance endpoints are under `/api/v1/compliance/`.

## Common Request Headers

```http
Authorization: Bearer <jwt_token>
X-Organisation-Id: <organisation_uuid>
Content-Type: application/json
```

> `X-Organisation-Id` is required on all compliance endpoints.
> All endpoints require role `ROLE_ADMIN` or `ROLE_ORG_ADMIN`.

## Pagination (where applicable)

Paged endpoints accept standard query params:

```
?page=0&size=20&sort=createdAt,desc
```

Response shape for paged endpoints:
```json
{
  "content": [ ... ],
  "totalElements": 42,
  "totalPages": 3,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false
}
```

---

## Enum Reference

### ComplianceFramework
`ISO_27001` | `SOC2` | `PCI_DSS` | `ICS` | `BOG`

### ControlStatus
`NOT_IMPLEMENTED` | `PARTIAL` | `IMPLEMENTED` | `NOT_APPLICABLE`

### RiskStatus
`OPEN` | `IN_TREATMENT` | `CLOSED` | `ACCEPTED`

### RiskTreatment
`ACCEPT` | `MITIGATE` | `TRANSFER` | `AVOID`

### IncidentStatus
`OPEN` | `IN_PROGRESS` | `RESOLVED` | `CLOSED`

### IncidentSeverity
`P1_CRITICAL` | `P2_HIGH` | `P3_MEDIUM` | `P4_LOW`

### PolicyStatus
`DRAFT` | `UNDER_REVIEW` | `APPROVED` | `RETIRED`

### VendorSupportStatus
`SUPPORTED` | `END_OF_LIFE` | `END_OF_SUPPORT` | `UNKNOWN`

### PatchStatus
`PLANNED` | `APPLIED` | `FAILED` | `ROLLED_BACK`

### ComplianceAnswer (PCI SAQ)
`YES` | `NO` | `NOT_APPLICABLE` | `COMPENSATING_CONTROL`

### ScanType
`INTERNAL` | `EXTERNAL` | `ASV` | `ICS_OT`

### ScanStatus
`PASS` | `FAIL` | `PENDING_REMEDIATION`

### FilingStatus
`PENDING` | `SUBMITTED` | `OVERDUE` | `ACKNOWLEDGED` | `REJECTED`

---

## 1) Compliance Controls

Generic multi-framework control tracking (ISO 27001, SOC 2, PCI-DSS, ICS, BOG).

### 1.1 List Controls

`GET /api/v1/compliance/controls`

Query params (all optional):

| Param | Type | Description |
|---|---|---|
| `framework` | string | Filter by framework enum value |
| `status` | string | Filter by control status enum value |

Response `200 OK`:
```json
[
  {
    "id": "a1b2c3d4-0000-0000-0000-000000000001",
    "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
    "framework": "ISO_27001",
    "controlRef": "A.9.1.1",
    "controlName": "Access Control Policy",
    "controlDescription": "An access control policy shall be established, documented and reviewed.",
    "status": "IMPLEMENTED",
    "justification": "Policy document in place and reviewed annually.",
    "evidenceUrl": "https://drive.example.com/access-policy.pdf",
    "gapDescription": null,
    "remediationPlan": null,
    "ownerId": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
    "ownerEmail": "ama@acme.com",
    "reviewDueDate": "2027-03-01T00:00:00.000Z",
    "lastReviewedAt": "2026-03-01T10:00:00.000Z",
    "lastReviewedByEmail": "ama@acme.com",
    "createdAt": "2026-01-15T08:00:00.000Z",
    "updatedAt": "2026-03-01T10:00:00.000Z"
  }
]
```

### 1.2 Get Control by ID

`GET /api/v1/compliance/controls/{id}`

Response `200 OK`: Same shape as a single item above.

### 1.3 Create Control

`POST /api/v1/compliance/controls`

Request:
```json
{
  "framework": "ISO_27001",
  "controlRef": "A.9.1.1",
  "controlName": "Access Control Policy",
  "controlDescription": "An access control policy shall be established, documented and reviewed.",
  "status": "NOT_IMPLEMENTED",
  "justification": null,
  "evidenceUrl": null,
  "gapDescription": "No formal policy document exists.",
  "remediationPlan": "Draft and approve policy by Q2 2026.",
  "ownerId": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
  "reviewDueDate": "2027-03-01T00:00:00.000Z"
}
```

Required fields: `framework`, `controlRef`, `controlName`

Response `201 Created`: Full control object.

### 1.4 Update Control (partial)

`PATCH /api/v1/compliance/controls/{id}`

Send only the fields to change:
```json
{
  "status": "IMPLEMENTED",
  "justification": "Policy approved by Board.",
  "evidenceUrl": "https://drive.example.com/access-policy.pdf",
  "lastReviewedAt": "2026-03-09T00:00:00.000Z",
  "lastReviewedByEmail": "ciso@acme.com"
}
```

Response `200 OK`: Updated control object.

### 1.5 Delete Control

`DELETE /api/v1/compliance/controls/{id}`

Response `204 No Content`

---

## 2) BOG Controls

Bank of Ghana ICT Security Directive control tracking for licensed financial institutions.

### 2.1 List BOG Controls

`GET /api/v1/compliance/bog-controls`

Query params (optional): `status` — filter by `ControlStatus`

Response `200 OK`:
```json
[
  {
    "id": "b2c3d4e5-0000-0000-0000-000000000001",
    "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
    "directiveRef": "ICT.4.1",
    "requirement": "Licensed institutions shall maintain a written ICT security policy.",
    "status": "PARTIAL",
    "evidenceUrl": null,
    "gapDescription": "Policy drafted but not yet board-approved.",
    "remediationPlan": "Present to board in April 2026.",
    "targetDate": "2026-04-30T00:00:00.000Z",
    "ownerId": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
    "ownerEmail": "ama@acme.com",
    "createdAt": "2026-02-01T08:00:00.000Z",
    "updatedAt": "2026-03-01T10:00:00.000Z"
  }
]
```

### 2.2 Get BOG Control by ID

`GET /api/v1/compliance/bog-controls/{id}`

Response `200 OK`: Single item.

### 2.3 Create BOG Control

`POST /api/v1/compliance/bog-controls`

Request:
```json
{
  "directiveRef": "ICT.4.1",
  "requirement": "Licensed institutions shall maintain a written ICT security policy.",
  "status": "NOT_IMPLEMENTED",
  "gapDescription": "No policy document exists.",
  "remediationPlan": "Draft policy by Q2 2026.",
  "targetDate": "2026-06-30T00:00:00.000Z",
  "ownerId": "9a5eb35a-bfef-4a20-8705-0176f7142e95"
}
```

Required fields: `directiveRef`, `requirement`

Response `201 Created`: Full object.

### 2.4 Update BOG Control (partial)

`PATCH /api/v1/compliance/bog-controls/{id}`

```json
{
  "status": "IMPLEMENTED",
  "evidenceUrl": "https://drive.example.com/bog-ict-policy.pdf"
}
```

Response `200 OK`: Updated object.

### 2.5 Delete BOG Control

`DELETE /api/v1/compliance/bog-controls/{id}`

Response `204 No Content`

---

## 3) Risk Register

Organisation-wide risk tracking with likelihood × impact scoring.

### 3.1 List Risks (paged)

`GET /api/v1/compliance/risks`

Query params:

| Param | Type | Description |
|---|---|---|
| `status` | string | Optional. Filter by `RiskStatus` |
| `page` | int | Page number (0-based) |
| `size` | int | Page size (default 20) |

Response `200 OK` (paged):
```json
{
  "content": [
    {
      "id": "c3d4e5f6-0000-0000-0000-000000000001",
      "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
      "framework": "ISO_27001",
      "riskId": "RISK-001",
      "title": "Unauthorised access to customer data",
      "description": "Risk of data breach due to weak access controls.",
      "likelihood": 3,
      "impact": 5,
      "riskScore": 15,
      "treatment": "MITIGATE",
      "mitigationPlan": "Implement MFA and role-based access control.",
      "residualRisk": 6,
      "status": "IN_TREATMENT",
      "ownerId": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
      "ownerEmail": "ama@acme.com",
      "reviewDate": "2026-06-01T00:00:00.000Z",
      "createdAt": "2026-01-10T08:00:00.000Z",
      "updatedAt": "2026-03-01T10:00:00.000Z"
    }
  ],
  "totalElements": 14,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

> `riskScore` is computed automatically as `likelihood × impact` (1–25).

### 3.2 Get Risk by ID

`GET /api/v1/compliance/risks/{id}`

Response `200 OK`: Single risk object.

### 3.3 Create Risk

`POST /api/v1/compliance/risks`

Request:
```json
{
  "framework": "ISO_27001",
  "riskId": "RISK-001",
  "title": "Unauthorised access to customer data",
  "description": "Risk of data breach due to weak access controls.",
  "likelihood": 3,
  "impact": 5,
  "treatment": "MITIGATE",
  "mitigationPlan": "Implement MFA and RBAC.",
  "residualRisk": 6,
  "status": "OPEN",
  "ownerId": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
  "reviewDate": "2026-06-01T00:00:00.000Z"
}
```

Required fields: `title`, `likelihood` (1–5), `impact` (1–5)

Response `201 Created`: Full risk object with computed `riskScore`.

### 3.4 Update Risk (partial)

`PATCH /api/v1/compliance/risks/{id}`

```json
{
  "status": "IN_TREATMENT",
  "residualRisk": 4,
  "mitigationPlan": "MFA deployed. RBAC review in progress."
}
```

Response `200 OK`: Updated risk object.

### 3.5 Delete Risk

`DELETE /api/v1/compliance/risks/{id}`

Response `204 No Content`

---

## 4) Security Incidents

Incident management with severity triage and lifecycle tracking.

### 4.1 List Incidents (paged)

`GET /api/v1/compliance/incidents`

Query params: `page`, `size`, `sort`

Response `200 OK` (paged):
```json
{
  "content": [
    {
      "id": "d4e5f6a7-0000-0000-0000-000000000001",
      "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
      "title": "Phishing campaign targeting finance team",
      "description": "Multiple employees received phishing emails impersonating the CEO.",
      "severity": "P2_HIGH",
      "category": "Phishing",
      "reportedById": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
      "reportedByEmail": "ama@acme.com",
      "assignedToId": "7f8e9d1c-bfef-4a20-8705-0176f7142e96",
      "assignedToEmail": "soc@acme.com",
      "detectedAt": "2026-03-08T14:30:00.000Z",
      "resolvedAt": null,
      "rootCause": null,
      "lessonsLearned": null,
      "status": "IN_PROGRESS",
      "createdAt": "2026-03-08T15:00:00.000Z",
      "updatedAt": "2026-03-08T15:00:00.000Z"
    }
  ],
  "totalElements": 7,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

### 4.2 Get Incident by ID

`GET /api/v1/compliance/incidents/{id}`

Response `200 OK`: Single incident object.

### 4.3 Create Incident

`POST /api/v1/compliance/incidents`

Request:
```json
{
  "title": "Phishing campaign targeting finance team",
  "description": "Multiple employees received phishing emails impersonating the CEO.",
  "severity": "P2_HIGH",
  "category": "Phishing",
  "reportedById": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
  "assignedToId": "7f8e9d1c-bfef-4a20-8705-0176f7142e96",
  "detectedAt": "2026-03-08T14:30:00.000Z"
}
```

Required fields: `title`, `severity`

Response `201 Created`: Full incident object.

### 4.4 Update Incident (partial)

`PATCH /api/v1/compliance/incidents/{id}`

```json
{
  "status": "RESOLVED",
  "resolvedAt": "2026-03-09T09:00:00.000Z",
  "rootCause": "Lack of email security gateway filtering.",
  "lessonsLearned": "Deploy SPF/DKIM/DMARC and conduct phishing simulation training."
}
```

Response `200 OK`: Updated incident object.

### 4.5 Delete Incident

`DELETE /api/v1/compliance/incidents/{id}`

Response `204 No Content`

---

## 5) Security Policies

Policy lifecycle management from draft through approval.

### 5.1 List Policies

`GET /api/v1/compliance/policies`

Response `200 OK`:
```json
[
  {
    "id": "e5f6a7b8-0000-0000-0000-000000000001",
    "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
    "title": "Information Security Policy",
    "version": "2.1",
    "documentUrl": "https://drive.example.com/isms-policy-v2.1.pdf",
    "ownerId": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
    "ownerEmail": "ama@acme.com",
    "approvedByEmail": "ceo@acme.com",
    "effectiveDate": "2026-01-01T00:00:00.000Z",
    "reviewDueDate": "2027-01-01T00:00:00.000Z",
    "status": "APPROVED",
    "createdAt": "2025-12-01T08:00:00.000Z",
    "updatedAt": "2026-01-01T08:00:00.000Z"
  }
]
```

### 5.2 Get Policy by ID

`GET /api/v1/compliance/policies/{id}`

Response `200 OK`: Single policy object.

### 5.3 Create Policy

`POST /api/v1/compliance/policies`

Request:
```json
{
  "title": "Information Security Policy",
  "version": "1.0",
  "documentUrl": "https://drive.example.com/draft-isms-policy.pdf",
  "ownerId": "9a5eb35a-bfef-4a20-8705-0176f7142e95",
  "reviewDueDate": "2027-01-01T00:00:00.000Z",
  "status": "DRAFT"
}
```

Required fields: `title`

Response `201 Created`: Full policy object.

### 5.4 Update Policy (partial)

`PATCH /api/v1/compliance/policies/{id}`

```json
{
  "status": "APPROVED",
  "version": "1.1",
  "approvedByEmail": "ceo@acme.com",
  "effectiveDate": "2026-04-01T00:00:00.000Z"
}
```

Response `200 OK`: Updated policy object.

### 5.5 Delete Policy

`DELETE /api/v1/compliance/policies/{id}`

Response `204 No Content`

---

## 6) Security Zones

ICS/IEC 62443 Purdue-model security zone management (levels 0–5).

### 6.1 List Security Zones

`GET /api/v1/compliance/security-zones`

Returns zones ordered by `purdueLevel` ascending.

Response `200 OK`:
```json
[
  {
    "id": "f6a7b8c9-0000-0000-0000-000000000001",
    "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
    "name": "Field Devices Zone",
    "purdueLevel": 0,
    "description": "Sensors, actuators, and field instruments.",
    "allowedProtocols": "Modbus, HART",
    "assetCount": 42,
    "networkRange": "10.0.0.0/24",
    "createdAt": "2026-01-10T08:00:00.000Z",
    "updatedAt": "2026-03-01T10:00:00.000Z"
  },
  {
    "id": "f6a7b8c9-0000-0000-0000-000000000002",
    "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
    "name": "Control Systems Zone",
    "purdueLevel": 1,
    "description": "PLCs and RTUs.",
    "allowedProtocols": "Modbus TCP, DNP3",
    "assetCount": 12,
    "networkRange": "10.1.0.0/24",
    "createdAt": "2026-01-10T08:00:00.000Z",
    "updatedAt": "2026-01-10T08:00:00.000Z"
  }
]
```

> Purdue levels: `0`=Field devices, `1`=Controllers, `2`=Supervisory, `3`=Operations, `4`=Enterprise, `5`=DMZ

### 6.2 Get Security Zone by ID

`GET /api/v1/compliance/security-zones/{id}`

Response `200 OK`: Single zone object.

### 6.3 Create Security Zone

`POST /api/v1/compliance/security-zones`

Request:
```json
{
  "name": "Field Devices Zone",
  "purdueLevel": 0,
  "description": "Sensors, actuators, and field instruments.",
  "allowedProtocols": "Modbus, HART",
  "networkRange": "10.0.0.0/24"
}
```

Required fields: `name`, `purdueLevel` (0–5)

Response `201 Created`: Full zone object.

### 6.4 Update Security Zone (partial)

`PATCH /api/v1/compliance/security-zones/{id}`

```json
{
  "allowedProtocols": "Modbus, HART, OPC-UA",
  "assetCount": 45
}
```

Response `200 OK`: Updated zone object.

### 6.5 Delete Security Zone

`DELETE /api/v1/compliance/security-zones/{id}`

Response `204 No Content`

---

## 7) ICS Assets

OT/ICS compliance metadata overlay for existing assets.

### 7.1 List ICS Assets

`GET /api/v1/compliance/ics-assets`

Response `200 OK`:
```json
[
  {
    "id": "a7b8c9d0-0000-0000-0000-000000000001",
    "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
    "assetId": "c0e8e2a4-6d25-4c67-a620-c0222f6cb7bf",
    "assetName": "Siemens S7-300 PLC",
    "securityZoneId": "f6a7b8c9-0000-0000-0000-000000000001",
    "securityZoneName": "Control Systems Zone",
    "firmwareVersion": "V3.2.7",
    "protocol": "Modbus TCP",
    "vendorSupportStatus": "SUPPORTED",
    "lastPatchedAt": "2026-01-15T00:00:00.000Z",
    "knownVulnerabilities": "CVE-2021-37185",
    "isolated": false,
    "notes": "Critical control node — change window restricted to Saturday 02:00-04:00.",
    "createdAt": "2026-02-01T08:00:00.000Z",
    "updatedAt": "2026-02-01T08:00:00.000Z"
  }
]
```

### 7.2 Get ICS Asset by ID

`GET /api/v1/compliance/ics-assets/{id}`

Response `200 OK`: Single ICS asset object.

### 7.3 Create ICS Asset

`POST /api/v1/compliance/ics-assets`

> Links OT compliance metadata to an existing asset. One-to-one per asset.

Request:
```json
{
  "assetId": "c0e8e2a4-6d25-4c67-a620-c0222f6cb7bf",
  "securityZoneId": "f6a7b8c9-0000-0000-0000-000000000001",
  "firmwareVersion": "V3.2.7",
  "protocol": "Modbus TCP",
  "vendorSupportStatus": "SUPPORTED",
  "lastPatchedAt": "2026-01-15T00:00:00.000Z",
  "knownVulnerabilities": "CVE-2021-37185",
  "isolated": false,
  "notes": "Critical control node — change window restricted to Saturday 02:00-04:00."
}
```

Required fields: `assetId`

Response `201 Created`: Full ICS asset object.

Error `400 Bad Request` if ICS metadata already exists for that asset:
```json
{ "status": 400, "message": "ICS metadata already exists for this asset" }
```

### 7.4 Update ICS Asset (partial)

`PATCH /api/v1/compliance/ics-assets/{id}`

```json
{
  "firmwareVersion": "V3.3.0",
  "lastPatchedAt": "2026-03-05T00:00:00.000Z",
  "knownVulnerabilities": null
}
```

Response `200 OK`: Updated ICS asset object.

### 7.5 Delete ICS Asset

`DELETE /api/v1/compliance/ics-assets/{id}`

Response `204 No Content`

---

## 8) Patch Records

Firmware/software patch history for ICS assets (IEC 62443 / NERC CIP).

### 8.1 List Patch Records (paged)

`GET /api/v1/compliance/patch-records`

Query params:

| Param | Type | Description |
|---|---|---|
| `assetId` | UUID | Optional. Filter by specific asset |
| `page` | int | Page number (0-based) |
| `size` | int | Page size (default 20) |

Response `200 OK` (paged):
```json
{
  "content": [
    {
      "id": "b8c9d0e1-0000-0000-0000-000000000001",
      "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
      "assetId": "c0e8e2a4-6d25-4c67-a620-c0222f6cb7bf",
      "assetName": "Siemens S7-300 PLC",
      "patchName": "Firmware security patch March 2026",
      "version": "V3.3.0",
      "appliedAt": "2026-03-05T02:30:00.000Z",
      "appliedByEmail": "ops@acme.com",
      "testEnvironmentValidated": true,
      "rollbackPlan": "Restore V3.2.7 firmware image from backup server.",
      "status": "APPLIED",
      "notes": "Applied during approved Saturday maintenance window.",
      "createdAt": "2026-03-01T08:00:00.000Z",
      "updatedAt": "2026-03-05T03:00:00.000Z"
    }
  ],
  "totalElements": 5,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

### 8.2 Get Patch Record by ID

`GET /api/v1/compliance/patch-records/{id}`

Response `200 OK`: Single patch record.

### 8.3 Create Patch Record

`POST /api/v1/compliance/patch-records`

Request:
```json
{
  "assetId": "c0e8e2a4-6d25-4c67-a620-c0222f6cb7bf",
  "patchName": "Firmware security patch March 2026",
  "version": "V3.3.0",
  "appliedAt": "2026-03-05T02:30:00.000Z",
  "appliedByEmail": "ops@acme.com",
  "testEnvironmentValidated": true,
  "rollbackPlan": "Restore V3.2.7 firmware image from backup server.",
  "status": "PLANNED",
  "notes": "Scheduled for Saturday maintenance window."
}
```

Required fields: `assetId`, `patchName`

Response `201 Created`: Full patch record.

### 8.4 Update Patch Record (partial)

`PATCH /api/v1/compliance/patch-records/{id}`

```json
{
  "status": "APPLIED",
  "appliedAt": "2026-03-05T02:30:00.000Z"
}
```

Response `200 OK`: Updated patch record.

### 8.5 Delete Patch Record

`DELETE /api/v1/compliance/patch-records/{id}`

Response `204 No Content`

---

## 9) PCI-DSS SAQ Records

Self-Assessment Questionnaire answers per PCI-DSS requirement.

### 9.1 List PCI SAQ Records

`GET /api/v1/compliance/pci-saq`

Returns all records ordered by `requirementNumber`.

Response `200 OK`:
```json
[
  {
    "id": "c9d0e1f2-0000-0000-0000-000000000001",
    "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
    "requirementNumber": "1.1",
    "requirementText": "Install and maintain a firewall configuration to protect cardholder data.",
    "complianceStatus": "YES",
    "compensatingControl": null,
    "evidenceUrl": "https://drive.example.com/firewall-config.pdf",
    "targetDate": null,
    "notes": "Firewall reviewed quarterly.",
    "createdAt": "2026-01-20T08:00:00.000Z",
    "updatedAt": "2026-03-01T10:00:00.000Z"
  },
  {
    "id": "c9d0e1f2-0000-0000-0000-000000000002",
    "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
    "requirementNumber": "6.3",
    "requirementText": "Protect all system components against known vulnerabilities by installing applicable security patches.",
    "complianceStatus": "COMPENSATING_CONTROL",
    "compensatingControl": "Air-gapped OT network with manual patch review process.",
    "evidenceUrl": null,
    "targetDate": "2026-09-01T00:00:00.000Z",
    "notes": null,
    "createdAt": "2026-01-20T08:00:00.000Z",
    "updatedAt": "2026-01-20T08:00:00.000Z"
  }
]
```

### 9.2 Get PCI SAQ Record by ID

`GET /api/v1/compliance/pci-saq/{id}`

Response `200 OK`: Single SAQ record.

### 9.3 Create / Upsert PCI SAQ Record

`POST /api/v1/compliance/pci-saq`

> Creates a new record. To update an existing one, use PATCH.

Request:
```json
{
  "requirementNumber": "1.1",
  "requirementText": "Install and maintain a firewall configuration to protect cardholder data.",
  "complianceStatus": "YES",
  "evidenceUrl": "https://drive.example.com/firewall-config.pdf",
  "notes": "Firewall reviewed quarterly."
}
```

Required fields: `requirementNumber`

Response `201 Created`: Full SAQ record.

### 9.4 Update PCI SAQ Record (partial)

`PATCH /api/v1/compliance/pci-saq/{id}`

```json
{
  "complianceStatus": "YES",
  "evidenceUrl": "https://drive.example.com/updated-firewall-policy.pdf"
}
```

Response `200 OK`: Updated SAQ record.

---

## 10) SLA Metrics

Monthly availability metrics for SOC 2 Availability criteria.

### 10.1 List SLA Metrics

`GET /api/v1/compliance/sla-metrics`

Returns all records ordered by year desc, month desc.

Response `200 OK`:
```json
[
  {
    "id": "d0e1f2a3-0000-0000-0000-000000000001",
    "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
    "month": 2,
    "year": 2026,
    "uptimePercent": 99.97,
    "plannedDowntimeMinutes": 30,
    "unplannedDowntimeMinutes": 8,
    "incidentCount": 2,
    "rtoMinutes": 15,
    "rpoMinutes": 60,
    "slaBreached": false,
    "notes": "Minor database failover on Feb 12.",
    "createdAt": "2026-03-01T08:00:00.000Z",
    "updatedAt": "2026-03-01T08:00:00.000Z"
  }
]
```

### 10.2 Get SLA Metric by ID

`GET /api/v1/compliance/sla-metrics/{id}`

Response `200 OK`: Single SLA metric.

### 10.3 Create SLA Metric

`POST /api/v1/compliance/sla-metrics`

> One record per month/year per organisation. Duplicate month+year returns `400`.

Request:
```json
{
  "month": 3,
  "year": 2026,
  "uptimePercent": 99.95,
  "plannedDowntimeMinutes": 60,
  "unplannedDowntimeMinutes": 0,
  "incidentCount": 1,
  "rtoMinutes": 10,
  "rpoMinutes": 60,
  "slaBreached": false,
  "notes": "Planned maintenance on March 8."
}
```

Required fields: `month` (1–12), `year`, `uptimePercent`

Response `201 Created`: Full SLA metric.

Error `400` if record already exists for that month/year:
```json
{ "status": 400, "message": "SLA metric already exists for 2026-3" }
```

### 10.4 Update SLA Metric (partial)

`PATCH /api/v1/compliance/sla-metrics/{id}`

```json
{
  "unplannedDowntimeMinutes": 12,
  "slaBreached": false,
  "notes": "Corrected downtime figure after post-mortem."
}
```

Response `200 OK`: Updated SLA metric.

---

## 11) Vulnerability Scans

Scan result tracking for PCI-DSS Requirement 11.3 and ICS/OT environments.

### 11.1 List Vulnerability Scans (paged)

`GET /api/v1/compliance/vulnerability-scans`

Query params: `page`, `size`, `sort`

Response `200 OK` (paged):
```json
{
  "content": [
    {
      "id": "e1f2a3b4-0000-0000-0000-000000000001",
      "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
      "scanDate": "2026-03-01T00:00:00.000Z",
      "scannerTool": "Nessus Professional",
      "scanType": "INTERNAL",
      "criticalCount": 0,
      "highCount": 2,
      "mediumCount": 7,
      "lowCount": 14,
      "status": "PENDING_REMEDIATION",
      "reportUrl": "https://drive.example.com/scan-report-mar2026.pdf",
      "nextScanDue": "2026-06-01T00:00:00.000Z",
      "notes": "High findings relate to unpatched web server components.",
      "createdAt": "2026-03-01T12:00:00.000Z",
      "updatedAt": "2026-03-01T12:00:00.000Z"
    }
  ],
  "totalElements": 8,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

### 11.2 Get Vulnerability Scan by ID

`GET /api/v1/compliance/vulnerability-scans/{id}`

Response `200 OK`: Single scan record.

### 11.3 Create Vulnerability Scan

`POST /api/v1/compliance/vulnerability-scans`

Request:
```json
{
  "scanDate": "2026-03-01T00:00:00.000Z",
  "scannerTool": "Nessus Professional",
  "scanType": "INTERNAL",
  "criticalCount": 0,
  "highCount": 2,
  "mediumCount": 7,
  "lowCount": 14,
  "status": "PENDING_REMEDIATION",
  "reportUrl": "https://drive.example.com/scan-report-mar2026.pdf",
  "nextScanDue": "2026-06-01T00:00:00.000Z",
  "notes": "High findings relate to unpatched web server components."
}
```

Required fields: `scanDate`, `scanType`

Response `201 Created`: Full scan record.

### 11.4 Update Vulnerability Scan (partial)

`PATCH /api/v1/compliance/vulnerability-scans/{id}`

```json
{
  "status": "PASS",
  "highCount": 0,
  "notes": "All high findings remediated. Re-scan confirmed pass."
}
```

Response `200 OK`: Updated scan record.

### 11.5 Delete Vulnerability Scan

`DELETE /api/v1/compliance/vulnerability-scans/{id}`

Response `204 No Content`

---

## 12) Regulatory Filings

Regulatory submission tracking for BOG, SEC, NCA, and other regulators.

### 12.1 List Regulatory Filings

`GET /api/v1/compliance/regulatory-filings`

Query params (optional): `status` — filter by `FilingStatus`

Returns filings ordered by `dueDate` ascending.

Response `200 OK`:
```json
[
  {
    "id": "f2a3b4c5-0000-0000-0000-000000000001",
    "organisationId": "94dc0c14-3bd2-40fb-b36b-cc74b7cbd165",
    "filingType": "Quarterly ICT Risk Report",
    "regulator": "BOG",
    "dueDate": "2026-04-15T00:00:00.000Z",
    "submittedAt": null,
    "reference": null,
    "status": "PENDING",
    "notes": "Q1 2026 submission. Drafted — awaiting CISO sign-off.",
    "createdAt": "2026-03-01T08:00:00.000Z",
    "updatedAt": "2026-03-01T08:00:00.000Z"
  }
]
```

### 12.2 Get Regulatory Filing by ID

`GET /api/v1/compliance/regulatory-filings/{id}`

Response `200 OK`: Single filing object.

### 12.3 Create Regulatory Filing

`POST /api/v1/compliance/regulatory-filings`

Request:
```json
{
  "filingType": "Quarterly ICT Risk Report",
  "regulator": "BOG",
  "dueDate": "2026-04-15T00:00:00.000Z",
  "status": "PENDING",
  "notes": "Q1 2026 submission."
}
```

Required fields: `filingType`, `regulator`, `dueDate`

Response `201 Created`: Full filing object.

### 12.4 Update Regulatory Filing (partial)

`PATCH /api/v1/compliance/regulatory-filings/{id}`

```json
{
  "status": "SUBMITTED",
  "submittedAt": "2026-04-12T10:30:00.000Z",
  "reference": "BOG-ICT-2026-Q1-0042"
}
```

Response `200 OK`: Updated filing object.

### 12.5 Delete Regulatory Filing

`DELETE /api/v1/compliance/regulatory-filings/{id}`

Response `204 No Content`

---

## Common Error Responses

### 400 Bad Request — Validation error
```json
{
  "status": 400,
  "errors": {
    "title": "Title is required",
    "likelihood": "must be between 1 and 5"
  }
}
```

### 400 Bad Request — Business logic error
```json
{
  "status": 400,
  "message": "SLA metric already exists for 2026-3"
}
```

### 403 Forbidden — Missing tenant context or insufficient role
```json
{
  "status": 403,
  "message": "Tenant context is required. Provide a valid X-Organisation-Id header."
}
```

### 404 Not Found
```json
{
  "status": 404,
  "message": "Risk not found"
}
```

---

## Quick Reference

| Resource | Base Path | List | Get | Create | Update | Delete |
|---|---|---|---|---|---|---|
| Compliance Controls | `/compliance/controls` | GET | GET `/{id}` | POST | PATCH `/{id}` | DELETE `/{id}` |
| BOG Controls | `/compliance/bog-controls` | GET | GET `/{id}` | POST | PATCH `/{id}` | DELETE `/{id}` |
| Risk Register | `/compliance/risks` | GET (paged) | GET `/{id}` | POST | PATCH `/{id}` | DELETE `/{id}` |
| Security Incidents | `/compliance/incidents` | GET (paged) | GET `/{id}` | POST | PATCH `/{id}` | DELETE `/{id}` |
| Security Policies | `/compliance/policies` | GET | GET `/{id}` | POST | PATCH `/{id}` | DELETE `/{id}` |
| Security Zones | `/compliance/security-zones` | GET | GET `/{id}` | POST | PATCH `/{id}` | DELETE `/{id}` |
| ICS Assets | `/compliance/ics-assets` | GET | GET `/{id}` | POST | PATCH `/{id}` | DELETE `/{id}` |
| Patch Records | `/compliance/patch-records` | GET (paged) | GET `/{id}` | POST | PATCH `/{id}` | DELETE `/{id}` |
| PCI SAQ Records | `/compliance/pci-saq` | GET | GET `/{id}` | POST | PATCH `/{id}` | — |
| SLA Metrics | `/compliance/sla-metrics` | GET | GET `/{id}` | POST | PATCH `/{id}` | — |
| Vulnerability Scans | `/compliance/vulnerability-scans` | GET (paged) | GET `/{id}` | POST | PATCH `/{id}` | DELETE `/{id}` |
| Regulatory Filings | `/compliance/regulatory-filings` | GET | GET `/{id}` | POST | PATCH `/{id}` | DELETE `/{id}` |

> All paths are relative to `/api/v1`.

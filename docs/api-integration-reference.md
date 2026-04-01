# Enterprise Asset Manager API Integration Reference

Generated from backend source code plus the live Spring-generated OpenAPI surface.

## Table of Contents
- **Onboarding**
- [Tenant Onboarding Service](#tenant-onboarding-service)
- [POST /api/v1/tenant/register](#post-api-v1-tenant-register)
- [SSO Authentication Service](#sso-authentication-service)
- [GET /api/v1/auth/sso/oauth2/authorize](#get-api-v1-auth-sso-oauth2-authorize)
- [GET /api/v1/auth/sso/oauth2/callback](#get-api-v1-auth-sso-oauth2-callback)
- [POST /api/v1/auth/sso/saml/acs](#post-api-v1-auth-sso-saml-acs)
- [GET /api/v1/auth/sso/saml/initiate](#get-api-v1-auth-sso-saml-initiate)
- [Authentication Service](#authentication-service)
- [POST /api/v1/auth/forgot-password](#post-api-v1-auth-forgot-password)
- [POST /api/v1/auth/login](#post-api-v1-auth-login)
- [POST /api/v1/auth/logout](#post-api-v1-auth-logout)
- [GET /api/v1/auth/profile](#get-api-v1-auth-profile)
- [POST /api/v1/auth/refresh](#post-api-v1-auth-refresh)
- [POST /api/v1/auth/register](#post-api-v1-auth-register)
- [POST /api/v1/auth/reset-password](#post-api-v1-auth-reset-password)
- [MFA Service](#mfa-service)
- [DELETE /api/v1/mfa/disable](#delete-api-v1-mfa-disable)
- [POST /api/v1/mfa/setup](#post-api-v1-mfa-setup)
- [POST /api/v1/mfa/verify](#post-api-v1-mfa-verify)
- **Organisation Setup**
- [SSO Configuration Service](#sso-configuration-service)
- [GET /api/v1/organisations/{orgId}/sso](#get-api-v1-organisations-orgid-sso)
- [PUT /api/v1/organisations/{orgId}/sso/oauth2](#put-api-v1-organisations-orgid-sso-oauth2)
- [PUT /api/v1/organisations/{orgId}/sso/saml](#put-api-v1-organisations-orgid-sso-saml)
- [PATCH /api/v1/organisations/{orgId}/sso/toggle](#patch-api-v1-organisations-orgid-sso-toggle)
- [Organisation Management Service](#organisation-management-service)
- [GET /api/v1/organisations](#get-api-v1-organisations)
- [POST /api/v1/organisations](#post-api-v1-organisations)
- [DELETE /api/v1/organisations/{id}](#delete-api-v1-organisations-id)
- [GET /api/v1/organisations/{id}](#get-api-v1-organisations-id)
- [PATCH /api/v1/organisations/{id}](#patch-api-v1-organisations-id)
- [PUT /api/v1/organisations/{id}](#put-api-v1-organisations-id)
- [Role Management Service](#role-management-service)
- [GET /api/v1/roles](#get-api-v1-roles)
- [POST /api/v1/roles](#post-api-v1-roles)
- [GET /api/v1/roles/by-name](#get-api-v1-roles-by-name)
- [DELETE /api/v1/roles/{id}](#delete-api-v1-roles-id)
- [GET /api/v1/roles/{id}](#get-api-v1-roles-id)
- [PATCH /api/v1/roles/{id}](#patch-api-v1-roles-id)
- [PUT /api/v1/roles/{id}](#put-api-v1-roles-id)
- [User Management Service](#user-management-service)
- [GET /api/v1/users](#get-api-v1-users)
- [POST /api/v1/users](#post-api-v1-users)
- [GET /api/v1/users/{id}](#get-api-v1-users-id)
- [PATCH /api/v1/users/{id}](#patch-api-v1-users-id)
- [PUT /api/v1/users/{id}](#put-api-v1-users-id)
- [PUT /api/v1/users/{id}/deactivate](#put-api-v1-users-id-deactivate)
- [PUT /api/v1/users/{id}/role](#put-api-v1-users-id-role)
- [Department Management Service](#department-management-service)
- [GET /api/v1/departments](#get-api-v1-departments)
- [POST /api/v1/departments](#post-api-v1-departments)
- [DELETE /api/v1/departments/{id}](#delete-api-v1-departments-id)
- [GET /api/v1/departments/{id}](#get-api-v1-departments-id)
- [PATCH /api/v1/departments/{id}](#patch-api-v1-departments-id)
- [PUT /api/v1/departments/{id}](#put-api-v1-departments-id)
- [Location Management Service](#location-management-service)
- [GET /api/v1/locations](#get-api-v1-locations)
- [POST /api/v1/locations](#post-api-v1-locations)
- [DELETE /api/v1/locations/{id}](#delete-api-v1-locations-id)
- [GET /api/v1/locations/{id}](#get-api-v1-locations-id)
- [PATCH /api/v1/locations/{id}](#patch-api-v1-locations-id)
- [PUT /api/v1/locations/{id}](#put-api-v1-locations-id)
- [GET /api/v1/locations/{parentId}/sub-locations](#get-api-v1-locations-parentid-sub-locations)
- [Category Management Service](#category-management-service)
- [GET /api/v1/categories](#get-api-v1-categories)
- [POST /api/v1/categories](#post-api-v1-categories)
- [DELETE /api/v1/categories/{id}](#delete-api-v1-categories-id)
- [GET /api/v1/categories/{id}](#get-api-v1-categories-id)
- [PATCH /api/v1/categories/{id}](#patch-api-v1-categories-id)
- [PUT /api/v1/categories/{id}](#put-api-v1-categories-id)
- [GET /api/v1/categories/{parentId}/sub-categories](#get-api-v1-categories-parentid-sub-categories)
- **Procurement Setup**
- [Supplier Management Service](#supplier-management-service)
- [GET /api/v1/suppliers](#get-api-v1-suppliers)
- [POST /api/v1/suppliers](#post-api-v1-suppliers)
- [GET /api/v1/suppliers/by-email](#get-api-v1-suppliers-by-email)
- [DELETE /api/v1/suppliers/{id}](#delete-api-v1-suppliers-id)
- [GET /api/v1/suppliers/{id}](#get-api-v1-suppliers-id)
- [PATCH /api/v1/suppliers/{id}](#patch-api-v1-suppliers-id)
- [PUT /api/v1/suppliers/{id}](#put-api-v1-suppliers-id)
- [Purchase Order Service](#purchase-order-service)
- [GET /api/v1/purchase-orders](#get-api-v1-purchase-orders)
- [POST /api/v1/purchase-orders](#post-api-v1-purchase-orders)
- [DELETE /api/v1/purchase-orders/{id}](#delete-api-v1-purchase-orders-id)
- [GET /api/v1/purchase-orders/{id}](#get-api-v1-purchase-orders-id)
- [PATCH /api/v1/purchase-orders/{id}](#patch-api-v1-purchase-orders-id)
- [PUT /api/v1/purchase-orders/{id}](#put-api-v1-purchase-orders-id)
- [POST /api/v1/purchase-orders/{id}/approve](#post-api-v1-purchase-orders-id-approve)
- [POST /api/v1/purchase-orders/{id}/reject](#post-api-v1-purchase-orders-id-reject)
- [Budget Service](#budget-service)
- [GET /api/v1/budgets](#get-api-v1-budgets)
- [POST /api/v1/budgets](#post-api-v1-budgets)
- [DELETE /api/v1/budgets/{id}](#delete-api-v1-budgets-id)
- [GET /api/v1/budgets/{id}](#get-api-v1-budgets-id)
- [PATCH /api/v1/budgets/{id}](#patch-api-v1-budgets-id)
- [PUT /api/v1/budgets/{id}](#put-api-v1-budgets-id)
- [POST /api/v1/budgets/{id}/spend](#post-api-v1-budgets-id-spend)
- [Contract Service](#contract-service)
- [GET /api/v1/contracts](#get-api-v1-contracts)
- [POST /api/v1/contracts](#post-api-v1-contracts)
- [GET /api/v1/contracts/expiring-soon](#get-api-v1-contracts-expiring-soon)
- [DELETE /api/v1/contracts/{id}](#delete-api-v1-contracts-id)
- [GET /api/v1/contracts/{id}](#get-api-v1-contracts-id)
- [PATCH /api/v1/contracts/{id}](#patch-api-v1-contracts-id)
- [PUT /api/v1/contracts/{id}](#put-api-v1-contracts-id)
- **Asset Setup**
- [Depreciation Policy Service](#depreciation-policy-service)
- [GET /api/v1/depreciation-policies](#get-api-v1-depreciation-policies)
- [POST /api/v1/depreciation-policies](#post-api-v1-depreciation-policies)
- [DELETE /api/v1/depreciation-policies/{id}](#delete-api-v1-depreciation-policies-id)
- [GET /api/v1/depreciation-policies/{id}](#get-api-v1-depreciation-policies-id)
- [PATCH /api/v1/depreciation-policies/{id}](#patch-api-v1-depreciation-policies-id)
- [PUT /api/v1/depreciation-policies/{id}](#put-api-v1-depreciation-policies-id)
- **Asset Lifecycle**
- [Asset Custom Field Service](#asset-custom-field-service)
- [GET /api/v1/assets/{assetId}/custom-fields](#get-api-v1-assets-assetid-custom-fields)
- [POST /api/v1/assets/{assetId}/custom-fields](#post-api-v1-assets-assetid-custom-fields)
- [DELETE /api/v1/assets/{assetId}/custom-fields/{fieldId}](#delete-api-v1-assets-assetid-custom-fields-fieldid)
- [PUT /api/v1/assets/{assetId}/custom-fields/{fieldId}](#put-api-v1-assets-assetid-custom-fields-fieldid)
- [Asset Service](#asset-service)
- [GET /api/v1/assets](#get-api-v1-assets)
- [POST /api/v1/assets](#post-api-v1-assets)
- [POST /api/v1/assets/import](#post-api-v1-assets-import)
- [DELETE /api/v1/assets/{id}](#delete-api-v1-assets-id)
- [GET /api/v1/assets/{id}](#get-api-v1-assets-id)
- [PATCH /api/v1/assets/{id}](#patch-api-v1-assets-id)
- [PUT /api/v1/assets/{id}](#put-api-v1-assets-id)
- [DELETE /api/v1/assets/{id}/assign-user](#delete-api-v1-assets-id-assign-user)
- [POST /api/v1/assets/{id}/assign-user/{userId}](#post-api-v1-assets-id-assign-user-userid)
- [POST /api/v1/assets/{id}/assign/{departmentId}](#post-api-v1-assets-id-assign-departmentid)
- [GET /api/v1/assets/{id}/history](#get-api-v1-assets-id-history)
- [GET /api/v1/assets/{id}/qrcode](#get-api-v1-assets-id-qrcode)
- [Bulk Operations Service](#bulk-operations-service)
- [POST /api/v1/bulk/assets/export](#post-api-v1-bulk-assets-export)
- [POST /api/v1/bulk/assets/import](#post-api-v1-bulk-assets-import)
- [POST /api/v1/bulk/purchase-orders/export](#post-api-v1-bulk-purchase-orders-export)
- [POST /api/v1/bulk/suppliers/export](#post-api-v1-bulk-suppliers-export)
- [Import Jobs Service](#import-jobs-service)
- [POST /api/v1/import-jobs/assets](#post-api-v1-import-jobs-assets)
- [GET /api/v1/import-jobs/{jobId}](#get-api-v1-import-jobs-jobid)
- [Network Discovery Service](#network-discovery-service)
- [GET /api/v1/discovery/devices](#get-api-v1-discovery-devices)
- [DELETE /api/v1/discovery/devices/{id}](#delete-api-v1-discovery-devices-id)
- [GET /api/v1/discovery/devices/{id}](#get-api-v1-discovery-devices-id)
- [POST /api/v1/discovery/devices/{id}/promote](#post-api-v1-discovery-devices-id-promote)
- [POST /api/v1/discovery/scan](#post-api-v1-discovery-scan)
- [GET /api/v1/discovery/summary](#get-api-v1-discovery-summary)
- [Cloud Asset Service](#cloud-asset-service)
- [GET /api/v1/cloud-assets](#get-api-v1-cloud-assets)
- [POST /api/v1/cloud-assets](#post-api-v1-cloud-assets)
- [GET /api/v1/cloud-assets/cost-summary](#get-api-v1-cloud-assets-cost-summary)
- [DELETE /api/v1/cloud-assets/{id}](#delete-api-v1-cloud-assets-id)
- [GET /api/v1/cloud-assets/{id}](#get-api-v1-cloud-assets-id)
- [PUT /api/v1/cloud-assets/{id}](#put-api-v1-cloud-assets-id)
- [POST /api/v1/cloud-assets/{id}/cost](#post-api-v1-cloud-assets-id-cost)
- [Software License Service](#software-license-service)
- [GET /api/v1/licenses](#get-api-v1-licenses)
- [POST /api/v1/licenses](#post-api-v1-licenses)
- [GET /api/v1/licenses/expiring-soon](#get-api-v1-licenses-expiring-soon)
- [GET /api/v1/licenses/over-allocated](#get-api-v1-licenses-over-allocated)
- [GET /api/v1/licenses/utilization](#get-api-v1-licenses-utilization)
- [DELETE /api/v1/licenses/{id}](#delete-api-v1-licenses-id)
- [GET /api/v1/licenses/{id}](#get-api-v1-licenses-id)
- [PATCH /api/v1/licenses/{id}](#patch-api-v1-licenses-id)
- [PUT /api/v1/licenses/{id}](#put-api-v1-licenses-id)
- [Vendor Performance Service](#vendor-performance-service)
- [GET /api/v1/vendor-reviews](#get-api-v1-vendor-reviews)
- [POST /api/v1/vendor-reviews](#post-api-v1-vendor-reviews)
- [GET /api/v1/vendor-reviews/suppliers/{supplierId}/summary](#get-api-v1-vendor-reviews-suppliers-supplierid-summary)
- [DELETE /api/v1/vendor-reviews/{id}](#delete-api-v1-vendor-reviews-id)
- [GET /api/v1/vendor-reviews/{id}](#get-api-v1-vendor-reviews-id)
- [PUT /api/v1/vendor-reviews/{id}](#put-api-v1-vendor-reviews-id)
- [Maintenance Service](#maintenance-service)
- [GET /api/v1/maintenance](#get-api-v1-maintenance)
- [POST /api/v1/maintenance](#post-api-v1-maintenance)
- [DELETE /api/v1/maintenance/{id}](#delete-api-v1-maintenance-id)
- [GET /api/v1/maintenance/{id}](#get-api-v1-maintenance-id)
- [PATCH /api/v1/maintenance/{id}](#patch-api-v1-maintenance-id)
- [PUT /api/v1/maintenance/{id}](#put-api-v1-maintenance-id)
- [POST /api/v1/maintenance/{id}/complete](#post-api-v1-maintenance-id-complete)
- [Asset Transfer Service](#asset-transfer-service)
- [GET /api/v1/asset-transfers](#get-api-v1-asset-transfers)
- [POST /api/v1/asset-transfers](#post-api-v1-asset-transfers)
- [DELETE /api/v1/asset-transfers/{id}](#delete-api-v1-asset-transfers-id)
- [GET /api/v1/asset-transfers/{id}](#get-api-v1-asset-transfers-id)
- [POST /api/v1/asset-transfers/{id}/approve](#post-api-v1-asset-transfers-id-approve)
- [POST /api/v1/asset-transfers/{id}/complete](#post-api-v1-asset-transfers-id-complete)
- [POST /api/v1/asset-transfers/{id}/reject](#post-api-v1-asset-transfers-id-reject)
- [Audit Service](#audit-service)
- [GET /api/v1/audits](#get-api-v1-audits)
- [POST /api/v1/audits](#post-api-v1-audits)
- [DELETE /api/v1/audits/{id}](#delete-api-v1-audits-id)
- [GET /api/v1/audits/{id}](#get-api-v1-audits-id)
- [PATCH /api/v1/audits/{id}/status](#patch-api-v1-audits-id-status)
- [Audit Event Service](#audit-event-service)
- [GET /api/v1/audit-events](#get-api-v1-audit-events)
- [GET /api/v1/audit-events/{id}](#get-api-v1-audit-events-id)
- [Disposal Service](#disposal-service)
- [GET /api/v1/disposals](#get-api-v1-disposals)
- [POST /api/v1/disposals](#post-api-v1-disposals)
- [DELETE /api/v1/disposals/{id}](#delete-api-v1-disposals-id)
- [GET /api/v1/disposals/{id}](#get-api-v1-disposals-id)
- [PATCH /api/v1/disposals/{id}](#patch-api-v1-disposals-id)
- [PUT /api/v1/disposals/{id}](#put-api-v1-disposals-id)
- **Governance**
- [Compliance Service](#compliance-service)
- [GET /api/v1/compliance/bog-controls](#get-api-v1-compliance-bog-controls)
- [POST /api/v1/compliance/bog-controls](#post-api-v1-compliance-bog-controls)
- [DELETE /api/v1/compliance/bog-controls/{id}](#delete-api-v1-compliance-bog-controls-id)
- [GET /api/v1/compliance/bog-controls/{id}](#get-api-v1-compliance-bog-controls-id)
- [PATCH /api/v1/compliance/bog-controls/{id}](#patch-api-v1-compliance-bog-controls-id)
- [GET /api/v1/compliance/controls](#get-api-v1-compliance-controls)
- [POST /api/v1/compliance/controls](#post-api-v1-compliance-controls)
- [DELETE /api/v1/compliance/controls/{id}](#delete-api-v1-compliance-controls-id)
- [GET /api/v1/compliance/controls/{id}](#get-api-v1-compliance-controls-id)
- [PATCH /api/v1/compliance/controls/{id}](#patch-api-v1-compliance-controls-id)
- [GET /api/v1/compliance/ics-assets](#get-api-v1-compliance-ics-assets)
- [POST /api/v1/compliance/ics-assets](#post-api-v1-compliance-ics-assets)
- [DELETE /api/v1/compliance/ics-assets/{id}](#delete-api-v1-compliance-ics-assets-id)
- [GET /api/v1/compliance/ics-assets/{id}](#get-api-v1-compliance-ics-assets-id)
- [PATCH /api/v1/compliance/ics-assets/{id}](#patch-api-v1-compliance-ics-assets-id)
- [GET /api/v1/compliance/incidents](#get-api-v1-compliance-incidents)
- [POST /api/v1/compliance/incidents](#post-api-v1-compliance-incidents)
- [DELETE /api/v1/compliance/incidents/{id}](#delete-api-v1-compliance-incidents-id)
- [GET /api/v1/compliance/incidents/{id}](#get-api-v1-compliance-incidents-id)
- [PATCH /api/v1/compliance/incidents/{id}](#patch-api-v1-compliance-incidents-id)
- [GET /api/v1/compliance/patch-records](#get-api-v1-compliance-patch-records)
- [POST /api/v1/compliance/patch-records](#post-api-v1-compliance-patch-records)
- [DELETE /api/v1/compliance/patch-records/{id}](#delete-api-v1-compliance-patch-records-id)
- [GET /api/v1/compliance/patch-records/{id}](#get-api-v1-compliance-patch-records-id)
- [PATCH /api/v1/compliance/patch-records/{id}](#patch-api-v1-compliance-patch-records-id)
- [GET /api/v1/compliance/pci-saq](#get-api-v1-compliance-pci-saq)
- [POST /api/v1/compliance/pci-saq](#post-api-v1-compliance-pci-saq)
- [GET /api/v1/compliance/pci-saq/{id}](#get-api-v1-compliance-pci-saq-id)
- [PATCH /api/v1/compliance/pci-saq/{id}](#patch-api-v1-compliance-pci-saq-id)
- [GET /api/v1/compliance/policies](#get-api-v1-compliance-policies)
- [POST /api/v1/compliance/policies](#post-api-v1-compliance-policies)
- [DELETE /api/v1/compliance/policies/{id}](#delete-api-v1-compliance-policies-id)
- [GET /api/v1/compliance/policies/{id}](#get-api-v1-compliance-policies-id)
- [PATCH /api/v1/compliance/policies/{id}](#patch-api-v1-compliance-policies-id)
- [GET /api/v1/compliance/regulatory-filings](#get-api-v1-compliance-regulatory-filings)
- [POST /api/v1/compliance/regulatory-filings](#post-api-v1-compliance-regulatory-filings)
- [DELETE /api/v1/compliance/regulatory-filings/{id}](#delete-api-v1-compliance-regulatory-filings-id)
- [GET /api/v1/compliance/regulatory-filings/{id}](#get-api-v1-compliance-regulatory-filings-id)
- [PATCH /api/v1/compliance/regulatory-filings/{id}](#patch-api-v1-compliance-regulatory-filings-id)
- [GET /api/v1/compliance/risks](#get-api-v1-compliance-risks)
- [POST /api/v1/compliance/risks](#post-api-v1-compliance-risks)
- [DELETE /api/v1/compliance/risks/{id}](#delete-api-v1-compliance-risks-id)
- [GET /api/v1/compliance/risks/{id}](#get-api-v1-compliance-risks-id)
- [PATCH /api/v1/compliance/risks/{id}](#patch-api-v1-compliance-risks-id)
- [GET /api/v1/compliance/security-zones](#get-api-v1-compliance-security-zones)
- [POST /api/v1/compliance/security-zones](#post-api-v1-compliance-security-zones)
- [DELETE /api/v1/compliance/security-zones/{id}](#delete-api-v1-compliance-security-zones-id)
- [GET /api/v1/compliance/security-zones/{id}](#get-api-v1-compliance-security-zones-id)
- [PATCH /api/v1/compliance/security-zones/{id}](#patch-api-v1-compliance-security-zones-id)
- [GET /api/v1/compliance/sla-metrics](#get-api-v1-compliance-sla-metrics)
- [POST /api/v1/compliance/sla-metrics](#post-api-v1-compliance-sla-metrics)
- [GET /api/v1/compliance/sla-metrics/{id}](#get-api-v1-compliance-sla-metrics-id)
- [PATCH /api/v1/compliance/sla-metrics/{id}](#patch-api-v1-compliance-sla-metrics-id)
- [GET /api/v1/compliance/vulnerability-scans](#get-api-v1-compliance-vulnerability-scans)
- [POST /api/v1/compliance/vulnerability-scans](#post-api-v1-compliance-vulnerability-scans)
- [DELETE /api/v1/compliance/vulnerability-scans/{id}](#delete-api-v1-compliance-vulnerability-scans-id)
- [GET /api/v1/compliance/vulnerability-scans/{id}](#get-api-v1-compliance-vulnerability-scans-id)
- [PATCH /api/v1/compliance/vulnerability-scans/{id}](#patch-api-v1-compliance-vulnerability-scans-id)
- **Operations & Insights**
- [AI Insights Service](#ai-insights-service)
- [GET /api/v1/ai/insights](#get-api-v1-ai-insights)
- [POST /api/v1/ai/insights/generate](#post-api-v1-ai-insights-generate)
- [GET /api/v1/ai/insights/summary](#get-api-v1-ai-insights-summary)
- [GET /api/v1/ai/insights/{id}](#get-api-v1-ai-insights-id)
- [POST /api/v1/ai/insights/{id}/resolve](#post-api-v1-ai-insights-id-resolve)
- [Dashboard Service](#dashboard-service)
- [GET /api/v1/dashboard/assets-by-department](#get-api-v1-dashboard-assets-by-department)
- [GET /api/v1/dashboard/assets-by-status](#get-api-v1-dashboard-assets-by-status)
- [GET /api/v1/dashboard/depreciation-summary](#get-api-v1-dashboard-depreciation-summary)
- [GET /api/v1/dashboard/maintenance-alerts](#get-api-v1-dashboard-maintenance-alerts)
- [GET /api/v1/dashboard/summary](#get-api-v1-dashboard-summary)
- [Analytics Service](#analytics-service)
- [GET /api/v1/analytics/assets](#get-api-v1-analytics-assets)
- [GET /api/v1/analytics/depreciation-trends](#get-api-v1-analytics-depreciation-trends)
- [GET /api/v1/analytics/financial](#get-api-v1-analytics-financial)
- [GET /api/v1/analytics/maintenance](#get-api-v1-analytics-maintenance)
- [GET /api/v1/analytics/purchase-orders](#get-api-v1-analytics-purchase-orders)
- [Reports Service](#reports-service)
- [POST /api/v1/reports/assets](#post-api-v1-reports-assets)
- [GET /api/v1/reports/assets/{reportId}/download](#get-api-v1-reports-assets-reportid-download)
- [POST /api/v1/reports/financial](#post-api-v1-reports-financial)
- [GET /api/v1/reports/financial/{reportId}/download](#get-api-v1-reports-financial-reportid-download)
- [GET /api/v1/reports/history](#get-api-v1-reports-history)
- [POST /api/v1/reports/maintenance](#post-api-v1-reports-maintenance)
- [GET /api/v1/reports/maintenance/{reportId}/download](#get-api-v1-reports-maintenance-reportid-download)
- [DELETE /api/v1/reports/{reportId}](#delete-api-v1-reports-reportid)
- [GET /api/v1/reports/{reportId}/download](#get-api-v1-reports-reportid-download)
- [Notifications Service](#notifications-service)
- [DELETE /api/v1/notifications](#delete-api-v1-notifications)
- [GET /api/v1/notifications](#get-api-v1-notifications)
- [PATCH /api/v1/notifications/mark-all-read](#patch-api-v1-notifications-mark-all-read)
- [GET /api/v1/notifications/preferences](#get-api-v1-notifications-preferences)
- [PATCH /api/v1/notifications/preferences](#patch-api-v1-notifications-preferences)
- [GET /api/v1/notifications/summary](#get-api-v1-notifications-summary)
- [DELETE /api/v1/notifications/{notificationId}](#delete-api-v1-notifications-notificationid)
- [PATCH /api/v1/notifications/{notificationId}/read](#patch-api-v1-notifications-notificationid-read)
- **Integrations**
- [Webhooks Service](#webhooks-service)
- [GET /api/v1/webhooks](#get-api-v1-webhooks)
- [POST /api/v1/webhooks](#post-api-v1-webhooks)
- [DELETE /api/v1/webhooks/{id}](#delete-api-v1-webhooks-id)
- [GET /api/v1/webhooks/{id}](#get-api-v1-webhooks-id)
- [PATCH /api/v1/webhooks/{id}](#patch-api-v1-webhooks-id)
- [GET /api/v1/webhooks/{id}/deliveries](#get-api-v1-webhooks-id-deliveries)
- [GET /api/v1/webhooks/{id}/deliveries/{deliveryId}](#get-api-v1-webhooks-id-deliveries-deliveryid)
- [POST /api/v1/webhooks/{id}/test](#post-api-v1-webhooks-id-test)
- **Commercial**
- [Billing Service](#billing-service)
- [POST /api/v1/billing/checkout](#post-api-v1-billing-checkout)
- [POST /api/v1/billing/checkout/verify](#post-api-v1-billing-checkout-verify)
- [GET /api/v1/billing/plans](#get-api-v1-billing-plans)
- [GET /api/v1/billing/subscription](#get-api-v1-billing-subscription)
- [PATCH /api/v1/billing/subscription/auto-renew](#patch-api-v1-billing-subscription-auto-renew)
- [POST /api/v1/billing/webhooks/paystack](#post-api-v1-billing-webhooks-paystack)
- **Platform**
- [Health & Monitoring Service](#health-monitoring-service)
- [GET /api/v1/health](#get-api-v1-health)
- [GET /api/v1/health/detailed](#get-api-v1-health-detailed)
- [GET /api/v1/metrics](#get-api-v1-metrics)
- [GET /api/v1/metrics/database](#get-api-v1-metrics-database)
- [GET /api/v1/metrics/endpoints](#get-api-v1-metrics-endpoints)
- [GET /api/v1/metrics/errors](#get-api-v1-metrics-errors)
- [GET /api/v1/metrics/throughput](#get-api-v1-metrics-throughput)

## Global Conventions

- Protected routes use `Authorization: Bearer <jwt>`.
- For tenant-scoped protected routes, include `X-Organisation-Id` from the active organisation context.
- All `/api/**` requests pass through rate limiting and request-correlation middleware.
- Some security and filter failures are emitted with `sendError()`, so `401`, `403`, and `429` bodies are not guaranteed to be stable JSON. The status code is the reliable contract.

Shared validation / exception envelope:

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```

## Tenant Onboarding Service

Stage: **Onboarding**

### POST /api/v1/tenant/register

User type: **Guest**

Description: Create or trigger a new register operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| organisationName | string | yes | required |
| organisationContactEmail | string | no | optional |
| country | string | no | optional |
| address | string | no | optional |
| timezone | string | no | optional |
| industry | string | no | optional |
| registrationNumber | string | no | optional |
| taxId | string | no | optional |
| contactPhone | string | no | optional |
| adminFirstName | string | yes | required |
| adminLastName | string | yes | required |
| adminEmail | string | yes | required |
| adminPhone | string | no | optional |
| adminJobTitle | string | no | optional |
| password | string | yes | required, minLength 8, maxLength 2147483647 |

```json
{
  "organisationName": "string",
  "organisationContactEmail": "jane.admin@example.com",
  "country": "string",
  "address": "string",
  "timezone": "09:00",
  "industry": "string",
  "registrationNumber": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "contactPhone": "+233555123456",
  "adminFirstName": "string",
  "adminLastName": "string",
  "adminEmail": "jane.admin@example.com",
  "adminPhone": "+233555123456",
  "adminJobTitle": "string",
  "password": "Password123"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| organisationId | string (uuid) | no | optional, format `uuid` |
| organisationName | string | no | optional |
| userId | string (uuid) | no | optional, format `uuid` |
| email | string | no | optional |
| firstName | string | no | optional |
| lastName | string | no | optional |
| role | string | no | optional |
| token | string | no | optional |
| expiresIn | integer (int64) | no | optional, format `int64` |

```json
{
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "organisationName": "string",
  "userId": "11111111-1111-1111-1111-111111111111",
  "email": "jane.admin@example.com",
  "firstName": "string",
  "lastName": "string",
  "role": "string",
  "token": "<token>",
  "expiresIn": "string"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- None. This is the first onboarding call.

Source reference: `src/main/java/com/example/demo/controllers/v1/TenantController.java`

## SSO Authentication Service

Stage: **Onboarding**

### GET /api/v1/auth/sso/oauth2/authorize

User type: **Guest**

Description: Build the OAuth2 redirect URL for an organisation SSO login.

When to call: Call when the user chooses OAuth2 SSO and the frontend needs the IdP URL.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| orgId | query | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| authorizationUrl | string | no | optional |

```json
{
  "authorizationUrl": "https://example.com/resource"
}
```

Enum values:

- None.

Error responses:

- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- An admin must configure and enable SSO first with `/api/v1/organisations/{orgId}/sso/*`.

Source reference: `src/main/java/com/example/demo/controllers/v1/SsoAuthController.java`

### GET /api/v1/auth/sso/oauth2/callback

User type: **Guest**

Description: Exchange the IdP callback code for the platform JWT.

When to call: Call on the frontend callback route after the IdP redirects back.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| code | query | yes | string | required |
| state | query | no | string | optional |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| token | string | no | optional |
| user | object | no | optional |
| user.id | string (uuid) | no | optional, format `uuid` |
| user.email | string | no | optional |
| user.firstName | string | no | optional |
| user.lastName | string | no | optional |
| user.role | string | no | optional |
| expiresIn | integer | no | optional |
| loginMethod | string | no | optional |

```json
{
  "token": "<token>",
  "user": {
    "id": "11111111-1111-1111-1111-111111111111",
    "email": "jane.admin@example.com",
    "firstName": "string",
    "lastName": "string",
    "role": "string"
  },
  "expiresIn": 1,
  "loginMethod": "string"
}
```

Enum values:

- None.

Error responses:

- **400** (Simple error map)

```json
{
  "error": "Invalid request"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- An admin must configure and enable SSO first with `/api/v1/organisations/{orgId}/sso/*`.

Source reference: `src/main/java/com/example/demo/controllers/v1/SsoAuthController.java`

### POST /api/v1/auth/sso/saml/acs

User type: **Guest**

Description: Consume a SAML assertion and return the platform JWT.

When to call: Handle the IdP POST back to the ACS route.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| SAMLResponse | query | yes | string | required |
| RelayState | query | no | string | optional |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| SAMLResponse | string | yes | required |
| RelayState | string (uuid) | no | optional, format `uuid` |

```json
{
  "SAMLResponse": "string",
  "RelayState": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| token | string | no | optional |
| user | object | no | optional |
| user.id | string (uuid) | no | optional, format `uuid` |
| user.email | string | no | optional |
| user.firstName | string | no | optional |
| user.lastName | string | no | optional |
| user.role | string | no | optional |
| expiresIn | integer | no | optional |
| loginMethod | string | no | optional |

```json
{
  "token": "<token>",
  "user": {
    "id": "11111111-1111-1111-1111-111111111111",
    "email": "jane.admin@example.com",
    "firstName": "string",
    "lastName": "string",
    "role": "string"
  },
  "expiresIn": 1,
  "loginMethod": "string"
}
```

Enum values:

- None.

Error responses:

- **400** (Simple error map)

```json
{
  "error": "Invalid request"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- An admin must configure and enable SSO first with `/api/v1/organisations/{orgId}/sso/*`.

Source reference: `src/main/java/com/example/demo/controllers/v1/SsoAuthController.java`

### GET /api/v1/auth/sso/saml/initiate

User type: **Guest**

Description: Build the SAML redirect URL for an organisation SSO login.

When to call: Call before redirecting the browser to the SAML identity provider.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| orgId | query | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| redirectUrl | string | no | optional |

```json
{
  "redirectUrl": "https://example.com/resource"
}
```

Enum values:

- None.

Error responses:

- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- An admin must configure and enable SSO first with `/api/v1/organisations/{orgId}/sso/*`.

Source reference: `src/main/java/com/example/demo/controllers/v1/SsoAuthController.java`

## Authentication Service

Stage: **Onboarding**

### POST /api/v1/auth/forgot-password

User type: **Guest**

Description: Create or trigger a new forgot password operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| email | string | yes | required |

```json
{
  "email": "jane.admin@example.com"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| message | string | no | optional |

```json
{
  "message": "string"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- No extra dependency beyond the current authentication context.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuthController.java`

### POST /api/v1/auth/login

User type: **Guest**

Description: Authenticate a user and return the JWT for all protected API calls.

When to call: Call on the sign-in screen before any protected flow.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| email | string | yes | required |
| password | string | yes | required |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "email": "jane.admin@example.com",
  "password": "Password123",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| token | string | no | optional |
| user | object | no | optional |
| user.id | string (uuid) | no | optional, format `uuid` |
| user.email | string | no | optional |
| user.firstName | string | no | optional |
| user.lastName | string | no | optional |
| user.role | string | no | optional |
| expiresIn | integer | no | optional |

```json
{
  "token": "<token>",
  "user": {
    "id": "11111111-1111-1111-1111-111111111111",
    "email": "jane.admin@example.com",
    "firstName": "string",
    "lastName": "string",
    "role": "string"
  },
  "expiresIn": 1
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **409** (Conflict envelope or no body)

```json
{
  "status": 409,
  "message": "Conflict",
  "errorCode": "CONFLICT",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Create the organisation first via `/api/v1/tenant/register` or `/api/v1/organisations`, or use an SSO-enabled organisation.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuthController.java`

### POST /api/v1/auth/logout

User type: **Authenticated user**

Description: Create or trigger a new logout operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| Authorization | header | no | string | optional |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| message | string | no | optional |

```json
{
  "message": "string"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuthController.java`

### GET /api/v1/auth/profile

User type: **Authenticated user**

Description: Fetch the signed-in user profile.

When to call: Call after login and on app bootstrap to hydrate the session.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| Authorization | header | yes | string | required |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| firstName | string | yes | required |
| lastName | string | yes | required |
| email | string | yes | required |
| phone | string | no | optional |
| password | string | yes | required |
| employeeId | string | no | optional |
| jobTitle | string | no | optional |
| roleId | string (uuid) | no | optional, format `uuid` |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |
| departmentId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "firstName": "string",
  "lastName": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "password": "Password123",
  "employeeId": "11111111-1111-1111-1111-111111111111",
  "jobTitle": "string",
  "roleId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `TERMINATED` = Terminated.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuthController.java`

### POST /api/v1/auth/refresh

User type: **Authenticated user**

Description: Create or trigger a new refresh operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| Authorization | header | yes | string | required |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| token | string | no | optional |
| expiresIn | integer | no | optional |

```json
{
  "token": "<token>",
  "expiresIn": 1
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuthController.java`

### POST /api/v1/auth/register

User type: **Guest**

Description: Create a user inside an existing organisation without logging them in.

When to call: Call from organisation-managed registration or invite completion.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| firstName | string | yes | required |
| lastName | string | yes | required |
| email | string | yes | required |
| phone | string | no | optional |
| password | string | yes | required, minLength 8, maxLength 2147483647 |
| jobTitle | string | no | optional |
| organisationId | string (uuid) | yes | required, format `uuid` |
| roleId | string (uuid) | no | optional, format `uuid` |

```json
{
  "firstName": "string",
  "lastName": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "password": "Password123",
  "jobTitle": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "roleId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| email | string | no | optional |
| firstName | string | no | optional |
| lastName | string | no | optional |
| message | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "email": "jane.admin@example.com",
  "firstName": "string",
  "lastName": "string",
  "message": "string"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- You need a valid `organisationId` first.
- If you send `roleId`, fetch or create the role first via `/api/v1/roles`.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuthController.java`

### POST /api/v1/auth/reset-password

User type: **Guest**

Description: Create or trigger a new reset password operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| token | string | yes | required |
| newPassword | string | yes | required, minLength 8, maxLength 2147483647 |

```json
{
  "token": "<token>",
  "newPassword": "Password123"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| message | string | no | optional |

```json
{
  "message": "string"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- No extra dependency beyond the current authentication context.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuthController.java`

## MFA Service

Stage: **Onboarding**

### DELETE /api/v1/mfa/disable

User type: **Authenticated user**

Description: Disable MFA after confirming a current TOTP code.

When to call: Call when the user confirms MFA removal.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| code | string | yes | required, minLength 6, maxLength 6 |

```json
{
  "code": "123456"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| message | string | no | optional |

```json
{
  "message": "string"
}
```

Enum values:

- None.

Error responses:

- **400** (Simple error map)

```json
{
  "error": "Invalid request"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first.

Source reference: `src/main/java/com/example/demo/controllers/v1/MfaController.java`

### POST /api/v1/mfa/setup

User type: **Authenticated user**

Description: Start TOTP MFA setup and return the QR code image.

When to call: Call from the account security screen when the user enables MFA.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| secret | string | no | optional |
| qrCodeImage | string | no | optional |
| message | string | no | optional |

```json
{
  "secret": "string",
  "qrCodeImage": "123456",
  "message": "string"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first.

Source reference: `src/main/java/com/example/demo/controllers/v1/MfaController.java`

### POST /api/v1/mfa/verify

User type: **Authenticated user**

Description: Verify the first TOTP code and enable MFA.

When to call: Call immediately after setup when the user submits the authenticator code.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| code | string | yes | required, minLength 6, maxLength 6 |

```json
{
  "code": "123456"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| message | string | no | optional |

```json
{
  "message": "string"
}
```

Enum values:

- None.

Error responses:

- **400** (Simple error map)

```json
{
  "error": "Invalid request"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first.
- For `/verify`, call `/api/v1/mfa/setup` first.

Source reference: `src/main/java/com/example/demo/controllers/v1/MfaController.java`

## SSO Configuration Service

Stage: **Organisation Setup**

### GET /api/v1/organisations/{orgId}/sso

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| orgId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `orgId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/SsoConfigController.java`

### PUT /api/v1/organisations/{orgId}/sso/oauth2

User type: **Org admin or system admin**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| orgId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| provider | enum<string> | yes | required, enum |
| enabled | boolean | no | optional |
| clientId | string | no | optional |
| clientSecret | string | no | optional |
| issuerUri | string | no | optional |
| scopes | string | no | optional |
| redirectUri | string | no | optional |
| idpMetadataUrl | string | no | optional |
| spEntityId | string | no | optional |
| assertionConsumerServiceUrl | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "provider": "GOOGLE",
  "enabled": true,
  "clientId": "11111111-1111-1111-1111-111111111111",
  "clientSecret": "string",
  "issuerUri": "string",
  "scopes": "string",
  "redirectUri": "string",
  "idpMetadataUrl": "https://example.com/resource",
  "spEntityId": "11111111-1111-1111-1111-111111111111",
  "assertionConsumerServiceUrl": "https://example.com/resource"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| provider | enum<string> | yes | required, enum |
| enabled | boolean | no | optional |
| clientId | string | no | optional |
| clientSecret | string | no | optional |
| issuerUri | string | no | optional |
| scopes | string | no | optional |
| redirectUri | string | no | optional |
| idpMetadataUrl | string | no | optional |
| spEntityId | string | no | optional |
| assertionConsumerServiceUrl | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "provider": "GOOGLE",
  "enabled": true,
  "clientId": "11111111-1111-1111-1111-111111111111",
  "clientSecret": "string",
  "issuerUri": "string",
  "scopes": "string",
  "redirectUri": "string",
  "idpMetadataUrl": "https://example.com/resource",
  "spEntityId": "11111111-1111-1111-1111-111111111111",
  "assertionConsumerServiceUrl": "https://example.com/resource"
}
```

Enum values:

- request `provider`: `GOOGLE` = Google., `AZURE_AD` = Azure ad., `OKTA` = Okta., `GITHUB` = Github., `SAML` = Saml., `CUSTOM_OAUTH2` = Custom oauth2.
- response `provider`: `GOOGLE` = Google., `AZURE_AD` = Azure ad., `OKTA` = Okta., `GITHUB` = Github., `SAML` = Saml., `CUSTOM_OAUTH2` = Custom oauth2.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `orgId` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `clientId` before submitting the payload.
- Resolve `spEntityId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/SsoConfigController.java`

### PUT /api/v1/organisations/{orgId}/sso/saml

User type: **Org admin or system admin**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| orgId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| provider | enum<string> | yes | required, enum |
| enabled | boolean | no | optional |
| clientId | string | no | optional |
| clientSecret | string | no | optional |
| issuerUri | string | no | optional |
| scopes | string | no | optional |
| redirectUri | string | no | optional |
| idpMetadataUrl | string | no | optional |
| spEntityId | string | no | optional |
| assertionConsumerServiceUrl | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "provider": "GOOGLE",
  "enabled": true,
  "clientId": "11111111-1111-1111-1111-111111111111",
  "clientSecret": "string",
  "issuerUri": "string",
  "scopes": "string",
  "redirectUri": "string",
  "idpMetadataUrl": "https://example.com/resource",
  "spEntityId": "11111111-1111-1111-1111-111111111111",
  "assertionConsumerServiceUrl": "https://example.com/resource"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| provider | enum<string> | yes | required, enum |
| enabled | boolean | no | optional |
| clientId | string | no | optional |
| clientSecret | string | no | optional |
| issuerUri | string | no | optional |
| scopes | string | no | optional |
| redirectUri | string | no | optional |
| idpMetadataUrl | string | no | optional |
| spEntityId | string | no | optional |
| assertionConsumerServiceUrl | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "provider": "GOOGLE",
  "enabled": true,
  "clientId": "11111111-1111-1111-1111-111111111111",
  "clientSecret": "string",
  "issuerUri": "string",
  "scopes": "string",
  "redirectUri": "string",
  "idpMetadataUrl": "https://example.com/resource",
  "spEntityId": "11111111-1111-1111-1111-111111111111",
  "assertionConsumerServiceUrl": "https://example.com/resource"
}
```

Enum values:

- request `provider`: `GOOGLE` = Google., `AZURE_AD` = Azure ad., `OKTA` = Okta., `GITHUB` = Github., `SAML` = Saml., `CUSTOM_OAUTH2` = Custom oauth2.
- response `provider`: `GOOGLE` = Google., `AZURE_AD` = Azure ad., `OKTA` = Okta., `GITHUB` = Github., `SAML` = Saml., `CUSTOM_OAUTH2` = Custom oauth2.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `orgId` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `clientId` before submitting the payload.
- Resolve `spEntityId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/SsoConfigController.java`

### PATCH /api/v1/organisations/{orgId}/sso/toggle

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| orgId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| enabled | boolean | yes | required |

```json
{
  "enabled": true
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| provider | enum<string> | yes | required, enum |
| enabled | boolean | no | optional |
| clientId | string | no | optional |
| clientSecret | string | no | optional |
| issuerUri | string | no | optional |
| scopes | string | no | optional |
| redirectUri | string | no | optional |
| idpMetadataUrl | string | no | optional |
| spEntityId | string | no | optional |
| assertionConsumerServiceUrl | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "provider": "GOOGLE",
  "enabled": true,
  "clientId": "11111111-1111-1111-1111-111111111111",
  "clientSecret": "string",
  "issuerUri": "string",
  "scopes": "string",
  "redirectUri": "string",
  "idpMetadataUrl": "https://example.com/resource",
  "spEntityId": "11111111-1111-1111-1111-111111111111",
  "assertionConsumerServiceUrl": "https://example.com/resource"
}
```

Enum values:

- response `provider`: `GOOGLE` = Google., `AZURE_AD` = Azure ad., `OKTA` = Okta., `GITHUB` = Github., `SAML` = Saml., `CUSTOM_OAUTH2` = Custom oauth2.

Error responses:

- **400** (No body)
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `orgId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/SsoConfigController.java`

## Organisation Management Service

Stage: **Organisation Setup**

### GET /api/v1/organisations

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize organisations.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "registrationNumber": "string",
    "taxId": "11111111-1111-1111-1111-111111111111",
    "industry": "string",
    "country": "string",
    "address": "string",
    "contactEmail": "jane.admin@example.com",
    "contactPhone": "+233555123456",
    "timezone": "09:00",
    "status": "ACTIVE"
  }
]
```

Enum values:

- response `[].status`: `ACTIVE` = Active., `SUSPENDED` = Suspended., `INACTIVE` = Inactive., `DELETED` = Deleted.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/OrganisationController.java`

### POST /api/v1/organisations

User type: **Authenticated user**

Description: Create or trigger a new organisations operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| taxId | string | no | optional |
| industry | string | no | optional |
| country | string | no | optional |
| address | string | no | optional |
| contactEmail | string | no | optional |
| contactPhone | string | no | optional |
| timezone | string | no | optional |
| status | enum<string> | no | optional, enum |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "industry": "string",
  "country": "string",
  "address": "string",
  "contactEmail": "jane.admin@example.com",
  "contactPhone": "+233555123456",
  "timezone": "09:00",
  "status": "ACTIVE"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| taxId | string | no | optional |
| industry | string | no | optional |
| country | string | no | optional |
| address | string | no | optional |
| contactEmail | string | no | optional |
| contactPhone | string | no | optional |
| timezone | string | no | optional |
| status | enum<string> | no | optional, enum |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "industry": "string",
  "country": "string",
  "address": "string",
  "contactEmail": "jane.admin@example.com",
  "contactPhone": "+233555123456",
  "timezone": "09:00",
  "status": "ACTIVE"
}
```

Enum values:

- request `status`: `ACTIVE` = Active., `SUSPENDED` = Suspended., `INACTIVE` = Inactive., `DELETED` = Deleted.
- response `status`: `ACTIVE` = Active., `SUSPENDED` = Suspended., `INACTIVE` = Inactive., `DELETED` = Deleted.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `taxId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/OrganisationController.java`

### DELETE /api/v1/organisations/{id}

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/OrganisationController.java`

### GET /api/v1/organisations/{id}

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| taxId | string | no | optional |
| industry | string | no | optional |
| country | string | no | optional |
| address | string | no | optional |
| contactEmail | string | no | optional |
| contactPhone | string | no | optional |
| timezone | string | no | optional |
| status | enum<string> | no | optional, enum |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "industry": "string",
  "country": "string",
  "address": "string",
  "contactEmail": "jane.admin@example.com",
  "contactPhone": "+233555123456",
  "timezone": "09:00",
  "status": "ACTIVE"
}
```

Enum values:

- response `status`: `ACTIVE` = Active., `SUSPENDED` = Suspended., `INACTIVE` = Inactive., `DELETED` = Deleted.

Error responses:

- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/OrganisationController.java`

### PATCH /api/v1/organisations/{id}

User type: **Authenticated user**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| taxId | string | no | optional |
| industry | string | no | optional |
| country | string | no | optional |
| address | string | no | optional |
| contactEmail | string | no | optional |
| contactPhone | string | no | optional |
| timezone | string | no | optional |
| status | enum<string> | no | optional, enum |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "industry": "string",
  "country": "string",
  "address": "string",
  "contactEmail": "jane.admin@example.com",
  "contactPhone": "+233555123456",
  "timezone": "09:00",
  "status": "ACTIVE"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| taxId | string | no | optional |
| industry | string | no | optional |
| country | string | no | optional |
| address | string | no | optional |
| contactEmail | string | no | optional |
| contactPhone | string | no | optional |
| timezone | string | no | optional |
| status | enum<string> | no | optional, enum |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "industry": "string",
  "country": "string",
  "address": "string",
  "contactEmail": "jane.admin@example.com",
  "contactPhone": "+233555123456",
  "timezone": "09:00",
  "status": "ACTIVE"
}
```

Enum values:

- request `status`: `ACTIVE` = Active., `SUSPENDED` = Suspended., `INACTIVE` = Inactive., `DELETED` = Deleted.
- response `status`: `ACTIVE` = Active., `SUSPENDED` = Suspended., `INACTIVE` = Inactive., `DELETED` = Deleted.

Error responses:

- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `taxId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/OrganisationController.java`

### PUT /api/v1/organisations/{id}

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| taxId | string | no | optional |
| industry | string | no | optional |
| country | string | no | optional |
| address | string | no | optional |
| contactEmail | string | no | optional |
| contactPhone | string | no | optional |
| timezone | string | no | optional |
| status | enum<string> | no | optional, enum |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "industry": "string",
  "country": "string",
  "address": "string",
  "contactEmail": "jane.admin@example.com",
  "contactPhone": "+233555123456",
  "timezone": "09:00",
  "status": "ACTIVE"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| taxId | string | no | optional |
| industry | string | no | optional |
| country | string | no | optional |
| address | string | no | optional |
| contactEmail | string | no | optional |
| contactPhone | string | no | optional |
| timezone | string | no | optional |
| status | enum<string> | no | optional, enum |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "industry": "string",
  "country": "string",
  "address": "string",
  "contactEmail": "jane.admin@example.com",
  "contactPhone": "+233555123456",
  "timezone": "09:00",
  "status": "ACTIVE"
}
```

Enum values:

- request `status`: `ACTIVE` = Active., `SUSPENDED` = Suspended., `INACTIVE` = Inactive., `DELETED` = Deleted.
- response `status`: `ACTIVE` = Active., `SUSPENDED` = Suspended., `INACTIVE` = Inactive., `DELETED` = Deleted.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `taxId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/OrganisationController.java`

## Role Management Service

Stage: **Organisation Setup**

### GET /api/v1/roles

User type: **Authenticated user**

Description: List or summarize roles.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| organisationId | query | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "description": "string",
    "permissions": "string",
    "organisationId": "11111111-1111-1111-1111-111111111111"
  }
]
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/RoleController.java`

### POST /api/v1/roles

User type: **Authenticated user**

Description: Create or trigger a new roles operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| organisationId | query | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| permissions | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "permissions": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| permissions | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "permissions": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/RoleController.java`

### GET /api/v1/roles/by-name

User type: **Authenticated user**

Description: List or summarize by name.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| name | query | yes | string | required |
| organisationId | query | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| permissions | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "permissions": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/RoleController.java`

### DELETE /api/v1/roles/{id}

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/RoleController.java`

### GET /api/v1/roles/{id}

User type: **Authenticated user**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| permissions | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "permissions": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/RoleController.java`

### PATCH /api/v1/roles/{id}

User type: **Authenticated user**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| permissions | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "permissions": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| permissions | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "permissions": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/RoleController.java`

### PUT /api/v1/roles/{id}

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| permissions | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "permissions": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| permissions | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "permissions": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/RoleController.java`

## User Management Service

Stage: **Organisation Setup**

### GET /api/v1/users

User type: **Authenticated user or system admin**

Description: List or summarize users.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| departmentId | query | no | string (uuid) | optional, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "firstName": "string",
    "lastName": "string",
    "email": "jane.admin@example.com",
    "phone": "+233555123456",
    "password": "Password123",
    "employeeId": "11111111-1111-1111-1111-111111111111",
    "jobTitle": "string",
    "roleId": "11111111-1111-1111-1111-111111111111",
    "status": "ACTIVE",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "departmentId": "11111111-1111-1111-1111-111111111111"
  }
]
```

Enum values:

- response `[].status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `TERMINATED` = Terminated.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/UserController.java`

### POST /api/v1/users

User type: **Authenticated user**

Description: Create or trigger a new users operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| firstName | string | yes | required |
| lastName | string | yes | required |
| email | string | yes | required |
| phone | string | no | optional |
| password | string | yes | required |
| employeeId | string | no | optional |
| jobTitle | string | no | optional |
| roleId | string (uuid) | no | optional, format `uuid` |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |
| departmentId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "firstName": "string",
  "lastName": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "password": "Password123",
  "employeeId": "11111111-1111-1111-1111-111111111111",
  "jobTitle": "string",
  "roleId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| firstName | string | yes | required |
| lastName | string | yes | required |
| email | string | yes | required |
| phone | string | no | optional |
| password | string | yes | required |
| employeeId | string | no | optional |
| jobTitle | string | no | optional |
| roleId | string (uuid) | no | optional, format `uuid` |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |
| departmentId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "firstName": "string",
  "lastName": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "password": "Password123",
  "employeeId": "11111111-1111-1111-1111-111111111111",
  "jobTitle": "string",
  "roleId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `TERMINATED` = Terminated.
- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `TERMINATED` = Terminated.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `employeeId` before submitting the payload.
- Resolve `roleId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `departmentId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/UserController.java`

### GET /api/v1/users/{id}

User type: **Authenticated user or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| firstName | string | yes | required |
| lastName | string | yes | required |
| email | string | yes | required |
| phone | string | no | optional |
| password | string | yes | required |
| employeeId | string | no | optional |
| jobTitle | string | no | optional |
| roleId | string (uuid) | no | optional, format `uuid` |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |
| departmentId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "firstName": "string",
  "lastName": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "password": "Password123",
  "employeeId": "11111111-1111-1111-1111-111111111111",
  "jobTitle": "string",
  "roleId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `TERMINATED` = Terminated.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/UserController.java`

### PATCH /api/v1/users/{id}

User type: **Authenticated user**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| firstName | string | yes | required |
| lastName | string | yes | required |
| email | string | yes | required |
| phone | string | no | optional |
| password | string | yes | required |
| employeeId | string | no | optional |
| jobTitle | string | no | optional |
| roleId | string (uuid) | no | optional, format `uuid` |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |
| departmentId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "firstName": "string",
  "lastName": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "password": "Password123",
  "employeeId": "11111111-1111-1111-1111-111111111111",
  "jobTitle": "string",
  "roleId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| firstName | string | yes | required |
| lastName | string | yes | required |
| email | string | yes | required |
| phone | string | no | optional |
| password | string | yes | required |
| employeeId | string | no | optional |
| jobTitle | string | no | optional |
| roleId | string (uuid) | no | optional, format `uuid` |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |
| departmentId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "firstName": "string",
  "lastName": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "password": "Password123",
  "employeeId": "11111111-1111-1111-1111-111111111111",
  "jobTitle": "string",
  "roleId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `TERMINATED` = Terminated.
- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `TERMINATED` = Terminated.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `employeeId` before submitting the payload.
- Resolve `roleId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `departmentId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/UserController.java`

### PUT /api/v1/users/{id}

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| firstName | string | yes | required |
| lastName | string | yes | required |
| email | string | yes | required |
| phone | string | no | optional |
| password | string | yes | required |
| employeeId | string | no | optional |
| jobTitle | string | no | optional |
| roleId | string (uuid) | no | optional, format `uuid` |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |
| departmentId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "firstName": "string",
  "lastName": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "password": "Password123",
  "employeeId": "11111111-1111-1111-1111-111111111111",
  "jobTitle": "string",
  "roleId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| firstName | string | yes | required |
| lastName | string | yes | required |
| email | string | yes | required |
| phone | string | no | optional |
| password | string | yes | required |
| employeeId | string | no | optional |
| jobTitle | string | no | optional |
| roleId | string (uuid) | no | optional, format `uuid` |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |
| departmentId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "firstName": "string",
  "lastName": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "password": "Password123",
  "employeeId": "11111111-1111-1111-1111-111111111111",
  "jobTitle": "string",
  "roleId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `TERMINATED` = Terminated.
- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `TERMINATED` = Terminated.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `employeeId` before submitting the payload.
- Resolve `roleId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `departmentId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/UserController.java`

### PUT /api/v1/users/{id}/deactivate

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| firstName | string | yes | required |
| lastName | string | yes | required |
| email | string | yes | required |
| phone | string | no | optional |
| password | string | yes | required |
| employeeId | string | no | optional |
| jobTitle | string | no | optional |
| roleId | string (uuid) | no | optional, format `uuid` |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |
| departmentId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "firstName": "string",
  "lastName": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "password": "Password123",
  "employeeId": "11111111-1111-1111-1111-111111111111",
  "jobTitle": "string",
  "roleId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `TERMINATED` = Terminated.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/UserController.java`

### PUT /api/v1/users/{id}/role

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |
| roleId | query | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| firstName | string | yes | required |
| lastName | string | yes | required |
| email | string | yes | required |
| phone | string | no | optional |
| password | string | yes | required |
| employeeId | string | no | optional |
| jobTitle | string | no | optional |
| roleId | string (uuid) | no | optional, format `uuid` |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |
| departmentId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "firstName": "string",
  "lastName": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "password": "Password123",
  "employeeId": "11111111-1111-1111-1111-111111111111",
  "jobTitle": "string",
  "roleId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `TERMINATED` = Terminated.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/UserController.java`

## Department Management Service

Stage: **Organisation Setup**

### GET /api/v1/departments

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize departments.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "departmentCode": "123456",
    "parentDepartmentId": "11111111-1111-1111-1111-111111111111",
    "managerId": "11111111-1111-1111-1111-111111111111",
    "costCenterCode": "123456",
    "budgetLimit": 1000.0,
    "status": "ACTIVE",
    "organisationId": "11111111-1111-1111-1111-111111111111"
  }
]
```

Enum values:

- response `[].status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `ARCHIVED` = Archived.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/DepartmentController.java`

### POST /api/v1/departments

User type: **Org admin or system admin**

Description: Create or trigger a new departments operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| departmentCode | string | no | optional |
| parentDepartmentId | string (uuid) | no | optional, format `uuid` |
| managerId | string (uuid) | no | optional, format `uuid` |
| costCenterCode | string | no | optional |
| budgetLimit | number | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "departmentCode": "123456",
  "parentDepartmentId": "11111111-1111-1111-1111-111111111111",
  "managerId": "11111111-1111-1111-1111-111111111111",
  "costCenterCode": "123456",
  "budgetLimit": 1000.0,
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| departmentCode | string | no | optional |
| parentDepartmentId | string (uuid) | no | optional, format `uuid` |
| managerId | string (uuid) | no | optional, format `uuid` |
| costCenterCode | string | no | optional |
| budgetLimit | number | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "departmentCode": "123456",
  "parentDepartmentId": "11111111-1111-1111-1111-111111111111",
  "managerId": "11111111-1111-1111-1111-111111111111",
  "costCenterCode": "123456",
  "budgetLimit": 1000.0,
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `ARCHIVED` = Archived.
- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `ARCHIVED` = Archived.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `parentDepartmentId` before submitting the payload.
- Resolve `managerId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/DepartmentController.java`

### DELETE /api/v1/departments/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/DepartmentController.java`

### GET /api/v1/departments/{id}

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| departmentCode | string | no | optional |
| parentDepartmentId | string (uuid) | no | optional, format `uuid` |
| managerId | string (uuid) | no | optional, format `uuid` |
| costCenterCode | string | no | optional |
| budgetLimit | number | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "departmentCode": "123456",
  "parentDepartmentId": "11111111-1111-1111-1111-111111111111",
  "managerId": "11111111-1111-1111-1111-111111111111",
  "costCenterCode": "123456",
  "budgetLimit": 1000.0,
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `ARCHIVED` = Archived.

Error responses:

- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/DepartmentController.java`

### PATCH /api/v1/departments/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| departmentCode | string | no | optional |
| parentDepartmentId | string (uuid) | no | optional, format `uuid` |
| managerId | string (uuid) | no | optional, format `uuid` |
| costCenterCode | string | no | optional |
| budgetLimit | number | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "departmentCode": "123456",
  "parentDepartmentId": "11111111-1111-1111-1111-111111111111",
  "managerId": "11111111-1111-1111-1111-111111111111",
  "costCenterCode": "123456",
  "budgetLimit": 1000.0,
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| departmentCode | string | no | optional |
| parentDepartmentId | string (uuid) | no | optional, format `uuid` |
| managerId | string (uuid) | no | optional, format `uuid` |
| costCenterCode | string | no | optional |
| budgetLimit | number | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "departmentCode": "123456",
  "parentDepartmentId": "11111111-1111-1111-1111-111111111111",
  "managerId": "11111111-1111-1111-1111-111111111111",
  "costCenterCode": "123456",
  "budgetLimit": 1000.0,
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `ARCHIVED` = Archived.
- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `ARCHIVED` = Archived.

Error responses:

- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `parentDepartmentId` before submitting the payload.
- Resolve `managerId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/DepartmentController.java`

### PUT /api/v1/departments/{id}

User type: **Org admin or system admin**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| departmentCode | string | no | optional |
| parentDepartmentId | string (uuid) | no | optional, format `uuid` |
| managerId | string (uuid) | no | optional, format `uuid` |
| costCenterCode | string | no | optional |
| budgetLimit | number | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "departmentCode": "123456",
  "parentDepartmentId": "11111111-1111-1111-1111-111111111111",
  "managerId": "11111111-1111-1111-1111-111111111111",
  "costCenterCode": "123456",
  "budgetLimit": 1000.0,
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| departmentCode | string | no | optional |
| parentDepartmentId | string (uuid) | no | optional, format `uuid` |
| managerId | string (uuid) | no | optional, format `uuid` |
| costCenterCode | string | no | optional |
| budgetLimit | number | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "departmentCode": "123456",
  "parentDepartmentId": "11111111-1111-1111-1111-111111111111",
  "managerId": "11111111-1111-1111-1111-111111111111",
  "costCenterCode": "123456",
  "budgetLimit": 1000.0,
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `ARCHIVED` = Archived.
- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `ARCHIVED` = Archived.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `parentDepartmentId` before submitting the payload.
- Resolve `managerId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/DepartmentController.java`

## Location Management Service

Stage: **Organisation Setup**

### GET /api/v1/locations

User type: **Authenticated user or system admin**

Description: List or summarize locations.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "building": "string",
    "floor": "string",
    "room": "string",
    "city": "string",
    "country": "string",
    "geoCoordinates": "string",
    "parentLocationId": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111"
  }
]
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/LocationController.java`

### POST /api/v1/locations

User type: **Authenticated user**

Description: Create or trigger a new locations operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| building | string | no | optional |
| floor | string | no | optional |
| room | string | no | optional |
| city | string | no | optional |
| country | string | no | optional |
| geoCoordinates | string | no | optional |
| parentLocationId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "building": "string",
  "floor": "string",
  "room": "string",
  "city": "string",
  "country": "string",
  "geoCoordinates": "string",
  "parentLocationId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| building | string | no | optional |
| floor | string | no | optional |
| room | string | no | optional |
| city | string | no | optional |
| country | string | no | optional |
| geoCoordinates | string | no | optional |
| parentLocationId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "building": "string",
  "floor": "string",
  "room": "string",
  "city": "string",
  "country": "string",
  "geoCoordinates": "string",
  "parentLocationId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `parentLocationId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/LocationController.java`

### DELETE /api/v1/locations/{id}

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/LocationController.java`

### GET /api/v1/locations/{id}

User type: **Authenticated user or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| building | string | no | optional |
| floor | string | no | optional |
| room | string | no | optional |
| city | string | no | optional |
| country | string | no | optional |
| geoCoordinates | string | no | optional |
| parentLocationId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "building": "string",
  "floor": "string",
  "room": "string",
  "city": "string",
  "country": "string",
  "geoCoordinates": "string",
  "parentLocationId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/LocationController.java`

### PATCH /api/v1/locations/{id}

User type: **Authenticated user**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| building | string | no | optional |
| floor | string | no | optional |
| room | string | no | optional |
| city | string | no | optional |
| country | string | no | optional |
| geoCoordinates | string | no | optional |
| parentLocationId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "building": "string",
  "floor": "string",
  "room": "string",
  "city": "string",
  "country": "string",
  "geoCoordinates": "string",
  "parentLocationId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| building | string | no | optional |
| floor | string | no | optional |
| room | string | no | optional |
| city | string | no | optional |
| country | string | no | optional |
| geoCoordinates | string | no | optional |
| parentLocationId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "building": "string",
  "floor": "string",
  "room": "string",
  "city": "string",
  "country": "string",
  "geoCoordinates": "string",
  "parentLocationId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `parentLocationId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/LocationController.java`

### PUT /api/v1/locations/{id}

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| building | string | no | optional |
| floor | string | no | optional |
| room | string | no | optional |
| city | string | no | optional |
| country | string | no | optional |
| geoCoordinates | string | no | optional |
| parentLocationId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "building": "string",
  "floor": "string",
  "room": "string",
  "city": "string",
  "country": "string",
  "geoCoordinates": "string",
  "parentLocationId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| building | string | no | optional |
| floor | string | no | optional |
| room | string | no | optional |
| city | string | no | optional |
| country | string | no | optional |
| geoCoordinates | string | no | optional |
| parentLocationId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "building": "string",
  "floor": "string",
  "room": "string",
  "city": "string",
  "country": "string",
  "geoCoordinates": "string",
  "parentLocationId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `parentLocationId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/LocationController.java`

### GET /api/v1/locations/{parentId}/sub-locations

User type: **Authenticated user or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| parentId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "building": "string",
    "floor": "string",
    "room": "string",
    "city": "string",
    "country": "string",
    "geoCoordinates": "string",
    "parentLocationId": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111"
  }
]
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `parentId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/LocationController.java`

## Category Management Service

Stage: **Organisation Setup**

### GET /api/v1/categories

User type: **Authenticated user or system admin**

Description: List or summarize categories.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "parentCategoryId": "11111111-1111-1111-1111-111111111111",
    "depreciationPolicyId": "11111111-1111-1111-1111-111111111111",
    "defaultWarrantyPeriodMonths": "2026-03",
    "assetPrefixCode": "123456",
    "organisationId": "11111111-1111-1111-1111-111111111111"
  }
]
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/CategoryController.java`

### POST /api/v1/categories

User type: **Authenticated user**

Description: Create or trigger a new categories operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| parentCategoryId | string (uuid) | no | optional, format `uuid` |
| depreciationPolicyId | string (uuid) | no | optional, format `uuid` |
| defaultWarrantyPeriodMonths | integer (int32) | no | optional, format `int32` |
| assetPrefixCode | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "parentCategoryId": "11111111-1111-1111-1111-111111111111",
  "depreciationPolicyId": "11111111-1111-1111-1111-111111111111",
  "defaultWarrantyPeriodMonths": "2026-03",
  "assetPrefixCode": "123456",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| parentCategoryId | string (uuid) | no | optional, format `uuid` |
| depreciationPolicyId | string (uuid) | no | optional, format `uuid` |
| defaultWarrantyPeriodMonths | integer (int32) | no | optional, format `int32` |
| assetPrefixCode | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "parentCategoryId": "11111111-1111-1111-1111-111111111111",
  "depreciationPolicyId": "11111111-1111-1111-1111-111111111111",
  "defaultWarrantyPeriodMonths": "2026-03",
  "assetPrefixCode": "123456",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `parentCategoryId` before submitting the payload.
- Resolve `depreciationPolicyId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/CategoryController.java`

### DELETE /api/v1/categories/{id}

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/CategoryController.java`

### GET /api/v1/categories/{id}

User type: **Authenticated user or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| parentCategoryId | string (uuid) | no | optional, format `uuid` |
| depreciationPolicyId | string (uuid) | no | optional, format `uuid` |
| defaultWarrantyPeriodMonths | integer (int32) | no | optional, format `int32` |
| assetPrefixCode | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "parentCategoryId": "11111111-1111-1111-1111-111111111111",
  "depreciationPolicyId": "11111111-1111-1111-1111-111111111111",
  "defaultWarrantyPeriodMonths": "2026-03",
  "assetPrefixCode": "123456",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/CategoryController.java`

### PATCH /api/v1/categories/{id}

User type: **Authenticated user**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| parentCategoryId | string (uuid) | no | optional, format `uuid` |
| depreciationPolicyId | string (uuid) | no | optional, format `uuid` |
| defaultWarrantyPeriodMonths | integer (int32) | no | optional, format `int32` |
| assetPrefixCode | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "parentCategoryId": "11111111-1111-1111-1111-111111111111",
  "depreciationPolicyId": "11111111-1111-1111-1111-111111111111",
  "defaultWarrantyPeriodMonths": "2026-03",
  "assetPrefixCode": "123456",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| parentCategoryId | string (uuid) | no | optional, format `uuid` |
| depreciationPolicyId | string (uuid) | no | optional, format `uuid` |
| defaultWarrantyPeriodMonths | integer (int32) | no | optional, format `int32` |
| assetPrefixCode | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "parentCategoryId": "11111111-1111-1111-1111-111111111111",
  "depreciationPolicyId": "11111111-1111-1111-1111-111111111111",
  "defaultWarrantyPeriodMonths": "2026-03",
  "assetPrefixCode": "123456",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `parentCategoryId` before submitting the payload.
- Resolve `depreciationPolicyId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/CategoryController.java`

### PUT /api/v1/categories/{id}

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| parentCategoryId | string (uuid) | no | optional, format `uuid` |
| depreciationPolicyId | string (uuid) | no | optional, format `uuid` |
| defaultWarrantyPeriodMonths | integer (int32) | no | optional, format `int32` |
| assetPrefixCode | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "parentCategoryId": "11111111-1111-1111-1111-111111111111",
  "depreciationPolicyId": "11111111-1111-1111-1111-111111111111",
  "defaultWarrantyPeriodMonths": "2026-03",
  "assetPrefixCode": "123456",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| parentCategoryId | string (uuid) | no | optional, format `uuid` |
| depreciationPolicyId | string (uuid) | no | optional, format `uuid` |
| defaultWarrantyPeriodMonths | integer (int32) | no | optional, format `int32` |
| assetPrefixCode | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "parentCategoryId": "11111111-1111-1111-1111-111111111111",
  "depreciationPolicyId": "11111111-1111-1111-1111-111111111111",
  "defaultWarrantyPeriodMonths": "2026-03",
  "assetPrefixCode": "123456",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `parentCategoryId` before submitting the payload.
- Resolve `depreciationPolicyId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/CategoryController.java`

### GET /api/v1/categories/{parentId}/sub-categories

User type: **Authenticated user or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| parentId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "parentCategoryId": "11111111-1111-1111-1111-111111111111",
    "depreciationPolicyId": "11111111-1111-1111-1111-111111111111",
    "defaultWarrantyPeriodMonths": "2026-03",
    "assetPrefixCode": "123456",
    "organisationId": "11111111-1111-1111-1111-111111111111"
  }
]
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `parentId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/CategoryController.java`

## Supplier Management Service

Stage: **Procurement Setup**

### GET /api/v1/suppliers

User type: **Authenticated user or system admin**

Description: List or summarize suppliers.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "registrationNumber": "string",
    "contactPerson": "string",
    "email": "jane.admin@example.com",
    "phone": "+233555123456",
    "address": "string",
    "bankDetails": "string",
    "taxId": "11111111-1111-1111-1111-111111111111",
    "status": "ACTIVE",
    "organisationId": "11111111-1111-1111-1111-111111111111"
  }
]
```

Enum values:

- response `[].status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `BLACKLISTED` = Blacklisted.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/SupplierController.java`

### POST /api/v1/suppliers

User type: **Authenticated user**

Description: Create or trigger a new suppliers operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| contactPerson | string | no | optional |
| email | string | no | optional |
| phone | string | no | optional |
| address | string | no | optional |
| bankDetails | string | no | optional |
| taxId | string | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "contactPerson": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "address": "string",
  "bankDetails": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| contactPerson | string | no | optional |
| email | string | no | optional |
| phone | string | no | optional |
| address | string | no | optional |
| bankDetails | string | no | optional |
| taxId | string | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "contactPerson": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "address": "string",
  "bankDetails": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `BLACKLISTED` = Blacklisted.
- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `BLACKLISTED` = Blacklisted.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `taxId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/SupplierController.java`

### GET /api/v1/suppliers/by-email

User type: **Authenticated user or system admin**

Description: List or summarize by email.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| email | query | yes | string | required |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| contactPerson | string | no | optional |
| email | string | no | optional |
| phone | string | no | optional |
| address | string | no | optional |
| bankDetails | string | no | optional |
| taxId | string | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "contactPerson": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "address": "string",
  "bankDetails": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `BLACKLISTED` = Blacklisted.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/SupplierController.java`

### DELETE /api/v1/suppliers/{id}

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/SupplierController.java`

### GET /api/v1/suppliers/{id}

User type: **Authenticated user or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| contactPerson | string | no | optional |
| email | string | no | optional |
| phone | string | no | optional |
| address | string | no | optional |
| bankDetails | string | no | optional |
| taxId | string | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "contactPerson": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "address": "string",
  "bankDetails": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `BLACKLISTED` = Blacklisted.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/SupplierController.java`

### PATCH /api/v1/suppliers/{id}

User type: **Authenticated user**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| contactPerson | string | no | optional |
| email | string | no | optional |
| phone | string | no | optional |
| address | string | no | optional |
| bankDetails | string | no | optional |
| taxId | string | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "contactPerson": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "address": "string",
  "bankDetails": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| contactPerson | string | no | optional |
| email | string | no | optional |
| phone | string | no | optional |
| address | string | no | optional |
| bankDetails | string | no | optional |
| taxId | string | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "contactPerson": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "address": "string",
  "bankDetails": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `BLACKLISTED` = Blacklisted.
- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `BLACKLISTED` = Blacklisted.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `taxId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/SupplierController.java`

### PUT /api/v1/suppliers/{id}

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| contactPerson | string | no | optional |
| email | string | no | optional |
| phone | string | no | optional |
| address | string | no | optional |
| bankDetails | string | no | optional |
| taxId | string | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "contactPerson": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "address": "string",
  "bankDetails": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| registrationNumber | string | no | optional |
| contactPerson | string | no | optional |
| email | string | no | optional |
| phone | string | no | optional |
| address | string | no | optional |
| bankDetails | string | no | optional |
| taxId | string | no | optional |
| status | enum<string> | no | optional, enum |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "registrationNumber": "string",
  "contactPerson": "string",
  "email": "jane.admin@example.com",
  "phone": "+233555123456",
  "address": "string",
  "bankDetails": "string",
  "taxId": "11111111-1111-1111-1111-111111111111",
  "status": "ACTIVE",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `BLACKLISTED` = Blacklisted.
- response `status`: `ACTIVE` = Active., `INACTIVE` = Inactive., `SUSPENDED` = Suspended., `BLACKLISTED` = Blacklisted.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `taxId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/SupplierController.java`

## Purchase Order Service

Stage: **Procurement Setup**

### GET /api/v1/purchase-orders

User type: **Authenticated user or system admin**

Description: List or summarize purchase orders.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| departmentId | query | no | string (uuid) | optional, format `uuid` |
| supplierId | query | no | string (uuid) | optional, format `uuid` |
| status | query | no | enum<string> | optional, enum |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "poNumber": "string",
    "totalAmount": 1000.0,
    "currency": "USD",
    "status": "DRAFT",
    "approvedById": "11111111-1111-1111-1111-111111111111",
    "remarks": "string",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "departmentId": "11111111-1111-1111-1111-111111111111",
    "supplierId": "11111111-1111-1111-1111-111111111111"
  }
]
```

Enum values:

- parameter `status`: `DRAFT` = Draft., `SUBMITTED` = Submitted., `APPROVED` = Approved., `REJECTED` = Rejected., `DELIVERED` = Delivered., `CANCELLED` = Cancelled.
- response `[].status`: `DRAFT` = Draft., `SUBMITTED` = Submitted., `APPROVED` = Approved., `REJECTED` = Rejected., `DELIVERED` = Delivered., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/PurchaseOrderController.java`

### POST /api/v1/purchase-orders

User type: **Authenticated user**

Description: Create or trigger a new purchase orders operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| poNumber | string | yes | required |
| totalAmount | number | yes | required |
| currency | string | no | optional |
| status | enum<string> | no | optional, enum |
| approvedById | string (uuid) | no | optional, format `uuid` |
| remarks | string | no | optional |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| supplierId | string (uuid) | yes | required, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "poNumber": "string",
  "totalAmount": 1000.0,
  "currency": "USD",
  "status": "DRAFT",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "remarks": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| poNumber | string | yes | required |
| totalAmount | number | yes | required |
| currency | string | no | optional |
| status | enum<string> | no | optional, enum |
| approvedById | string (uuid) | no | optional, format `uuid` |
| remarks | string | no | optional |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| supplierId | string (uuid) | yes | required, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "poNumber": "string",
  "totalAmount": 1000.0,
  "currency": "USD",
  "status": "DRAFT",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "remarks": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `status`: `DRAFT` = Draft., `SUBMITTED` = Submitted., `APPROVED` = Approved., `REJECTED` = Rejected., `DELIVERED` = Delivered., `CANCELLED` = Cancelled.
- response `status`: `DRAFT` = Draft., `SUBMITTED` = Submitted., `APPROVED` = Approved., `REJECTED` = Rejected., `DELIVERED` = Delivered., `CANCELLED` = Cancelled.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `approvedById` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `departmentId` before submitting the payload.
- Resolve `supplierId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/PurchaseOrderController.java`

### DELETE /api/v1/purchase-orders/{id}

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/PurchaseOrderController.java`

### GET /api/v1/purchase-orders/{id}

User type: **Authenticated user or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| poNumber | string | yes | required |
| totalAmount | number | yes | required |
| currency | string | no | optional |
| status | enum<string> | no | optional, enum |
| approvedById | string (uuid) | no | optional, format `uuid` |
| remarks | string | no | optional |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| supplierId | string (uuid) | yes | required, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "poNumber": "string",
  "totalAmount": 1000.0,
  "currency": "USD",
  "status": "DRAFT",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "remarks": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- response `status`: `DRAFT` = Draft., `SUBMITTED` = Submitted., `APPROVED` = Approved., `REJECTED` = Rejected., `DELIVERED` = Delivered., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/PurchaseOrderController.java`

### PATCH /api/v1/purchase-orders/{id}

User type: **Authenticated user**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| poNumber | string | yes | required |
| totalAmount | number | yes | required |
| currency | string | no | optional |
| status | enum<string> | no | optional, enum |
| approvedById | string (uuid) | no | optional, format `uuid` |
| remarks | string | no | optional |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| supplierId | string (uuid) | yes | required, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "poNumber": "string",
  "totalAmount": 1000.0,
  "currency": "USD",
  "status": "DRAFT",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "remarks": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| poNumber | string | yes | required |
| totalAmount | number | yes | required |
| currency | string | no | optional |
| status | enum<string> | no | optional, enum |
| approvedById | string (uuid) | no | optional, format `uuid` |
| remarks | string | no | optional |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| supplierId | string (uuid) | yes | required, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "poNumber": "string",
  "totalAmount": 1000.0,
  "currency": "USD",
  "status": "DRAFT",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "remarks": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `status`: `DRAFT` = Draft., `SUBMITTED` = Submitted., `APPROVED` = Approved., `REJECTED` = Rejected., `DELIVERED` = Delivered., `CANCELLED` = Cancelled.
- response `status`: `DRAFT` = Draft., `SUBMITTED` = Submitted., `APPROVED` = Approved., `REJECTED` = Rejected., `DELIVERED` = Delivered., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `approvedById` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `departmentId` before submitting the payload.
- Resolve `supplierId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/PurchaseOrderController.java`

### PUT /api/v1/purchase-orders/{id}

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| poNumber | string | yes | required |
| totalAmount | number | yes | required |
| currency | string | no | optional |
| status | enum<string> | no | optional, enum |
| approvedById | string (uuid) | no | optional, format `uuid` |
| remarks | string | no | optional |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| supplierId | string (uuid) | yes | required, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "poNumber": "string",
  "totalAmount": 1000.0,
  "currency": "USD",
  "status": "DRAFT",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "remarks": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| poNumber | string | yes | required |
| totalAmount | number | yes | required |
| currency | string | no | optional |
| status | enum<string> | no | optional, enum |
| approvedById | string (uuid) | no | optional, format `uuid` |
| remarks | string | no | optional |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| supplierId | string (uuid) | yes | required, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "poNumber": "string",
  "totalAmount": 1000.0,
  "currency": "USD",
  "status": "DRAFT",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "remarks": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `status`: `DRAFT` = Draft., `SUBMITTED` = Submitted., `APPROVED` = Approved., `REJECTED` = Rejected., `DELIVERED` = Delivered., `CANCELLED` = Cancelled.
- response `status`: `DRAFT` = Draft., `SUBMITTED` = Submitted., `APPROVED` = Approved., `REJECTED` = Rejected., `DELIVERED` = Delivered., `CANCELLED` = Cancelled.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `approvedById` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `departmentId` before submitting the payload.
- Resolve `supplierId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/PurchaseOrderController.java`

### POST /api/v1/purchase-orders/{id}/approve

User type: **Authenticated user**

Description: Approve the targeted v1 record.

When to call: Call from an approval action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| poNumber | string | yes | required |
| totalAmount | number | yes | required |
| currency | string | no | optional |
| status | enum<string> | no | optional, enum |
| approvedById | string (uuid) | no | optional, format `uuid` |
| remarks | string | no | optional |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| supplierId | string (uuid) | yes | required, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "poNumber": "string",
  "totalAmount": 1000.0,
  "currency": "USD",
  "status": "DRAFT",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "remarks": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- response `status`: `DRAFT` = Draft., `SUBMITTED` = Submitted., `APPROVED` = Approved., `REJECTED` = Rejected., `DELIVERED` = Delivered., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/PurchaseOrderController.java`

### POST /api/v1/purchase-orders/{id}/reject

User type: **Authenticated user**

Description: Reject the targeted v1 record.

When to call: Call from an approval or review action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| poNumber | string | yes | required |
| totalAmount | number | yes | required |
| currency | string | no | optional |
| status | enum<string> | no | optional, enum |
| approvedById | string (uuid) | no | optional, format `uuid` |
| remarks | string | no | optional |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| supplierId | string (uuid) | yes | required, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "poNumber": "string",
  "totalAmount": 1000.0,
  "currency": "USD",
  "status": "DRAFT",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "remarks": "string",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- response `status`: `DRAFT` = Draft., `SUBMITTED` = Submitted., `APPROVED` = Approved., `REJECTED` = Rejected., `DELIVERED` = Delivered., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/PurchaseOrderController.java`

## Budget Service

Stage: **Procurement Setup**

### GET /api/v1/budgets

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize budgets.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "description": "string",
    "departmentId": "11111111-1111-1111-1111-111111111111",
    "departmentName": "string",
    "totalAmount": 0.01,
    "spentAmount": 1000.0,
    "currency": "USD",
    "periodStart": "2026-03-27",
    "periodEnd": "2026-03-27",
    "status": "DRAFT",
    "remainingAmount": 1000.0,
    "utilizationPct": "string"
  }
]
```

Enum values:

- response `[].status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXCEEDED` = Exceeded., `CLOSED` = Closed.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/BudgetController.java`

### POST /api/v1/budgets

User type: **Org admin or system admin**

Description: Create or trigger a new budgets operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| departmentName | string | no | optional |
| totalAmount | number | yes | required, min 0.01 |
| spentAmount | number | no | optional |
| currency | string | no | optional |
| periodStart | string (date) | yes | required, format `date` |
| periodEnd | string (date) | yes | required, format `date` |
| status | enum<string> | no | optional, enum |
| remainingAmount | number | no | optional |
| utilizationPct | number (double) | no | optional, format `double` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "departmentName": "string",
  "totalAmount": 0.01,
  "spentAmount": 1000.0,
  "currency": "USD",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "status": "DRAFT",
  "remainingAmount": 1000.0,
  "utilizationPct": "string"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| departmentName | string | no | optional |
| totalAmount | number | yes | required, min 0.01 |
| spentAmount | number | no | optional |
| currency | string | no | optional |
| periodStart | string (date) | yes | required, format `date` |
| periodEnd | string (date) | yes | required, format `date` |
| status | enum<string> | no | optional, enum |
| remainingAmount | number | no | optional |
| utilizationPct | number (double) | no | optional, format `double` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "departmentName": "string",
  "totalAmount": 0.01,
  "spentAmount": 1000.0,
  "currency": "USD",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "status": "DRAFT",
  "remainingAmount": 1000.0,
  "utilizationPct": "string"
}
```

Enum values:

- request `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXCEEDED` = Exceeded., `CLOSED` = Closed.
- response `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXCEEDED` = Exceeded., `CLOSED` = Closed.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `departmentId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/BudgetController.java`

### DELETE /api/v1/budgets/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/BudgetController.java`

### GET /api/v1/budgets/{id}

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| departmentName | string | no | optional |
| totalAmount | number | yes | required, min 0.01 |
| spentAmount | number | no | optional |
| currency | string | no | optional |
| periodStart | string (date) | yes | required, format `date` |
| periodEnd | string (date) | yes | required, format `date` |
| status | enum<string> | no | optional, enum |
| remainingAmount | number | no | optional |
| utilizationPct | number (double) | no | optional, format `double` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "departmentName": "string",
  "totalAmount": 0.01,
  "spentAmount": 1000.0,
  "currency": "USD",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "status": "DRAFT",
  "remainingAmount": 1000.0,
  "utilizationPct": "string"
}
```

Enum values:

- response `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXCEEDED` = Exceeded., `CLOSED` = Closed.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/BudgetController.java`

### PATCH /api/v1/budgets/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| departmentName | string | no | optional |
| totalAmount | number | yes | required, min 0.01 |
| spentAmount | number | no | optional |
| currency | string | no | optional |
| periodStart | string (date) | yes | required, format `date` |
| periodEnd | string (date) | yes | required, format `date` |
| status | enum<string> | no | optional, enum |
| remainingAmount | number | no | optional |
| utilizationPct | number (double) | no | optional, format `double` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "departmentName": "string",
  "totalAmount": 0.01,
  "spentAmount": 1000.0,
  "currency": "USD",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "status": "DRAFT",
  "remainingAmount": 1000.0,
  "utilizationPct": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| departmentName | string | no | optional |
| totalAmount | number | yes | required, min 0.01 |
| spentAmount | number | no | optional |
| currency | string | no | optional |
| periodStart | string (date) | yes | required, format `date` |
| periodEnd | string (date) | yes | required, format `date` |
| status | enum<string> | no | optional, enum |
| remainingAmount | number | no | optional |
| utilizationPct | number (double) | no | optional, format `double` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "departmentName": "string",
  "totalAmount": 0.01,
  "spentAmount": 1000.0,
  "currency": "USD",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "status": "DRAFT",
  "remainingAmount": 1000.0,
  "utilizationPct": "string"
}
```

Enum values:

- request `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXCEEDED` = Exceeded., `CLOSED` = Closed.
- response `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXCEEDED` = Exceeded., `CLOSED` = Closed.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `departmentId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/BudgetController.java`

### PUT /api/v1/budgets/{id}

User type: **Org admin or system admin**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| departmentName | string | no | optional |
| totalAmount | number | yes | required, min 0.01 |
| spentAmount | number | no | optional |
| currency | string | no | optional |
| periodStart | string (date) | yes | required, format `date` |
| periodEnd | string (date) | yes | required, format `date` |
| status | enum<string> | no | optional, enum |
| remainingAmount | number | no | optional |
| utilizationPct | number (double) | no | optional, format `double` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "departmentName": "string",
  "totalAmount": 0.01,
  "spentAmount": 1000.0,
  "currency": "USD",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "status": "DRAFT",
  "remainingAmount": 1000.0,
  "utilizationPct": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| departmentName | string | no | optional |
| totalAmount | number | yes | required, min 0.01 |
| spentAmount | number | no | optional |
| currency | string | no | optional |
| periodStart | string (date) | yes | required, format `date` |
| periodEnd | string (date) | yes | required, format `date` |
| status | enum<string> | no | optional, enum |
| remainingAmount | number | no | optional |
| utilizationPct | number (double) | no | optional, format `double` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "departmentName": "string",
  "totalAmount": 0.01,
  "spentAmount": 1000.0,
  "currency": "USD",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "status": "DRAFT",
  "remainingAmount": 1000.0,
  "utilizationPct": "string"
}
```

Enum values:

- request `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXCEEDED` = Exceeded., `CLOSED` = Closed.
- response `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXCEEDED` = Exceeded., `CLOSED` = Closed.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `departmentId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/BudgetController.java`

### POST /api/v1/budgets/{id}/spend

User type: **Org admin or system admin**

Description: Create or trigger a new spend operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| amount | number | yes | required, min 0.01 |

```json
{
  "amount": 0.01
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| departmentName | string | no | optional |
| totalAmount | number | yes | required, min 0.01 |
| spentAmount | number | no | optional |
| currency | string | no | optional |
| periodStart | string (date) | yes | required, format `date` |
| periodEnd | string (date) | yes | required, format `date` |
| status | enum<string> | no | optional, enum |
| remainingAmount | number | no | optional |
| utilizationPct | number (double) | no | optional, format `double` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "departmentName": "string",
  "totalAmount": 0.01,
  "spentAmount": 1000.0,
  "currency": "USD",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "status": "DRAFT",
  "remainingAmount": 1000.0,
  "utilizationPct": "string"
}
```

Enum values:

- response `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXCEEDED` = Exceeded., `CLOSED` = Closed.

Error responses:

- **400** (No body)
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/BudgetController.java`

## Contract Service

Stage: **Procurement Setup**

### GET /api/v1/contracts

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize contracts.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "title": "string",
    "contractNumber": "string",
    "contractType": "PURCHASE",
    "status": "DRAFT",
    "supplierId": "11111111-1111-1111-1111-111111111111",
    "supplierName": "string",
    "assetId": "11111111-1111-1111-1111-111111111111",
    "assetName": "string",
    "startDate": "2026-03-27",
    "endDate": "2026-03-27",
    "alertDaysBefore": "string",
    "value": 1000.0,
    "currency": "USD",
    "autoRenew": true,
    "documentUrl": "https://example.com/resource",
    "notes": "string",
    "daysUntilExpiry": "string"
  }
]
```

Enum values:

- response `[].contractType`: `PURCHASE` = Purchase., `LEASE` = Lease., `MAINTENANCE` = Maintenance., `SERVICE_LEVEL_AGREEMENT` = Service level agreement., `WARRANTY` = Warranty., `INSURANCE` = Insurance., `OTHER` = Other.
- response `[].status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `TERMINATED` = Terminated., `RENEWED` = Renewed.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ContractController.java`

### POST /api/v1/contracts

User type: **Org admin or system admin**

Description: Create or trigger a new contracts operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| contractNumber | string | no | optional |
| contractType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| supplierId | string (uuid) | no | optional, format `uuid` |
| supplierName | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| assetName | string | no | optional |
| startDate | string (date) | yes | required, format `date` |
| endDate | string (date) | yes | required, format `date` |
| alertDaysBefore | integer (int32) | no | optional, format `int32` |
| value | number | no | optional |
| currency | string | no | optional |
| autoRenew | boolean | no | optional |
| documentUrl | string | no | optional |
| notes | string | no | optional |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "contractNumber": "string",
  "contractType": "PURCHASE",
  "status": "DRAFT",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "supplierName": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "startDate": "2026-03-27",
  "endDate": "2026-03-27",
  "alertDaysBefore": "string",
  "value": 1000.0,
  "currency": "USD",
  "autoRenew": true,
  "documentUrl": "https://example.com/resource",
  "notes": "string",
  "daysUntilExpiry": "string"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| contractNumber | string | no | optional |
| contractType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| supplierId | string (uuid) | no | optional, format `uuid` |
| supplierName | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| assetName | string | no | optional |
| startDate | string (date) | yes | required, format `date` |
| endDate | string (date) | yes | required, format `date` |
| alertDaysBefore | integer (int32) | no | optional, format `int32` |
| value | number | no | optional |
| currency | string | no | optional |
| autoRenew | boolean | no | optional |
| documentUrl | string | no | optional |
| notes | string | no | optional |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "contractNumber": "string",
  "contractType": "PURCHASE",
  "status": "DRAFT",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "supplierName": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "startDate": "2026-03-27",
  "endDate": "2026-03-27",
  "alertDaysBefore": "string",
  "value": 1000.0,
  "currency": "USD",
  "autoRenew": true,
  "documentUrl": "https://example.com/resource",
  "notes": "string",
  "daysUntilExpiry": "string"
}
```

Enum values:

- request `contractType`: `PURCHASE` = Purchase., `LEASE` = Lease., `MAINTENANCE` = Maintenance., `SERVICE_LEVEL_AGREEMENT` = Service level agreement., `WARRANTY` = Warranty., `INSURANCE` = Insurance., `OTHER` = Other.
- request `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `TERMINATED` = Terminated., `RENEWED` = Renewed.
- response `contractType`: `PURCHASE` = Purchase., `LEASE` = Lease., `MAINTENANCE` = Maintenance., `SERVICE_LEVEL_AGREEMENT` = Service level agreement., `WARRANTY` = Warranty., `INSURANCE` = Insurance., `OTHER` = Other.
- response `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `TERMINATED` = Terminated., `RENEWED` = Renewed.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `supplierId` before submitting the payload.
- Resolve `assetId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ContractController.java`

### GET /api/v1/contracts/expiring-soon

User type: **Org admin or system admin**

Description: List or summarize expiring soon.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| days | query | no | integer (int32) | optional, format `int32` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "title": "string",
    "contractNumber": "string",
    "contractType": "PURCHASE",
    "status": "DRAFT",
    "supplierId": "11111111-1111-1111-1111-111111111111",
    "supplierName": "string",
    "assetId": "11111111-1111-1111-1111-111111111111",
    "assetName": "string",
    "startDate": "2026-03-27",
    "endDate": "2026-03-27",
    "alertDaysBefore": "string",
    "value": 1000.0,
    "currency": "USD",
    "autoRenew": true,
    "documentUrl": "https://example.com/resource",
    "notes": "string",
    "daysUntilExpiry": "string"
  }
]
```

Enum values:

- response `[].contractType`: `PURCHASE` = Purchase., `LEASE` = Lease., `MAINTENANCE` = Maintenance., `SERVICE_LEVEL_AGREEMENT` = Service level agreement., `WARRANTY` = Warranty., `INSURANCE` = Insurance., `OTHER` = Other.
- response `[].status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `TERMINATED` = Terminated., `RENEWED` = Renewed.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ContractController.java`

### DELETE /api/v1/contracts/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ContractController.java`

### GET /api/v1/contracts/{id}

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| contractNumber | string | no | optional |
| contractType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| supplierId | string (uuid) | no | optional, format `uuid` |
| supplierName | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| assetName | string | no | optional |
| startDate | string (date) | yes | required, format `date` |
| endDate | string (date) | yes | required, format `date` |
| alertDaysBefore | integer (int32) | no | optional, format `int32` |
| value | number | no | optional |
| currency | string | no | optional |
| autoRenew | boolean | no | optional |
| documentUrl | string | no | optional |
| notes | string | no | optional |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "contractNumber": "string",
  "contractType": "PURCHASE",
  "status": "DRAFT",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "supplierName": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "startDate": "2026-03-27",
  "endDate": "2026-03-27",
  "alertDaysBefore": "string",
  "value": 1000.0,
  "currency": "USD",
  "autoRenew": true,
  "documentUrl": "https://example.com/resource",
  "notes": "string",
  "daysUntilExpiry": "string"
}
```

Enum values:

- response `contractType`: `PURCHASE` = Purchase., `LEASE` = Lease., `MAINTENANCE` = Maintenance., `SERVICE_LEVEL_AGREEMENT` = Service level agreement., `WARRANTY` = Warranty., `INSURANCE` = Insurance., `OTHER` = Other.
- response `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `TERMINATED` = Terminated., `RENEWED` = Renewed.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ContractController.java`

### PATCH /api/v1/contracts/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| contractNumber | string | no | optional |
| contractType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| supplierId | string (uuid) | no | optional, format `uuid` |
| supplierName | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| assetName | string | no | optional |
| startDate | string (date) | yes | required, format `date` |
| endDate | string (date) | yes | required, format `date` |
| alertDaysBefore | integer (int32) | no | optional, format `int32` |
| value | number | no | optional |
| currency | string | no | optional |
| autoRenew | boolean | no | optional |
| documentUrl | string | no | optional |
| notes | string | no | optional |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "contractNumber": "string",
  "contractType": "PURCHASE",
  "status": "DRAFT",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "supplierName": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "startDate": "2026-03-27",
  "endDate": "2026-03-27",
  "alertDaysBefore": "string",
  "value": 1000.0,
  "currency": "USD",
  "autoRenew": true,
  "documentUrl": "https://example.com/resource",
  "notes": "string",
  "daysUntilExpiry": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| contractNumber | string | no | optional |
| contractType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| supplierId | string (uuid) | no | optional, format `uuid` |
| supplierName | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| assetName | string | no | optional |
| startDate | string (date) | yes | required, format `date` |
| endDate | string (date) | yes | required, format `date` |
| alertDaysBefore | integer (int32) | no | optional, format `int32` |
| value | number | no | optional |
| currency | string | no | optional |
| autoRenew | boolean | no | optional |
| documentUrl | string | no | optional |
| notes | string | no | optional |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "contractNumber": "string",
  "contractType": "PURCHASE",
  "status": "DRAFT",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "supplierName": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "startDate": "2026-03-27",
  "endDate": "2026-03-27",
  "alertDaysBefore": "string",
  "value": 1000.0,
  "currency": "USD",
  "autoRenew": true,
  "documentUrl": "https://example.com/resource",
  "notes": "string",
  "daysUntilExpiry": "string"
}
```

Enum values:

- request `contractType`: `PURCHASE` = Purchase., `LEASE` = Lease., `MAINTENANCE` = Maintenance., `SERVICE_LEVEL_AGREEMENT` = Service level agreement., `WARRANTY` = Warranty., `INSURANCE` = Insurance., `OTHER` = Other.
- request `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `TERMINATED` = Terminated., `RENEWED` = Renewed.
- response `contractType`: `PURCHASE` = Purchase., `LEASE` = Lease., `MAINTENANCE` = Maintenance., `SERVICE_LEVEL_AGREEMENT` = Service level agreement., `WARRANTY` = Warranty., `INSURANCE` = Insurance., `OTHER` = Other.
- response `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `TERMINATED` = Terminated., `RENEWED` = Renewed.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `supplierId` before submitting the payload.
- Resolve `assetId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ContractController.java`

### PUT /api/v1/contracts/{id}

User type: **Org admin or system admin**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| contractNumber | string | no | optional |
| contractType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| supplierId | string (uuid) | no | optional, format `uuid` |
| supplierName | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| assetName | string | no | optional |
| startDate | string (date) | yes | required, format `date` |
| endDate | string (date) | yes | required, format `date` |
| alertDaysBefore | integer (int32) | no | optional, format `int32` |
| value | number | no | optional |
| currency | string | no | optional |
| autoRenew | boolean | no | optional |
| documentUrl | string | no | optional |
| notes | string | no | optional |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "contractNumber": "string",
  "contractType": "PURCHASE",
  "status": "DRAFT",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "supplierName": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "startDate": "2026-03-27",
  "endDate": "2026-03-27",
  "alertDaysBefore": "string",
  "value": 1000.0,
  "currency": "USD",
  "autoRenew": true,
  "documentUrl": "https://example.com/resource",
  "notes": "string",
  "daysUntilExpiry": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| contractNumber | string | no | optional |
| contractType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| supplierId | string (uuid) | no | optional, format `uuid` |
| supplierName | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| assetName | string | no | optional |
| startDate | string (date) | yes | required, format `date` |
| endDate | string (date) | yes | required, format `date` |
| alertDaysBefore | integer (int32) | no | optional, format `int32` |
| value | number | no | optional |
| currency | string | no | optional |
| autoRenew | boolean | no | optional |
| documentUrl | string | no | optional |
| notes | string | no | optional |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "contractNumber": "string",
  "contractType": "PURCHASE",
  "status": "DRAFT",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "supplierName": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "startDate": "2026-03-27",
  "endDate": "2026-03-27",
  "alertDaysBefore": "string",
  "value": 1000.0,
  "currency": "USD",
  "autoRenew": true,
  "documentUrl": "https://example.com/resource",
  "notes": "string",
  "daysUntilExpiry": "string"
}
```

Enum values:

- request `contractType`: `PURCHASE` = Purchase., `LEASE` = Lease., `MAINTENANCE` = Maintenance., `SERVICE_LEVEL_AGREEMENT` = Service level agreement., `WARRANTY` = Warranty., `INSURANCE` = Insurance., `OTHER` = Other.
- request `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `TERMINATED` = Terminated., `RENEWED` = Renewed.
- response `contractType`: `PURCHASE` = Purchase., `LEASE` = Lease., `MAINTENANCE` = Maintenance., `SERVICE_LEVEL_AGREEMENT` = Service level agreement., `WARRANTY` = Warranty., `INSURANCE` = Insurance., `OTHER` = Other.
- response `status`: `DRAFT` = Draft., `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `TERMINATED` = Terminated., `RENEWED` = Renewed.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `supplierId` before submitting the payload.
- Resolve `assetId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ContractController.java`

## Depreciation Policy Service

Stage: **Asset Setup**

### GET /api/v1/depreciation-policies

User type: **Authenticated user**

Description: List or summarize depreciation policies.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| organisationId | query | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "description": "string",
    "method": "STRAIGHT_LINE",
    "usefulLifeMonths": "2026-03",
    "salvageValuePercent": 1000.0,
    "organisationId": "11111111-1111-1111-1111-111111111111"
  }
]
```

Enum values:

- response `[].method`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/DepreciationPolicyController.java`

### POST /api/v1/depreciation-policies

User type: **Authenticated user**

Description: Create or trigger a new depreciation policies operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| organisationId | query | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| method | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| salvageValuePercent | number | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "method": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "salvageValuePercent": 1000.0,
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| method | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| salvageValuePercent | number | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "method": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "salvageValuePercent": 1000.0,
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `method`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- response `method`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/DepreciationPolicyController.java`

### DELETE /api/v1/depreciation-policies/{id}

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/DepreciationPolicyController.java`

### GET /api/v1/depreciation-policies/{id}

User type: **Authenticated user**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| method | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| salvageValuePercent | number | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "method": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "salvageValuePercent": 1000.0,
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- response `method`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/DepreciationPolicyController.java`

### PATCH /api/v1/depreciation-policies/{id}

User type: **Authenticated user**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| method | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| salvageValuePercent | number | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "method": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "salvageValuePercent": 1000.0,
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| method | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| salvageValuePercent | number | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "method": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "salvageValuePercent": 1000.0,
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `method`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- response `method`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/DepreciationPolicyController.java`

### PUT /api/v1/depreciation-policies/{id}

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| method | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| salvageValuePercent | number | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "method": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "salvageValuePercent": 1000.0,
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| description | string | no | optional |
| method | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| salvageValuePercent | number | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "description": "string",
  "method": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "salvageValuePercent": 1000.0,
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `method`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- response `method`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/DepreciationPolicyController.java`

## Asset Custom Field Service

Stage: **Asset Lifecycle**

### GET /api/v1/assets/{assetId}/custom-fields

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| assetId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "assetId": "11111111-1111-1111-1111-111111111111",
    "fieldName": "string",
    "fieldValue": "string"
  }
]
```

Enum values:

- None.

Error responses:

- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `assetId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetCustomFieldController.java`

### POST /api/v1/assets/{assetId}/custom-fields

User type: **Org admin or system admin**

Description: Create or trigger a new custom fields operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| assetId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | no | optional, format `uuid` |
| fieldName | string | yes | required, minLength 0, maxLength 100 |
| fieldValue | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "fieldName": "string",
  "fieldValue": "string"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | no | optional, format `uuid` |
| fieldName | string | yes | required, minLength 0, maxLength 100 |
| fieldValue | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "fieldName": "string",
  "fieldValue": "string"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **409** (Conflict envelope or no body)

```json
{
  "status": 409,
  "message": "Conflict",
  "errorCode": "CONFLICT",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `assetId` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `assetId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetCustomFieldController.java`

### DELETE /api/v1/assets/{assetId}/custom-fields/{fieldId}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| assetId | path | yes | string (uuid) | required, format `uuid` |
| fieldId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `assetId` before calling this route.
- Resolve `fieldId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetCustomFieldController.java`

### PUT /api/v1/assets/{assetId}/custom-fields/{fieldId}

User type: **Org admin or system admin**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| assetId | path | yes | string (uuid) | required, format `uuid` |
| fieldId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | no | optional, format `uuid` |
| fieldName | string | yes | required, minLength 0, maxLength 100 |
| fieldValue | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "fieldName": "string",
  "fieldValue": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | no | optional, format `uuid` |
| fieldName | string | yes | required, minLength 0, maxLength 100 |
| fieldValue | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "fieldName": "string",
  "fieldValue": "string"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `assetId` before calling this route.
- Resolve `fieldId` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `assetId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetCustomFieldController.java`

## Asset Service

Stage: **Asset Lifecycle**

### GET /api/v1/assets

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize assets.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| status | query | no | enum<string> | optional, enum |
| departmentId | query | no | string (uuid) | optional, format `uuid` |
| categoryId | query | no | string (uuid) | optional, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "assetTag": "string",
    "serialNumber": "string",
    "barcodeQrCode": "123456",
    "description": "string",
    "categoryId": "11111111-1111-1111-1111-111111111111",
    "assetType": "HARDWARE",
    "manufacturer": "string",
    "model": "string",
    "purchaseDate": "2026-03-27",
    "purchaseCost": 1000.0,
    "currency": "USD",
    "depreciationMethod": "STRAIGHT_LINE",
    "usefulLifeMonths": "2026-03",
    "residualValue": 1000.0,
    "currentBookValue": 1000.0,
    "warrantyExpiryDate": "2026-03-27",
    "status": "PENDING_PROCUREMENT",
    "condition": "NEW",
    "locationId": "11111111-1111-1111-1111-111111111111",
    "assignedUserId": "11111111-1111-1111-1111-111111111111",
    "supplierId": "11111111-1111-1111-1111-111111111111",
    "invoiceId": "11111111-1111-1111-1111-111111111111",
    "insurancePolicyId": "11111111-1111-1111-1111-111111111111",
    "departmentId": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "purchaseOrderId": "11111111-1111-1111-1111-111111111111",
    "procurementType": "CAPEX",
    "costCenter": "string"
  }
]
```

Enum values:

- parameter `status`: `PENDING_PROCUREMENT` = Pending procurement., `IN_STOCK` = In stock., `RESERVED` = Reserved., `IN_USE` = In use., `MAINTENANCE` = Maintenance., `UNDER_REPAIR` = Under repair., `RETIRED` = Retired., `DISPOSED` = Disposed., `MISSING` = Missing.
- response `[].assetType`: `HARDWARE` = Hardware., `SOFTWARE` = Software., `FURNITURE` = Furniture., `VEHICLE` = Vehicle., `EQUIPMENT` = Equipment., `OTHER` = Other.
- response `[].depreciationMethod`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- response `[].status`: `PENDING_PROCUREMENT` = Pending procurement., `IN_STOCK` = In stock., `RESERVED` = Reserved., `IN_USE` = In use., `MAINTENANCE` = Maintenance., `UNDER_REPAIR` = Under repair., `RETIRED` = Retired., `DISPOSED` = Disposed., `MISSING` = Missing.
- response `[].condition`: `NEW` = New., `EXCELLENT` = Excellent., `GOOD` = Good., `FAIR` = Fair., `DAMAGED` = Damaged., `SCRAP` = Scrap.
- response `[].procurementType`: `CAPEX` = Capex., `OPEX` = Opex.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetController.java`

### POST /api/v1/assets

User type: **Org admin or system admin**

Description: Create or trigger a new assets operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| assetTag | string | no | optional |
| serialNumber | string | no | optional |
| barcodeQrCode | string | no | optional |
| description | string | no | optional |
| categoryId | string (uuid) | no | optional, format `uuid` |
| assetType | enum<string> | no | optional, enum |
| manufacturer | string | no | optional |
| model | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| purchaseCost | number | no | optional |
| currency | string | no | optional |
| depreciationMethod | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| residualValue | number | no | optional |
| currentBookValue | number | no | optional |
| warrantyExpiryDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| condition | enum<string> | no | optional, enum |
| locationId | string (uuid) | no | optional, format `uuid` |
| assignedUserId | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| invoiceId | string | no | optional |
| insurancePolicyId | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| purchaseOrderId | string (uuid) | no | optional, format `uuid` |
| procurementType | enum<string> | no | optional, enum |
| costCenter | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "assetTag": "string",
  "serialNumber": "string",
  "barcodeQrCode": "123456",
  "description": "string",
  "categoryId": "11111111-1111-1111-1111-111111111111",
  "assetType": "HARDWARE",
  "manufacturer": "string",
  "model": "string",
  "purchaseDate": "2026-03-27",
  "purchaseCost": 1000.0,
  "currency": "USD",
  "depreciationMethod": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "residualValue": 1000.0,
  "currentBookValue": 1000.0,
  "warrantyExpiryDate": "2026-03-27",
  "status": "PENDING_PROCUREMENT",
  "condition": "NEW",
  "locationId": "11111111-1111-1111-1111-111111111111",
  "assignedUserId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "invoiceId": "11111111-1111-1111-1111-111111111111",
  "insurancePolicyId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "purchaseOrderId": "11111111-1111-1111-1111-111111111111",
  "procurementType": "CAPEX",
  "costCenter": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| assetTag | string | no | optional |
| serialNumber | string | no | optional |
| barcodeQrCode | string | no | optional |
| description | string | no | optional |
| categoryId | string (uuid) | no | optional, format `uuid` |
| assetType | enum<string> | no | optional, enum |
| manufacturer | string | no | optional |
| model | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| purchaseCost | number | no | optional |
| currency | string | no | optional |
| depreciationMethod | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| residualValue | number | no | optional |
| currentBookValue | number | no | optional |
| warrantyExpiryDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| condition | enum<string> | no | optional, enum |
| locationId | string (uuid) | no | optional, format `uuid` |
| assignedUserId | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| invoiceId | string | no | optional |
| insurancePolicyId | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| purchaseOrderId | string (uuid) | no | optional, format `uuid` |
| procurementType | enum<string> | no | optional, enum |
| costCenter | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "assetTag": "string",
  "serialNumber": "string",
  "barcodeQrCode": "123456",
  "description": "string",
  "categoryId": "11111111-1111-1111-1111-111111111111",
  "assetType": "HARDWARE",
  "manufacturer": "string",
  "model": "string",
  "purchaseDate": "2026-03-27",
  "purchaseCost": 1000.0,
  "currency": "USD",
  "depreciationMethod": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "residualValue": 1000.0,
  "currentBookValue": 1000.0,
  "warrantyExpiryDate": "2026-03-27",
  "status": "PENDING_PROCUREMENT",
  "condition": "NEW",
  "locationId": "11111111-1111-1111-1111-111111111111",
  "assignedUserId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "invoiceId": "11111111-1111-1111-1111-111111111111",
  "insurancePolicyId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "purchaseOrderId": "11111111-1111-1111-1111-111111111111",
  "procurementType": "CAPEX",
  "costCenter": "string"
}
```

Enum values:

- request `assetType`: `HARDWARE` = Hardware., `SOFTWARE` = Software., `FURNITURE` = Furniture., `VEHICLE` = Vehicle., `EQUIPMENT` = Equipment., `OTHER` = Other.
- request `depreciationMethod`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- request `status`: `PENDING_PROCUREMENT` = Pending procurement., `IN_STOCK` = In stock., `RESERVED` = Reserved., `IN_USE` = In use., `MAINTENANCE` = Maintenance., `UNDER_REPAIR` = Under repair., `RETIRED` = Retired., `DISPOSED` = Disposed., `MISSING` = Missing.
- request `condition`: `NEW` = New., `EXCELLENT` = Excellent., `GOOD` = Good., `FAIR` = Fair., `DAMAGED` = Damaged., `SCRAP` = Scrap.
- request `procurementType`: `CAPEX` = Capex., `OPEX` = Opex.
- response `assetType`: `HARDWARE` = Hardware., `SOFTWARE` = Software., `FURNITURE` = Furniture., `VEHICLE` = Vehicle., `EQUIPMENT` = Equipment., `OTHER` = Other.
- response `depreciationMethod`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- response `status`: `PENDING_PROCUREMENT` = Pending procurement., `IN_STOCK` = In stock., `RESERVED` = Reserved., `IN_USE` = In use., `MAINTENANCE` = Maintenance., `UNDER_REPAIR` = Under repair., `RETIRED` = Retired., `DISPOSED` = Disposed., `MISSING` = Missing.
- response `condition`: `NEW` = New., `EXCELLENT` = Excellent., `GOOD` = Good., `FAIR` = Fair., `DAMAGED` = Damaged., `SCRAP` = Scrap.
- response `procurementType`: `CAPEX` = Capex., `OPEX` = Opex.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **409** (Conflict envelope or no body)

```json
{
  "status": 409,
  "message": "Conflict",
  "errorCode": "CONFLICT",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `categoryId` before submitting the payload.
- Resolve `locationId` before submitting the payload.
- Resolve `assignedUserId` before submitting the payload.
- Resolve `supplierId` before submitting the payload.
- Resolve `invoiceId` before submitting the payload.
- Resolve `insurancePolicyId` before submitting the payload.
- Resolve `departmentId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `purchaseOrderId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetController.java`

### POST /api/v1/assets/import

User type: **Org admin or system admin**

Description: Create or trigger a new import operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): multipart/form-data
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `Idempotency-Key` (optional): Used by import endpoints to make retries safe.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| Idempotency-Key | header | no | string | optional |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| file | string (binary) | yes | required, format `binary` |

```json
{
  "file": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| totalRows | integer (int32) | no | optional, format `int32` |
| imported | integer (int32) | no | optional, format `int32` |
| skipped | integer (int32) | no | optional, format `int32` |
| dryRun | boolean | no | optional |
| errors | array<object> | no | optional |
| errors[].row | integer (int32) | no | optional, format `int32` |
| errors[].message | string | no | optional |

```json
{
  "totalRows": "string",
  "imported": "string",
  "skipped": "string",
  "dryRun": true,
  "errors": [
    {
      "row": "string",
      "message": "string"
    }
  ]
}
```

Enum values:

- None.

Error responses:

- **409** (Conflict envelope or no body)

```json
{
  "status": 409,
  "message": "Conflict",
  "errorCode": "CONFLICT",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetController.java`

### DELETE /api/v1/assets/{id}

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetController.java`

### GET /api/v1/assets/{id}

User type: **Authenticated user**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| assetTag | string | no | optional |
| serialNumber | string | no | optional |
| barcodeQrCode | string | no | optional |
| description | string | no | optional |
| categoryId | string (uuid) | no | optional, format `uuid` |
| assetType | enum<string> | no | optional, enum |
| manufacturer | string | no | optional |
| model | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| purchaseCost | number | no | optional |
| currency | string | no | optional |
| depreciationMethod | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| residualValue | number | no | optional |
| currentBookValue | number | no | optional |
| warrantyExpiryDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| condition | enum<string> | no | optional, enum |
| locationId | string (uuid) | no | optional, format `uuid` |
| assignedUserId | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| invoiceId | string | no | optional |
| insurancePolicyId | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| purchaseOrderId | string (uuid) | no | optional, format `uuid` |
| procurementType | enum<string> | no | optional, enum |
| costCenter | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "assetTag": "string",
  "serialNumber": "string",
  "barcodeQrCode": "123456",
  "description": "string",
  "categoryId": "11111111-1111-1111-1111-111111111111",
  "assetType": "HARDWARE",
  "manufacturer": "string",
  "model": "string",
  "purchaseDate": "2026-03-27",
  "purchaseCost": 1000.0,
  "currency": "USD",
  "depreciationMethod": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "residualValue": 1000.0,
  "currentBookValue": 1000.0,
  "warrantyExpiryDate": "2026-03-27",
  "status": "PENDING_PROCUREMENT",
  "condition": "NEW",
  "locationId": "11111111-1111-1111-1111-111111111111",
  "assignedUserId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "invoiceId": "11111111-1111-1111-1111-111111111111",
  "insurancePolicyId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "purchaseOrderId": "11111111-1111-1111-1111-111111111111",
  "procurementType": "CAPEX",
  "costCenter": "string"
}
```

Enum values:

- response `assetType`: `HARDWARE` = Hardware., `SOFTWARE` = Software., `FURNITURE` = Furniture., `VEHICLE` = Vehicle., `EQUIPMENT` = Equipment., `OTHER` = Other.
- response `depreciationMethod`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- response `status`: `PENDING_PROCUREMENT` = Pending procurement., `IN_STOCK` = In stock., `RESERVED` = Reserved., `IN_USE` = In use., `MAINTENANCE` = Maintenance., `UNDER_REPAIR` = Under repair., `RETIRED` = Retired., `DISPOSED` = Disposed., `MISSING` = Missing.
- response `condition`: `NEW` = New., `EXCELLENT` = Excellent., `GOOD` = Good., `FAIR` = Fair., `DAMAGED` = Damaged., `SCRAP` = Scrap.
- response `procurementType`: `CAPEX` = Capex., `OPEX` = Opex.

Error responses:

- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetController.java`

### PATCH /api/v1/assets/{id}

User type: **Authenticated user**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| assetTag | string | no | optional |
| serialNumber | string | no | optional |
| barcodeQrCode | string | no | optional |
| description | string | no | optional |
| categoryId | string (uuid) | no | optional, format `uuid` |
| assetType | enum<string> | no | optional, enum |
| manufacturer | string | no | optional |
| model | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| purchaseCost | number | no | optional |
| currency | string | no | optional |
| depreciationMethod | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| residualValue | number | no | optional |
| currentBookValue | number | no | optional |
| warrantyExpiryDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| condition | enum<string> | no | optional, enum |
| locationId | string (uuid) | no | optional, format `uuid` |
| assignedUserId | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| invoiceId | string | no | optional |
| insurancePolicyId | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| purchaseOrderId | string (uuid) | no | optional, format `uuid` |
| procurementType | enum<string> | no | optional, enum |
| costCenter | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "assetTag": "string",
  "serialNumber": "string",
  "barcodeQrCode": "123456",
  "description": "string",
  "categoryId": "11111111-1111-1111-1111-111111111111",
  "assetType": "HARDWARE",
  "manufacturer": "string",
  "model": "string",
  "purchaseDate": "2026-03-27",
  "purchaseCost": 1000.0,
  "currency": "USD",
  "depreciationMethod": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "residualValue": 1000.0,
  "currentBookValue": 1000.0,
  "warrantyExpiryDate": "2026-03-27",
  "status": "PENDING_PROCUREMENT",
  "condition": "NEW",
  "locationId": "11111111-1111-1111-1111-111111111111",
  "assignedUserId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "invoiceId": "11111111-1111-1111-1111-111111111111",
  "insurancePolicyId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "purchaseOrderId": "11111111-1111-1111-1111-111111111111",
  "procurementType": "CAPEX",
  "costCenter": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| assetTag | string | no | optional |
| serialNumber | string | no | optional |
| barcodeQrCode | string | no | optional |
| description | string | no | optional |
| categoryId | string (uuid) | no | optional, format `uuid` |
| assetType | enum<string> | no | optional, enum |
| manufacturer | string | no | optional |
| model | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| purchaseCost | number | no | optional |
| currency | string | no | optional |
| depreciationMethod | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| residualValue | number | no | optional |
| currentBookValue | number | no | optional |
| warrantyExpiryDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| condition | enum<string> | no | optional, enum |
| locationId | string (uuid) | no | optional, format `uuid` |
| assignedUserId | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| invoiceId | string | no | optional |
| insurancePolicyId | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| purchaseOrderId | string (uuid) | no | optional, format `uuid` |
| procurementType | enum<string> | no | optional, enum |
| costCenter | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "assetTag": "string",
  "serialNumber": "string",
  "barcodeQrCode": "123456",
  "description": "string",
  "categoryId": "11111111-1111-1111-1111-111111111111",
  "assetType": "HARDWARE",
  "manufacturer": "string",
  "model": "string",
  "purchaseDate": "2026-03-27",
  "purchaseCost": 1000.0,
  "currency": "USD",
  "depreciationMethod": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "residualValue": 1000.0,
  "currentBookValue": 1000.0,
  "warrantyExpiryDate": "2026-03-27",
  "status": "PENDING_PROCUREMENT",
  "condition": "NEW",
  "locationId": "11111111-1111-1111-1111-111111111111",
  "assignedUserId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "invoiceId": "11111111-1111-1111-1111-111111111111",
  "insurancePolicyId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "purchaseOrderId": "11111111-1111-1111-1111-111111111111",
  "procurementType": "CAPEX",
  "costCenter": "string"
}
```

Enum values:

- request `assetType`: `HARDWARE` = Hardware., `SOFTWARE` = Software., `FURNITURE` = Furniture., `VEHICLE` = Vehicle., `EQUIPMENT` = Equipment., `OTHER` = Other.
- request `depreciationMethod`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- request `status`: `PENDING_PROCUREMENT` = Pending procurement., `IN_STOCK` = In stock., `RESERVED` = Reserved., `IN_USE` = In use., `MAINTENANCE` = Maintenance., `UNDER_REPAIR` = Under repair., `RETIRED` = Retired., `DISPOSED` = Disposed., `MISSING` = Missing.
- request `condition`: `NEW` = New., `EXCELLENT` = Excellent., `GOOD` = Good., `FAIR` = Fair., `DAMAGED` = Damaged., `SCRAP` = Scrap.
- request `procurementType`: `CAPEX` = Capex., `OPEX` = Opex.
- response `assetType`: `HARDWARE` = Hardware., `SOFTWARE` = Software., `FURNITURE` = Furniture., `VEHICLE` = Vehicle., `EQUIPMENT` = Equipment., `OTHER` = Other.
- response `depreciationMethod`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- response `status`: `PENDING_PROCUREMENT` = Pending procurement., `IN_STOCK` = In stock., `RESERVED` = Reserved., `IN_USE` = In use., `MAINTENANCE` = Maintenance., `UNDER_REPAIR` = Under repair., `RETIRED` = Retired., `DISPOSED` = Disposed., `MISSING` = Missing.
- response `condition`: `NEW` = New., `EXCELLENT` = Excellent., `GOOD` = Good., `FAIR` = Fair., `DAMAGED` = Damaged., `SCRAP` = Scrap.
- response `procurementType`: `CAPEX` = Capex., `OPEX` = Opex.

Error responses:

- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `categoryId` before submitting the payload.
- Resolve `locationId` before submitting the payload.
- Resolve `assignedUserId` before submitting the payload.
- Resolve `supplierId` before submitting the payload.
- Resolve `invoiceId` before submitting the payload.
- Resolve `insurancePolicyId` before submitting the payload.
- Resolve `departmentId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `purchaseOrderId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetController.java`

### PUT /api/v1/assets/{id}

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| assetTag | string | no | optional |
| serialNumber | string | no | optional |
| barcodeQrCode | string | no | optional |
| description | string | no | optional |
| categoryId | string (uuid) | no | optional, format `uuid` |
| assetType | enum<string> | no | optional, enum |
| manufacturer | string | no | optional |
| model | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| purchaseCost | number | no | optional |
| currency | string | no | optional |
| depreciationMethod | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| residualValue | number | no | optional |
| currentBookValue | number | no | optional |
| warrantyExpiryDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| condition | enum<string> | no | optional, enum |
| locationId | string (uuid) | no | optional, format `uuid` |
| assignedUserId | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| invoiceId | string | no | optional |
| insurancePolicyId | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| purchaseOrderId | string (uuid) | no | optional, format `uuid` |
| procurementType | enum<string> | no | optional, enum |
| costCenter | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "assetTag": "string",
  "serialNumber": "string",
  "barcodeQrCode": "123456",
  "description": "string",
  "categoryId": "11111111-1111-1111-1111-111111111111",
  "assetType": "HARDWARE",
  "manufacturer": "string",
  "model": "string",
  "purchaseDate": "2026-03-27",
  "purchaseCost": 1000.0,
  "currency": "USD",
  "depreciationMethod": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "residualValue": 1000.0,
  "currentBookValue": 1000.0,
  "warrantyExpiryDate": "2026-03-27",
  "status": "PENDING_PROCUREMENT",
  "condition": "NEW",
  "locationId": "11111111-1111-1111-1111-111111111111",
  "assignedUserId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "invoiceId": "11111111-1111-1111-1111-111111111111",
  "insurancePolicyId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "purchaseOrderId": "11111111-1111-1111-1111-111111111111",
  "procurementType": "CAPEX",
  "costCenter": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| assetTag | string | no | optional |
| serialNumber | string | no | optional |
| barcodeQrCode | string | no | optional |
| description | string | no | optional |
| categoryId | string (uuid) | no | optional, format `uuid` |
| assetType | enum<string> | no | optional, enum |
| manufacturer | string | no | optional |
| model | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| purchaseCost | number | no | optional |
| currency | string | no | optional |
| depreciationMethod | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| residualValue | number | no | optional |
| currentBookValue | number | no | optional |
| warrantyExpiryDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| condition | enum<string> | no | optional, enum |
| locationId | string (uuid) | no | optional, format `uuid` |
| assignedUserId | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| invoiceId | string | no | optional |
| insurancePolicyId | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| purchaseOrderId | string (uuid) | no | optional, format `uuid` |
| procurementType | enum<string> | no | optional, enum |
| costCenter | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "assetTag": "string",
  "serialNumber": "string",
  "barcodeQrCode": "123456",
  "description": "string",
  "categoryId": "11111111-1111-1111-1111-111111111111",
  "assetType": "HARDWARE",
  "manufacturer": "string",
  "model": "string",
  "purchaseDate": "2026-03-27",
  "purchaseCost": 1000.0,
  "currency": "USD",
  "depreciationMethod": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "residualValue": 1000.0,
  "currentBookValue": 1000.0,
  "warrantyExpiryDate": "2026-03-27",
  "status": "PENDING_PROCUREMENT",
  "condition": "NEW",
  "locationId": "11111111-1111-1111-1111-111111111111",
  "assignedUserId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "invoiceId": "11111111-1111-1111-1111-111111111111",
  "insurancePolicyId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "purchaseOrderId": "11111111-1111-1111-1111-111111111111",
  "procurementType": "CAPEX",
  "costCenter": "string"
}
```

Enum values:

- request `assetType`: `HARDWARE` = Hardware., `SOFTWARE` = Software., `FURNITURE` = Furniture., `VEHICLE` = Vehicle., `EQUIPMENT` = Equipment., `OTHER` = Other.
- request `depreciationMethod`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- request `status`: `PENDING_PROCUREMENT` = Pending procurement., `IN_STOCK` = In stock., `RESERVED` = Reserved., `IN_USE` = In use., `MAINTENANCE` = Maintenance., `UNDER_REPAIR` = Under repair., `RETIRED` = Retired., `DISPOSED` = Disposed., `MISSING` = Missing.
- request `condition`: `NEW` = New., `EXCELLENT` = Excellent., `GOOD` = Good., `FAIR` = Fair., `DAMAGED` = Damaged., `SCRAP` = Scrap.
- request `procurementType`: `CAPEX` = Capex., `OPEX` = Opex.
- response `assetType`: `HARDWARE` = Hardware., `SOFTWARE` = Software., `FURNITURE` = Furniture., `VEHICLE` = Vehicle., `EQUIPMENT` = Equipment., `OTHER` = Other.
- response `depreciationMethod`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- response `status`: `PENDING_PROCUREMENT` = Pending procurement., `IN_STOCK` = In stock., `RESERVED` = Reserved., `IN_USE` = In use., `MAINTENANCE` = Maintenance., `UNDER_REPAIR` = Under repair., `RETIRED` = Retired., `DISPOSED` = Disposed., `MISSING` = Missing.
- response `condition`: `NEW` = New., `EXCELLENT` = Excellent., `GOOD` = Good., `FAIR` = Fair., `DAMAGED` = Damaged., `SCRAP` = Scrap.
- response `procurementType`: `CAPEX` = Capex., `OPEX` = Opex.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `categoryId` before submitting the payload.
- Resolve `locationId` before submitting the payload.
- Resolve `assignedUserId` before submitting the payload.
- Resolve `supplierId` before submitting the payload.
- Resolve `invoiceId` before submitting the payload.
- Resolve `insurancePolicyId` before submitting the payload.
- Resolve `departmentId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `purchaseOrderId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetController.java`

### DELETE /api/v1/assets/{id}/assign-user

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| assetTag | string | no | optional |
| serialNumber | string | no | optional |
| barcodeQrCode | string | no | optional |
| description | string | no | optional |
| categoryId | string (uuid) | no | optional, format `uuid` |
| assetType | enum<string> | no | optional, enum |
| manufacturer | string | no | optional |
| model | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| purchaseCost | number | no | optional |
| currency | string | no | optional |
| depreciationMethod | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| residualValue | number | no | optional |
| currentBookValue | number | no | optional |
| warrantyExpiryDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| condition | enum<string> | no | optional, enum |
| locationId | string (uuid) | no | optional, format `uuid` |
| assignedUserId | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| invoiceId | string | no | optional |
| insurancePolicyId | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| purchaseOrderId | string (uuid) | no | optional, format `uuid` |
| procurementType | enum<string> | no | optional, enum |
| costCenter | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "assetTag": "string",
  "serialNumber": "string",
  "barcodeQrCode": "123456",
  "description": "string",
  "categoryId": "11111111-1111-1111-1111-111111111111",
  "assetType": "HARDWARE",
  "manufacturer": "string",
  "model": "string",
  "purchaseDate": "2026-03-27",
  "purchaseCost": 1000.0,
  "currency": "USD",
  "depreciationMethod": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "residualValue": 1000.0,
  "currentBookValue": 1000.0,
  "warrantyExpiryDate": "2026-03-27",
  "status": "PENDING_PROCUREMENT",
  "condition": "NEW",
  "locationId": "11111111-1111-1111-1111-111111111111",
  "assignedUserId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "invoiceId": "11111111-1111-1111-1111-111111111111",
  "insurancePolicyId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "purchaseOrderId": "11111111-1111-1111-1111-111111111111",
  "procurementType": "CAPEX",
  "costCenter": "string"
}
```

Enum values:

- response `assetType`: `HARDWARE` = Hardware., `SOFTWARE` = Software., `FURNITURE` = Furniture., `VEHICLE` = Vehicle., `EQUIPMENT` = Equipment., `OTHER` = Other.
- response `depreciationMethod`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- response `status`: `PENDING_PROCUREMENT` = Pending procurement., `IN_STOCK` = In stock., `RESERVED` = Reserved., `IN_USE` = In use., `MAINTENANCE` = Maintenance., `UNDER_REPAIR` = Under repair., `RETIRED` = Retired., `DISPOSED` = Disposed., `MISSING` = Missing.
- response `condition`: `NEW` = New., `EXCELLENT` = Excellent., `GOOD` = Good., `FAIR` = Fair., `DAMAGED` = Damaged., `SCRAP` = Scrap.
- response `procurementType`: `CAPEX` = Capex., `OPEX` = Opex.

Error responses:

- **400** (No body)
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetController.java`

### POST /api/v1/assets/{id}/assign-user/{userId}

User type: **Authenticated user**

Description: Create or trigger a new assign user operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |
| userId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| assetTag | string | no | optional |
| serialNumber | string | no | optional |
| barcodeQrCode | string | no | optional |
| description | string | no | optional |
| categoryId | string (uuid) | no | optional, format `uuid` |
| assetType | enum<string> | no | optional, enum |
| manufacturer | string | no | optional |
| model | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| purchaseCost | number | no | optional |
| currency | string | no | optional |
| depreciationMethod | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| residualValue | number | no | optional |
| currentBookValue | number | no | optional |
| warrantyExpiryDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| condition | enum<string> | no | optional, enum |
| locationId | string (uuid) | no | optional, format `uuid` |
| assignedUserId | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| invoiceId | string | no | optional |
| insurancePolicyId | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| purchaseOrderId | string (uuid) | no | optional, format `uuid` |
| procurementType | enum<string> | no | optional, enum |
| costCenter | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "assetTag": "string",
  "serialNumber": "string",
  "barcodeQrCode": "123456",
  "description": "string",
  "categoryId": "11111111-1111-1111-1111-111111111111",
  "assetType": "HARDWARE",
  "manufacturer": "string",
  "model": "string",
  "purchaseDate": "2026-03-27",
  "purchaseCost": 1000.0,
  "currency": "USD",
  "depreciationMethod": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "residualValue": 1000.0,
  "currentBookValue": 1000.0,
  "warrantyExpiryDate": "2026-03-27",
  "status": "PENDING_PROCUREMENT",
  "condition": "NEW",
  "locationId": "11111111-1111-1111-1111-111111111111",
  "assignedUserId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "invoiceId": "11111111-1111-1111-1111-111111111111",
  "insurancePolicyId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "purchaseOrderId": "11111111-1111-1111-1111-111111111111",
  "procurementType": "CAPEX",
  "costCenter": "string"
}
```

Enum values:

- response `assetType`: `HARDWARE` = Hardware., `SOFTWARE` = Software., `FURNITURE` = Furniture., `VEHICLE` = Vehicle., `EQUIPMENT` = Equipment., `OTHER` = Other.
- response `depreciationMethod`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- response `status`: `PENDING_PROCUREMENT` = Pending procurement., `IN_STOCK` = In stock., `RESERVED` = Reserved., `IN_USE` = In use., `MAINTENANCE` = Maintenance., `UNDER_REPAIR` = Under repair., `RETIRED` = Retired., `DISPOSED` = Disposed., `MISSING` = Missing.
- response `condition`: `NEW` = New., `EXCELLENT` = Excellent., `GOOD` = Good., `FAIR` = Fair., `DAMAGED` = Damaged., `SCRAP` = Scrap.
- response `procurementType`: `CAPEX` = Capex., `OPEX` = Opex.

Error responses:

- **400** (No body)
- **409** (Conflict envelope or no body)

```json
{
  "status": 409,
  "message": "Conflict",
  "errorCode": "CONFLICT",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `userId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetController.java`

### POST /api/v1/assets/{id}/assign/{departmentId}

User type: **Authenticated user**

Description: Create or trigger a new assign operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |
| departmentId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| assetTag | string | no | optional |
| serialNumber | string | no | optional |
| barcodeQrCode | string | no | optional |
| description | string | no | optional |
| categoryId | string (uuid) | no | optional, format `uuid` |
| assetType | enum<string> | no | optional, enum |
| manufacturer | string | no | optional |
| model | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| purchaseCost | number | no | optional |
| currency | string | no | optional |
| depreciationMethod | enum<string> | no | optional, enum |
| usefulLifeMonths | integer (int32) | no | optional, format `int32` |
| residualValue | number | no | optional |
| currentBookValue | number | no | optional |
| warrantyExpiryDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| condition | enum<string> | no | optional, enum |
| locationId | string (uuid) | no | optional, format `uuid` |
| assignedUserId | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| invoiceId | string | no | optional |
| insurancePolicyId | string | no | optional |
| departmentId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| purchaseOrderId | string (uuid) | no | optional, format `uuid` |
| procurementType | enum<string> | no | optional, enum |
| costCenter | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "assetTag": "string",
  "serialNumber": "string",
  "barcodeQrCode": "123456",
  "description": "string",
  "categoryId": "11111111-1111-1111-1111-111111111111",
  "assetType": "HARDWARE",
  "manufacturer": "string",
  "model": "string",
  "purchaseDate": "2026-03-27",
  "purchaseCost": 1000.0,
  "currency": "USD",
  "depreciationMethod": "STRAIGHT_LINE",
  "usefulLifeMonths": "2026-03",
  "residualValue": 1000.0,
  "currentBookValue": 1000.0,
  "warrantyExpiryDate": "2026-03-27",
  "status": "PENDING_PROCUREMENT",
  "condition": "NEW",
  "locationId": "11111111-1111-1111-1111-111111111111",
  "assignedUserId": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "invoiceId": "11111111-1111-1111-1111-111111111111",
  "insurancePolicyId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "purchaseOrderId": "11111111-1111-1111-1111-111111111111",
  "procurementType": "CAPEX",
  "costCenter": "string"
}
```

Enum values:

- response `assetType`: `HARDWARE` = Hardware., `SOFTWARE` = Software., `FURNITURE` = Furniture., `VEHICLE` = Vehicle., `EQUIPMENT` = Equipment., `OTHER` = Other.
- response `depreciationMethod`: `STRAIGHT_LINE` = Straight line., `DECLINING_BALANCE` = Declining balance., `UNITS_OF_PRODUCTION` = Units of production., `SUM_OF_YEARS_DIGITS` = Sum of years digits.
- response `status`: `PENDING_PROCUREMENT` = Pending procurement., `IN_STOCK` = In stock., `RESERVED` = Reserved., `IN_USE` = In use., `MAINTENANCE` = Maintenance., `UNDER_REPAIR` = Under repair., `RETIRED` = Retired., `DISPOSED` = Disposed., `MISSING` = Missing.
- response `condition`: `NEW` = New., `EXCELLENT` = Excellent., `GOOD` = Good., `FAIR` = Fair., `DAMAGED` = Damaged., `SCRAP` = Scrap.
- response `procurementType`: `CAPEX` = Capex., `OPEX` = Opex.

Error responses:

- **400** (No body)
- **409** (Conflict envelope or no body)

```json
{
  "status": 409,
  "message": "Conflict",
  "errorCode": "CONFLICT",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `departmentId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetController.java`

### GET /api/v1/assets/{id}/history

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "eventType": "API_ACTION",
    "occurredAt": "2026-03-27T10:15:30Z",
    "summary": "string",
    "actor": "string",
    "fromDepartment": "string",
    "toDepartment": "string",
    "fromLocation": "string",
    "toLocation": "string",
    "transferStatus": "string",
    "maintenanceType": "string",
    "maintenanceStatus": "string",
    "scheduledDate": "2026-03-27",
    "performedDate": "2026-03-27",
    "disposalMethod": "string",
    "disposalDate": "2026-03-27",
    "httpMethod": "string",
    "path": "string",
    "responseStatus": "string"
  }
]
```

Enum values:

- response `[].eventType`: `API_ACTION` = Api action., `TRANSFER` = Transfer., `MAINTENANCE` = Maintenance., `DISPOSAL` = Disposal.

Error responses:

- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetController.java`

### GET /api/v1/assets/{id}/qrcode

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | string (byte) | yes | See example below |

```json
"string"
```

Enum values:

- None.

Error responses:

- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetController.java`

## Bulk Operations Service

Stage: **Asset Lifecycle**

### POST /api/v1/bulk/assets/export

User type: **Org admin or system admin**

Description: Create or trigger a new export operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| format | enum<string> | no | optional, enum |

```json
{
  "format": "CSV"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | string (byte) | yes | See example below |

```json
"string"
```

Enum values:

- request `format`: `CSV` = Csv., `EXCEL` = Excel., `PDF` = Pdf.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/BulkOperationsController.java`

### POST /api/v1/bulk/assets/import

User type: **Org admin or system admin**

Description: Create or trigger a new import operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): multipart/form-data
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `Idempotency-Key` (optional): Used by import endpoints to make retries safe.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| Idempotency-Key | header | no | string | optional |
| dryRun | query | no | boolean | optional |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| file | string (binary) | yes | required, format `binary` |

```json
{
  "file": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| totalRows | integer (int32) | no | optional, format `int32` |
| imported | integer (int32) | no | optional, format `int32` |
| skipped | integer (int32) | no | optional, format `int32` |
| dryRun | boolean | no | optional |
| errors | array<object> | no | optional |
| errors[].row | integer (int32) | no | optional, format `int32` |
| errors[].message | string | no | optional |

```json
{
  "totalRows": "string",
  "imported": "string",
  "skipped": "string",
  "dryRun": true,
  "errors": [
    {
      "row": "string",
      "message": "string"
    }
  ]
}
```

Enum values:

- None.

Error responses:

- **409** (Conflict envelope or no body)

```json
{
  "status": 409,
  "message": "Conflict",
  "errorCode": "CONFLICT",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/BulkOperationsController.java`

### POST /api/v1/bulk/purchase-orders/export

User type: **Org admin or system admin**

Description: Create or trigger a new export operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| format | enum<string> | no | optional, enum |

```json
{
  "format": "CSV"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | string (byte) | yes | See example below |

```json
"string"
```

Enum values:

- request `format`: `CSV` = Csv., `EXCEL` = Excel., `PDF` = Pdf.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/BulkOperationsController.java`

### POST /api/v1/bulk/suppliers/export

User type: **Org admin or system admin**

Description: Create or trigger a new export operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| format | enum<string> | no | optional, enum |

```json
{
  "format": "CSV"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | string (byte) | yes | See example below |

```json
"string"
```

Enum values:

- request `format`: `CSV` = Csv., `EXCEL` = Excel., `PDF` = Pdf.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/BulkOperationsController.java`

## Import Jobs Service

Stage: **Asset Lifecycle**

### POST /api/v1/import-jobs/assets

User type: **Org admin or system admin**

Description: Create or trigger a new assets operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): multipart/form-data
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `Idempotency-Key` (optional): Used by import endpoints to make retries safe.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| Idempotency-Key | header | no | string | optional |
| dryRun | query | no | boolean | optional |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| file | string (binary) | yes | required, format `binary` |

```json
{
  "file": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| jobId | string (uuid) | no | optional, format `uuid` |
| status | string | no | optional |
| dryRun | boolean | no | optional |
| result | object | no | optional |
| result.totalRows | integer (int32) | no | optional, format `int32` |
| result.imported | integer (int32) | no | optional, format `int32` |
| result.skipped | integer (int32) | no | optional, format `int32` |
| result.dryRun | boolean | no | optional |
| result.errors | array<object> | no | optional |
| result.errors[].row | integer (int32) | no | optional, format `int32` |
| result.errors[].message | string | no | optional |

```json
{
  "jobId": "11111111-1111-1111-1111-111111111111",
  "status": "string",
  "dryRun": true,
  "result": {
    "totalRows": "string",
    "imported": "string",
    "skipped": "string",
    "dryRun": true,
    "errors": [
      {
        "row": "string",
        "message": "string"
      }
    ]
  }
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ImportJobsController.java`

### GET /api/v1/import-jobs/{jobId}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| jobId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| jobId | string (uuid) | no | optional, format `uuid` |
| status | string | no | optional |
| dryRun | boolean | no | optional |
| result | object | no | optional |
| result.totalRows | integer (int32) | no | optional, format `int32` |
| result.imported | integer (int32) | no | optional, format `int32` |
| result.skipped | integer (int32) | no | optional, format `int32` |
| result.dryRun | boolean | no | optional |
| result.errors | array<object> | no | optional |
| result.errors[].row | integer (int32) | no | optional, format `int32` |
| result.errors[].message | string | no | optional |

```json
{
  "jobId": "11111111-1111-1111-1111-111111111111",
  "status": "string",
  "dryRun": true,
  "result": {
    "totalRows": "string",
    "imported": "string",
    "skipped": "string",
    "dryRun": true,
    "errors": [
      {
        "row": "string",
        "message": "string"
      }
    ]
  }
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `jobId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ImportJobsController.java`

## Network Discovery Service

Stage: **Asset Lifecycle**

### GET /api/v1/discovery/devices

User type: **Org admin or system admin**

Description: List or summarize devices.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| limit | query | no | integer (int32) | optional, format `int32` |
| offset | query | no | integer (int64) | optional, format `int64` |
| pageable | query | yes | Pageable | required |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| total | integer (int64) | no | optional, format `int64` |
| limit | integer (int32) | no | optional, format `int32` |
| offset | integer (int64) | no | optional, format `int64` |
| items | array<object> | no | optional |
| items[].id | string (uuid) | no | optional, format `uuid` |
| items[].ipAddress | string | no | optional |
| items[].hostname | string | no | optional |
| items[].macAddress | string | no | optional |
| items[].deviceType | string | no | optional |
| items[].openPorts | array<integer (int32)> | no | optional |
| items[].discoveryMethod | enum<string> | no | optional, enum |
| items[].status | enum<string> | no | optional, enum |
| items[].osHint | string | no | optional |
| items[].responseTimeMs | integer (int64) | no | optional, format `int64` |
| items[].lastSeenAt | string (date-time) | no | optional, format `date-time` |
| items[].promotedAssetId | string (uuid) | no | optional, format `uuid` |
| items[].createdAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "total": "string",
  "limit": "string",
  "offset": "string",
  "items": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "ipAddress": "string",
      "hostname": "string",
      "macAddress": "string",
      "deviceType": "string",
      "openPorts": [
        "string"
      ],
      "discoveryMethod": "PING",
      "status": "ONLINE",
      "osHint": "string",
      "responseTimeMs": "09:00",
      "lastSeenAt": "2026-03-27T10:15:30Z",
      "promotedAssetId": "11111111-1111-1111-1111-111111111111",
      "createdAt": "2026-03-27T10:15:30Z"
    }
  ]
}
```

Enum values:

- response `items.[].discoveryMethod`: `PING` = Ping., `PORT_SCAN` = Port scan., `MANUAL` = Manual.
- response `items.[].status`: `ONLINE` = Online., `OFFLINE` = Offline., `UNKNOWN` = Unknown., `PROMOTED` = Promoted.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/NetworkDiscoveryController.java`

### DELETE /api/v1/discovery/devices/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/NetworkDiscoveryController.java`

### GET /api/v1/discovery/devices/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| ipAddress | string | no | optional |
| hostname | string | no | optional |
| macAddress | string | no | optional |
| deviceType | string | no | optional |
| openPorts | array<integer (int32)> | no | optional |
| discoveryMethod | enum<string> | no | optional, enum |
| status | enum<string> | no | optional, enum |
| osHint | string | no | optional |
| responseTimeMs | integer (int64) | no | optional, format `int64` |
| lastSeenAt | string (date-time) | no | optional, format `date-time` |
| promotedAssetId | string (uuid) | no | optional, format `uuid` |
| createdAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "ipAddress": "string",
  "hostname": "string",
  "macAddress": "string",
  "deviceType": "string",
  "openPorts": [
    "string"
  ],
  "discoveryMethod": "PING",
  "status": "ONLINE",
  "osHint": "string",
  "responseTimeMs": "09:00",
  "lastSeenAt": "2026-03-27T10:15:30Z",
  "promotedAssetId": "11111111-1111-1111-1111-111111111111",
  "createdAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `discoveryMethod`: `PING` = Ping., `PORT_SCAN` = Port scan., `MANUAL` = Manual.
- response `status`: `ONLINE` = Online., `OFFLINE` = Offline., `UNKNOWN` = Unknown., `PROMOTED` = Promoted.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/NetworkDiscoveryController.java`

### POST /api/v1/discovery/devices/{id}/promote

User type: **Org admin or system admin**

Description: Create or trigger a new promote operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/NetworkDiscoveryController.java`

### POST /api/v1/discovery/scan

User type: **Org admin or system admin**

Description: Create or trigger a new scan operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| cidrRange | string | no | optional |
| ipAddresses | array<string> | no | optional |
| portScan | boolean | no | optional |
| ports | array<integer (int32)> | no | optional |
| timeoutMs | integer (int32) | no | optional, format `int32` |

```json
{
  "cidrRange": "string",
  "ipAddresses": [
    "string"
  ],
  "portScan": true,
  "ports": [
    "string"
  ],
  "timeoutMs": "09:00"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "ipAddress": "string",
    "hostname": "string",
    "macAddress": "string",
    "deviceType": "string",
    "openPorts": [
      "string"
    ],
    "discoveryMethod": "PING",
    "status": "ONLINE",
    "osHint": "string",
    "responseTimeMs": "09:00",
    "lastSeenAt": "2026-03-27T10:15:30Z",
    "promotedAssetId": "11111111-1111-1111-1111-111111111111",
    "createdAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- response `[].discoveryMethod`: `PING` = Ping., `PORT_SCAN` = Port scan., `MANUAL` = Manual.
- response `[].status`: `ONLINE` = Online., `OFFLINE` = Offline., `UNKNOWN` = Unknown., `PROMOTED` = Promoted.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/NetworkDiscoveryController.java`

### GET /api/v1/discovery/summary

User type: **Org admin or system admin**

Description: List or summarize summary.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/NetworkDiscoveryController.java`

## Cloud Asset Service

Stage: **Asset Lifecycle**

### GET /api/v1/cloud-assets

User type: **Authenticated user**

Description: List or summarize cloud assets.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| provider | query | no | string | optional |
| environment | query | no | string | optional |
| limit | query | no | integer (int32) | optional, format `int32` |
| offset | query | no | integer (int64) | optional, format `int64` |
| pageable | query | yes | Pageable | required |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| total | integer (int64) | no | optional, format `int64` |
| limit | integer (int32) | no | optional, format `int32` |
| offset | integer (int64) | no | optional, format `int64` |
| items | array<object> | no | optional |
| items[].id | string (uuid) | no | optional, format `uuid` |
| items[].name | string | yes | required |
| items[].provider | enum<string> | yes | required, enum |
| items[].region | string | yes | required |
| items[].resourceId | string | yes | required |
| items[].resourceType | enum<string> | yes | required, enum |
| items[].status | enum<string> | no | optional, enum |
| items[].accountId | string | no | optional |
| items[].monthlyCostEstimate | number | no | optional |
| items[].currency | string | no | optional |
| items[].environment | string | no | optional |
| items[].tags | string | no | optional |
| items[].description | string | no | optional |
| items[].lastSyncAt | string (date-time) | no | optional, format `date-time` |
| items[].createdAt | string (date-time) | no | optional, format `date-time` |
| items[].updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "total": "string",
  "limit": "string",
  "offset": "string",
  "items": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "name": "string",
      "provider": "AWS",
      "region": "string",
      "resourceId": "11111111-1111-1111-1111-111111111111",
      "resourceType": "VIRTUAL_MACHINE",
      "status": "RUNNING",
      "accountId": "11111111-1111-1111-1111-111111111111",
      "monthlyCostEstimate": 1000.0,
      "currency": "USD",
      "environment": "string",
      "tags": "string",
      "description": "string",
      "lastSyncAt": "2026-03-27T10:15:30Z",
      "createdAt": "2026-03-27T10:15:30Z",
      "updatedAt": "2026-03-27T10:15:30Z"
    }
  ]
}
```

Enum values:

- response `items.[].provider`: `AWS` = Aws., `AZURE` = Azure., `GCP` = Gcp., `ALIBABA` = Alibaba., `ORACLE_CLOUD` = Oracle cloud., `IBM_CLOUD` = Ibm cloud., `OTHER` = Other.
- response `items.[].resourceType`: `VIRTUAL_MACHINE` = Virtual machine., `STORAGE_BUCKET` = Storage bucket., `DATABASE` = Database., `LOAD_BALANCER` = Load balancer., `CONTAINER` = Container., `SERVERLESS_FUNCTION` = Serverless function., `NETWORK` = Network., `CDN` = Cdn., `DNS` = Dns., `KUBERNETES_CLUSTER` = Kubernetes cluster., `VPN_GATEWAY` = Vpn gateway., `CACHE` = Cache., `MESSAGE_QUEUE` = Message queue., `OTHER` = Other.
- response `items.[].status`: `RUNNING` = Running., `STOPPED` = Stopped., `TERMINATED` = Terminated., `PENDING` = Pending., `UNKNOWN` = Unknown.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/CloudAssetController.java`

### POST /api/v1/cloud-assets

User type: **Org admin or system admin**

Description: Create or trigger a new cloud assets operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| provider | enum<string> | yes | required, enum |
| region | string | yes | required |
| resourceId | string | yes | required |
| resourceType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| accountId | string | no | optional |
| monthlyCostEstimate | number | no | optional |
| currency | string | no | optional |
| environment | string | no | optional |
| tags | string | no | optional |
| description | string | no | optional |
| lastSyncAt | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "provider": "AWS",
  "region": "string",
  "resourceId": "11111111-1111-1111-1111-111111111111",
  "resourceType": "VIRTUAL_MACHINE",
  "status": "RUNNING",
  "accountId": "11111111-1111-1111-1111-111111111111",
  "monthlyCostEstimate": 1000.0,
  "currency": "USD",
  "environment": "string",
  "tags": "string",
  "description": "string",
  "lastSyncAt": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| provider | enum<string> | yes | required, enum |
| region | string | yes | required |
| resourceId | string | yes | required |
| resourceType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| accountId | string | no | optional |
| monthlyCostEstimate | number | no | optional |
| currency | string | no | optional |
| environment | string | no | optional |
| tags | string | no | optional |
| description | string | no | optional |
| lastSyncAt | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "provider": "AWS",
  "region": "string",
  "resourceId": "11111111-1111-1111-1111-111111111111",
  "resourceType": "VIRTUAL_MACHINE",
  "status": "RUNNING",
  "accountId": "11111111-1111-1111-1111-111111111111",
  "monthlyCostEstimate": 1000.0,
  "currency": "USD",
  "environment": "string",
  "tags": "string",
  "description": "string",
  "lastSyncAt": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `provider`: `AWS` = Aws., `AZURE` = Azure., `GCP` = Gcp., `ALIBABA` = Alibaba., `ORACLE_CLOUD` = Oracle cloud., `IBM_CLOUD` = Ibm cloud., `OTHER` = Other.
- request `resourceType`: `VIRTUAL_MACHINE` = Virtual machine., `STORAGE_BUCKET` = Storage bucket., `DATABASE` = Database., `LOAD_BALANCER` = Load balancer., `CONTAINER` = Container., `SERVERLESS_FUNCTION` = Serverless function., `NETWORK` = Network., `CDN` = Cdn., `DNS` = Dns., `KUBERNETES_CLUSTER` = Kubernetes cluster., `VPN_GATEWAY` = Vpn gateway., `CACHE` = Cache., `MESSAGE_QUEUE` = Message queue., `OTHER` = Other.
- request `status`: `RUNNING` = Running., `STOPPED` = Stopped., `TERMINATED` = Terminated., `PENDING` = Pending., `UNKNOWN` = Unknown.
- response `provider`: `AWS` = Aws., `AZURE` = Azure., `GCP` = Gcp., `ALIBABA` = Alibaba., `ORACLE_CLOUD` = Oracle cloud., `IBM_CLOUD` = Ibm cloud., `OTHER` = Other.
- response `resourceType`: `VIRTUAL_MACHINE` = Virtual machine., `STORAGE_BUCKET` = Storage bucket., `DATABASE` = Database., `LOAD_BALANCER` = Load balancer., `CONTAINER` = Container., `SERVERLESS_FUNCTION` = Serverless function., `NETWORK` = Network., `CDN` = Cdn., `DNS` = Dns., `KUBERNETES_CLUSTER` = Kubernetes cluster., `VPN_GATEWAY` = Vpn gateway., `CACHE` = Cache., `MESSAGE_QUEUE` = Message queue., `OTHER` = Other.
- response `status`: `RUNNING` = Running., `STOPPED` = Stopped., `TERMINATED` = Terminated., `PENDING` = Pending., `UNKNOWN` = Unknown.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `resourceId` before submitting the payload.
- Resolve `accountId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/CloudAssetController.java`

### GET /api/v1/cloud-assets/cost-summary

User type: **Authenticated user**

Description: List or summarize cost summary.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| totalMonthlyCost | number | no | optional |
| currency | string | no | optional |
| costByProvider | object<string, number> | no | optional |
| costByEnvironment | object<string, number> | no | optional |
| topAssets | array<object> | no | optional |
| topAssets[].assetName | string | no | optional |
| topAssets[].resourceType | string | no | optional |
| topAssets[].monthlyCost | number | no | optional |

```json
{
  "totalMonthlyCost": 1000.0,
  "currency": "USD",
  "costByProvider": {
    "key": 1000.0
  },
  "costByEnvironment": {
    "key": 1000.0
  },
  "topAssets": [
    {
      "assetName": "string",
      "resourceType": "string",
      "monthlyCost": 1000.0
    }
  ]
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/CloudAssetController.java`

### DELETE /api/v1/cloud-assets/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/CloudAssetController.java`

### GET /api/v1/cloud-assets/{id}

User type: **Authenticated user**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| provider | enum<string> | yes | required, enum |
| region | string | yes | required |
| resourceId | string | yes | required |
| resourceType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| accountId | string | no | optional |
| monthlyCostEstimate | number | no | optional |
| currency | string | no | optional |
| environment | string | no | optional |
| tags | string | no | optional |
| description | string | no | optional |
| lastSyncAt | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "provider": "AWS",
  "region": "string",
  "resourceId": "11111111-1111-1111-1111-111111111111",
  "resourceType": "VIRTUAL_MACHINE",
  "status": "RUNNING",
  "accountId": "11111111-1111-1111-1111-111111111111",
  "monthlyCostEstimate": 1000.0,
  "currency": "USD",
  "environment": "string",
  "tags": "string",
  "description": "string",
  "lastSyncAt": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `provider`: `AWS` = Aws., `AZURE` = Azure., `GCP` = Gcp., `ALIBABA` = Alibaba., `ORACLE_CLOUD` = Oracle cloud., `IBM_CLOUD` = Ibm cloud., `OTHER` = Other.
- response `resourceType`: `VIRTUAL_MACHINE` = Virtual machine., `STORAGE_BUCKET` = Storage bucket., `DATABASE` = Database., `LOAD_BALANCER` = Load balancer., `CONTAINER` = Container., `SERVERLESS_FUNCTION` = Serverless function., `NETWORK` = Network., `CDN` = Cdn., `DNS` = Dns., `KUBERNETES_CLUSTER` = Kubernetes cluster., `VPN_GATEWAY` = Vpn gateway., `CACHE` = Cache., `MESSAGE_QUEUE` = Message queue., `OTHER` = Other.
- response `status`: `RUNNING` = Running., `STOPPED` = Stopped., `TERMINATED` = Terminated., `PENDING` = Pending., `UNKNOWN` = Unknown.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/CloudAssetController.java`

### PUT /api/v1/cloud-assets/{id}

User type: **Org admin or system admin**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| provider | enum<string> | yes | required, enum |
| region | string | yes | required |
| resourceId | string | yes | required |
| resourceType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| accountId | string | no | optional |
| monthlyCostEstimate | number | no | optional |
| currency | string | no | optional |
| environment | string | no | optional |
| tags | string | no | optional |
| description | string | no | optional |
| lastSyncAt | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "provider": "AWS",
  "region": "string",
  "resourceId": "11111111-1111-1111-1111-111111111111",
  "resourceType": "VIRTUAL_MACHINE",
  "status": "RUNNING",
  "accountId": "11111111-1111-1111-1111-111111111111",
  "monthlyCostEstimate": 1000.0,
  "currency": "USD",
  "environment": "string",
  "tags": "string",
  "description": "string",
  "lastSyncAt": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| provider | enum<string> | yes | required, enum |
| region | string | yes | required |
| resourceId | string | yes | required |
| resourceType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| accountId | string | no | optional |
| monthlyCostEstimate | number | no | optional |
| currency | string | no | optional |
| environment | string | no | optional |
| tags | string | no | optional |
| description | string | no | optional |
| lastSyncAt | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "provider": "AWS",
  "region": "string",
  "resourceId": "11111111-1111-1111-1111-111111111111",
  "resourceType": "VIRTUAL_MACHINE",
  "status": "RUNNING",
  "accountId": "11111111-1111-1111-1111-111111111111",
  "monthlyCostEstimate": 1000.0,
  "currency": "USD",
  "environment": "string",
  "tags": "string",
  "description": "string",
  "lastSyncAt": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `provider`: `AWS` = Aws., `AZURE` = Azure., `GCP` = Gcp., `ALIBABA` = Alibaba., `ORACLE_CLOUD` = Oracle cloud., `IBM_CLOUD` = Ibm cloud., `OTHER` = Other.
- request `resourceType`: `VIRTUAL_MACHINE` = Virtual machine., `STORAGE_BUCKET` = Storage bucket., `DATABASE` = Database., `LOAD_BALANCER` = Load balancer., `CONTAINER` = Container., `SERVERLESS_FUNCTION` = Serverless function., `NETWORK` = Network., `CDN` = Cdn., `DNS` = Dns., `KUBERNETES_CLUSTER` = Kubernetes cluster., `VPN_GATEWAY` = Vpn gateway., `CACHE` = Cache., `MESSAGE_QUEUE` = Message queue., `OTHER` = Other.
- request `status`: `RUNNING` = Running., `STOPPED` = Stopped., `TERMINATED` = Terminated., `PENDING` = Pending., `UNKNOWN` = Unknown.
- response `provider`: `AWS` = Aws., `AZURE` = Azure., `GCP` = Gcp., `ALIBABA` = Alibaba., `ORACLE_CLOUD` = Oracle cloud., `IBM_CLOUD` = Ibm cloud., `OTHER` = Other.
- response `resourceType`: `VIRTUAL_MACHINE` = Virtual machine., `STORAGE_BUCKET` = Storage bucket., `DATABASE` = Database., `LOAD_BALANCER` = Load balancer., `CONTAINER` = Container., `SERVERLESS_FUNCTION` = Serverless function., `NETWORK` = Network., `CDN` = Cdn., `DNS` = Dns., `KUBERNETES_CLUSTER` = Kubernetes cluster., `VPN_GATEWAY` = Vpn gateway., `CACHE` = Cache., `MESSAGE_QUEUE` = Message queue., `OTHER` = Other.
- response `status`: `RUNNING` = Running., `STOPPED` = Stopped., `TERMINATED` = Terminated., `PENDING` = Pending., `UNKNOWN` = Unknown.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `resourceId` before submitting the payload.
- Resolve `accountId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/CloudAssetController.java`

### POST /api/v1/cloud-assets/{id}/cost

User type: **Org admin or system admin**

Description: Create or trigger a new cost operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| billingMonth | string | yes | required, pattern `YYYY-MM` |
| amount | number | yes | required |
| serviceName | string | no | optional |

```json
{
  "billingMonth": "2026-03",
  "amount": 1000.0,
  "serviceName": "string"
}
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/CloudAssetController.java`

## Software License Service

Stage: **Asset Lifecycle**

### GET /api/v1/licenses

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize licenses.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "vendor": "string",
    "licenseKey": "string",
    "productName": "string",
    "version": "string",
    "licenseType": "PERPETUAL",
    "status": "ACTIVE",
    "totalSeats": "string",
    "usedSeats": "string",
    "purchaseCost": 1000.0,
    "annualRenewalCost": 1000.0,
    "currency": "USD",
    "purchaseDate": "2026-03-27",
    "expiryDate": "2026-03-27",
    "renewalDate": "2026-03-27",
    "autoRenew": true,
    "licenseDocumentUrl": "https://example.com/resource",
    "notes": "string",
    "assetId": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "availableSeats": "string",
    "daysUntilExpiry": "string"
  }
]
```

Enum values:

- response `[].licenseType`: `PERPETUAL` = Perpetual., `SUBSCRIPTION` = Subscription., `VOLUME` = Volume., `OPEN_SOURCE` = Open source., `TRIAL` = Trial., `ENTERPRISE` = Enterprise., `OEM` = Oem.
- response `[].status`: `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `SUSPENDED` = Suspended., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/SoftwareLicenseController.java`

### POST /api/v1/licenses

User type: **Org admin or system admin**

Description: Create or trigger a new licenses operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| vendor | string | yes | required |
| licenseKey | string | no | optional |
| productName | string | no | optional |
| version | string | no | optional |
| licenseType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| totalSeats | integer (int32) | no | optional, format `int32` |
| usedSeats | integer (int32) | no | optional, format `int32` |
| purchaseCost | number | no | optional |
| annualRenewalCost | number | no | optional |
| currency | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| expiryDate | string (date) | no | optional, format `date` |
| renewalDate | string (date) | no | optional, format `date` |
| autoRenew | boolean | no | optional |
| licenseDocumentUrl | string | no | optional |
| notes | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| availableSeats | integer (int32) | no | optional, format `int32` |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "vendor": "string",
  "licenseKey": "string",
  "productName": "string",
  "version": "string",
  "licenseType": "PERPETUAL",
  "status": "ACTIVE",
  "totalSeats": "string",
  "usedSeats": "string",
  "purchaseCost": 1000.0,
  "annualRenewalCost": 1000.0,
  "currency": "USD",
  "purchaseDate": "2026-03-27",
  "expiryDate": "2026-03-27",
  "renewalDate": "2026-03-27",
  "autoRenew": true,
  "licenseDocumentUrl": "https://example.com/resource",
  "notes": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "availableSeats": "string",
  "daysUntilExpiry": "string"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| vendor | string | yes | required |
| licenseKey | string | no | optional |
| productName | string | no | optional |
| version | string | no | optional |
| licenseType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| totalSeats | integer (int32) | no | optional, format `int32` |
| usedSeats | integer (int32) | no | optional, format `int32` |
| purchaseCost | number | no | optional |
| annualRenewalCost | number | no | optional |
| currency | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| expiryDate | string (date) | no | optional, format `date` |
| renewalDate | string (date) | no | optional, format `date` |
| autoRenew | boolean | no | optional |
| licenseDocumentUrl | string | no | optional |
| notes | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| availableSeats | integer (int32) | no | optional, format `int32` |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "vendor": "string",
  "licenseKey": "string",
  "productName": "string",
  "version": "string",
  "licenseType": "PERPETUAL",
  "status": "ACTIVE",
  "totalSeats": "string",
  "usedSeats": "string",
  "purchaseCost": 1000.0,
  "annualRenewalCost": 1000.0,
  "currency": "USD",
  "purchaseDate": "2026-03-27",
  "expiryDate": "2026-03-27",
  "renewalDate": "2026-03-27",
  "autoRenew": true,
  "licenseDocumentUrl": "https://example.com/resource",
  "notes": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "availableSeats": "string",
  "daysUntilExpiry": "string"
}
```

Enum values:

- request `licenseType`: `PERPETUAL` = Perpetual., `SUBSCRIPTION` = Subscription., `VOLUME` = Volume., `OPEN_SOURCE` = Open source., `TRIAL` = Trial., `ENTERPRISE` = Enterprise., `OEM` = Oem.
- request `status`: `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `SUSPENDED` = Suspended., `CANCELLED` = Cancelled.
- response `licenseType`: `PERPETUAL` = Perpetual., `SUBSCRIPTION` = Subscription., `VOLUME` = Volume., `OPEN_SOURCE` = Open source., `TRIAL` = Trial., `ENTERPRISE` = Enterprise., `OEM` = Oem.
- response `status`: `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `SUSPENDED` = Suspended., `CANCELLED` = Cancelled.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `assetId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/SoftwareLicenseController.java`

### GET /api/v1/licenses/expiring-soon

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize expiring soon.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| days | query | no | integer (int32) | optional, format `int32` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "vendor": "string",
    "licenseKey": "string",
    "productName": "string",
    "version": "string",
    "licenseType": "PERPETUAL",
    "status": "ACTIVE",
    "totalSeats": "string",
    "usedSeats": "string",
    "purchaseCost": 1000.0,
    "annualRenewalCost": 1000.0,
    "currency": "USD",
    "purchaseDate": "2026-03-27",
    "expiryDate": "2026-03-27",
    "renewalDate": "2026-03-27",
    "autoRenew": true,
    "licenseDocumentUrl": "https://example.com/resource",
    "notes": "string",
    "assetId": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "availableSeats": "string",
    "daysUntilExpiry": "string"
  }
]
```

Enum values:

- response `[].licenseType`: `PERPETUAL` = Perpetual., `SUBSCRIPTION` = Subscription., `VOLUME` = Volume., `OPEN_SOURCE` = Open source., `TRIAL` = Trial., `ENTERPRISE` = Enterprise., `OEM` = Oem.
- response `[].status`: `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `SUSPENDED` = Suspended., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/SoftwareLicenseController.java`

### GET /api/v1/licenses/over-allocated

User type: **Org admin or system admin**

Description: List or summarize over allocated.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "vendor": "string",
    "licenseKey": "string",
    "productName": "string",
    "version": "string",
    "licenseType": "PERPETUAL",
    "status": "ACTIVE",
    "totalSeats": "string",
    "usedSeats": "string",
    "purchaseCost": 1000.0,
    "annualRenewalCost": 1000.0,
    "currency": "USD",
    "purchaseDate": "2026-03-27",
    "expiryDate": "2026-03-27",
    "renewalDate": "2026-03-27",
    "autoRenew": true,
    "licenseDocumentUrl": "https://example.com/resource",
    "notes": "string",
    "assetId": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "availableSeats": "string",
    "daysUntilExpiry": "string"
  }
]
```

Enum values:

- response `[].licenseType`: `PERPETUAL` = Perpetual., `SUBSCRIPTION` = Subscription., `VOLUME` = Volume., `OPEN_SOURCE` = Open source., `TRIAL` = Trial., `ENTERPRISE` = Enterprise., `OEM` = Oem.
- response `[].status`: `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `SUSPENDED` = Suspended., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/SoftwareLicenseController.java`

### GET /api/v1/licenses/utilization

User type: **Org admin or system admin**

Description: List or summarize utilization.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/SoftwareLicenseController.java`

### DELETE /api/v1/licenses/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/SoftwareLicenseController.java`

### GET /api/v1/licenses/{id}

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| vendor | string | yes | required |
| licenseKey | string | no | optional |
| productName | string | no | optional |
| version | string | no | optional |
| licenseType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| totalSeats | integer (int32) | no | optional, format `int32` |
| usedSeats | integer (int32) | no | optional, format `int32` |
| purchaseCost | number | no | optional |
| annualRenewalCost | number | no | optional |
| currency | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| expiryDate | string (date) | no | optional, format `date` |
| renewalDate | string (date) | no | optional, format `date` |
| autoRenew | boolean | no | optional |
| licenseDocumentUrl | string | no | optional |
| notes | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| availableSeats | integer (int32) | no | optional, format `int32` |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "vendor": "string",
  "licenseKey": "string",
  "productName": "string",
  "version": "string",
  "licenseType": "PERPETUAL",
  "status": "ACTIVE",
  "totalSeats": "string",
  "usedSeats": "string",
  "purchaseCost": 1000.0,
  "annualRenewalCost": 1000.0,
  "currency": "USD",
  "purchaseDate": "2026-03-27",
  "expiryDate": "2026-03-27",
  "renewalDate": "2026-03-27",
  "autoRenew": true,
  "licenseDocumentUrl": "https://example.com/resource",
  "notes": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "availableSeats": "string",
  "daysUntilExpiry": "string"
}
```

Enum values:

- response `licenseType`: `PERPETUAL` = Perpetual., `SUBSCRIPTION` = Subscription., `VOLUME` = Volume., `OPEN_SOURCE` = Open source., `TRIAL` = Trial., `ENTERPRISE` = Enterprise., `OEM` = Oem.
- response `status`: `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `SUSPENDED` = Suspended., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/SoftwareLicenseController.java`

### PATCH /api/v1/licenses/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| vendor | string | yes | required |
| licenseKey | string | no | optional |
| productName | string | no | optional |
| version | string | no | optional |
| licenseType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| totalSeats | integer (int32) | no | optional, format `int32` |
| usedSeats | integer (int32) | no | optional, format `int32` |
| purchaseCost | number | no | optional |
| annualRenewalCost | number | no | optional |
| currency | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| expiryDate | string (date) | no | optional, format `date` |
| renewalDate | string (date) | no | optional, format `date` |
| autoRenew | boolean | no | optional |
| licenseDocumentUrl | string | no | optional |
| notes | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| availableSeats | integer (int32) | no | optional, format `int32` |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "vendor": "string",
  "licenseKey": "string",
  "productName": "string",
  "version": "string",
  "licenseType": "PERPETUAL",
  "status": "ACTIVE",
  "totalSeats": "string",
  "usedSeats": "string",
  "purchaseCost": 1000.0,
  "annualRenewalCost": 1000.0,
  "currency": "USD",
  "purchaseDate": "2026-03-27",
  "expiryDate": "2026-03-27",
  "renewalDate": "2026-03-27",
  "autoRenew": true,
  "licenseDocumentUrl": "https://example.com/resource",
  "notes": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "availableSeats": "string",
  "daysUntilExpiry": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| vendor | string | yes | required |
| licenseKey | string | no | optional |
| productName | string | no | optional |
| version | string | no | optional |
| licenseType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| totalSeats | integer (int32) | no | optional, format `int32` |
| usedSeats | integer (int32) | no | optional, format `int32` |
| purchaseCost | number | no | optional |
| annualRenewalCost | number | no | optional |
| currency | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| expiryDate | string (date) | no | optional, format `date` |
| renewalDate | string (date) | no | optional, format `date` |
| autoRenew | boolean | no | optional |
| licenseDocumentUrl | string | no | optional |
| notes | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| availableSeats | integer (int32) | no | optional, format `int32` |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "vendor": "string",
  "licenseKey": "string",
  "productName": "string",
  "version": "string",
  "licenseType": "PERPETUAL",
  "status": "ACTIVE",
  "totalSeats": "string",
  "usedSeats": "string",
  "purchaseCost": 1000.0,
  "annualRenewalCost": 1000.0,
  "currency": "USD",
  "purchaseDate": "2026-03-27",
  "expiryDate": "2026-03-27",
  "renewalDate": "2026-03-27",
  "autoRenew": true,
  "licenseDocumentUrl": "https://example.com/resource",
  "notes": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "availableSeats": "string",
  "daysUntilExpiry": "string"
}
```

Enum values:

- request `licenseType`: `PERPETUAL` = Perpetual., `SUBSCRIPTION` = Subscription., `VOLUME` = Volume., `OPEN_SOURCE` = Open source., `TRIAL` = Trial., `ENTERPRISE` = Enterprise., `OEM` = Oem.
- request `status`: `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `SUSPENDED` = Suspended., `CANCELLED` = Cancelled.
- response `licenseType`: `PERPETUAL` = Perpetual., `SUBSCRIPTION` = Subscription., `VOLUME` = Volume., `OPEN_SOURCE` = Open source., `TRIAL` = Trial., `ENTERPRISE` = Enterprise., `OEM` = Oem.
- response `status`: `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `SUSPENDED` = Suspended., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `assetId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/SoftwareLicenseController.java`

### PUT /api/v1/licenses/{id}

User type: **Org admin or system admin**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| vendor | string | yes | required |
| licenseKey | string | no | optional |
| productName | string | no | optional |
| version | string | no | optional |
| licenseType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| totalSeats | integer (int32) | no | optional, format `int32` |
| usedSeats | integer (int32) | no | optional, format `int32` |
| purchaseCost | number | no | optional |
| annualRenewalCost | number | no | optional |
| currency | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| expiryDate | string (date) | no | optional, format `date` |
| renewalDate | string (date) | no | optional, format `date` |
| autoRenew | boolean | no | optional |
| licenseDocumentUrl | string | no | optional |
| notes | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| availableSeats | integer (int32) | no | optional, format `int32` |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "vendor": "string",
  "licenseKey": "string",
  "productName": "string",
  "version": "string",
  "licenseType": "PERPETUAL",
  "status": "ACTIVE",
  "totalSeats": "string",
  "usedSeats": "string",
  "purchaseCost": 1000.0,
  "annualRenewalCost": 1000.0,
  "currency": "USD",
  "purchaseDate": "2026-03-27",
  "expiryDate": "2026-03-27",
  "renewalDate": "2026-03-27",
  "autoRenew": true,
  "licenseDocumentUrl": "https://example.com/resource",
  "notes": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "availableSeats": "string",
  "daysUntilExpiry": "string"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| vendor | string | yes | required |
| licenseKey | string | no | optional |
| productName | string | no | optional |
| version | string | no | optional |
| licenseType | enum<string> | yes | required, enum |
| status | enum<string> | no | optional, enum |
| totalSeats | integer (int32) | no | optional, format `int32` |
| usedSeats | integer (int32) | no | optional, format `int32` |
| purchaseCost | number | no | optional |
| annualRenewalCost | number | no | optional |
| currency | string | no | optional |
| purchaseDate | string (date) | no | optional, format `date` |
| expiryDate | string (date) | no | optional, format `date` |
| renewalDate | string (date) | no | optional, format `date` |
| autoRenew | boolean | no | optional |
| licenseDocumentUrl | string | no | optional |
| notes | string | no | optional |
| assetId | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| availableSeats | integer (int32) | no | optional, format `int32` |
| daysUntilExpiry | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "vendor": "string",
  "licenseKey": "string",
  "productName": "string",
  "version": "string",
  "licenseType": "PERPETUAL",
  "status": "ACTIVE",
  "totalSeats": "string",
  "usedSeats": "string",
  "purchaseCost": 1000.0,
  "annualRenewalCost": 1000.0,
  "currency": "USD",
  "purchaseDate": "2026-03-27",
  "expiryDate": "2026-03-27",
  "renewalDate": "2026-03-27",
  "autoRenew": true,
  "licenseDocumentUrl": "https://example.com/resource",
  "notes": "string",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "availableSeats": "string",
  "daysUntilExpiry": "string"
}
```

Enum values:

- request `licenseType`: `PERPETUAL` = Perpetual., `SUBSCRIPTION` = Subscription., `VOLUME` = Volume., `OPEN_SOURCE` = Open source., `TRIAL` = Trial., `ENTERPRISE` = Enterprise., `OEM` = Oem.
- request `status`: `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `SUSPENDED` = Suspended., `CANCELLED` = Cancelled.
- response `licenseType`: `PERPETUAL` = Perpetual., `SUBSCRIPTION` = Subscription., `VOLUME` = Volume., `OPEN_SOURCE` = Open source., `TRIAL` = Trial., `ENTERPRISE` = Enterprise., `OEM` = Oem.
- response `status`: `ACTIVE` = Active., `EXPIRING_SOON` = Expiring soon., `EXPIRED` = Expired., `SUSPENDED` = Suspended., `CANCELLED` = Cancelled.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `assetId` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/SoftwareLicenseController.java`

## Vendor Performance Service

Stage: **Asset Lifecycle**

### GET /api/v1/vendor-reviews

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize vendor reviews.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| supplierId | query | no | string (uuid) | optional, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "supplierId": "11111111-1111-1111-1111-111111111111",
    "supplierName": "string",
    "rating": 1.0,
    "deliveryScore": "string",
    "qualityScore": "string",
    "supportScore": "string",
    "feedback": "string",
    "periodStart": "2026-03-27",
    "periodEnd": "2026-03-27",
    "reviewedById": "11111111-1111-1111-1111-111111111111",
    "reviewedByEmail": "jane.admin@example.com"
  }
]
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/VendorPerformanceController.java`

### POST /api/v1/vendor-reviews

User type: **Org admin or system admin**

Description: Create or trigger a new vendor reviews operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| supplierName | string | no | optional |
| rating | number | yes | required, min 1.0, max 5.0 |
| deliveryScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| qualityScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| supportScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| feedback | string | no | optional |
| periodStart | string (date) | no | optional, format `date` |
| periodEnd | string (date) | no | optional, format `date` |
| reviewedById | string (uuid) | no | optional, format `uuid` |
| reviewedByEmail | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "supplierName": "string",
  "rating": 1.0,
  "deliveryScore": "string",
  "qualityScore": "string",
  "supportScore": "string",
  "feedback": "string",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "reviewedById": "11111111-1111-1111-1111-111111111111",
  "reviewedByEmail": "jane.admin@example.com"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| supplierName | string | no | optional |
| rating | number | yes | required, min 1.0, max 5.0 |
| deliveryScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| qualityScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| supportScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| feedback | string | no | optional |
| periodStart | string (date) | no | optional, format `date` |
| periodEnd | string (date) | no | optional, format `date` |
| reviewedById | string (uuid) | no | optional, format `uuid` |
| reviewedByEmail | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "supplierName": "string",
  "rating": 1.0,
  "deliveryScore": "string",
  "qualityScore": "string",
  "supportScore": "string",
  "feedback": "string",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "reviewedById": "11111111-1111-1111-1111-111111111111",
  "reviewedByEmail": "jane.admin@example.com"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `supplierId` before submitting the payload.
- Resolve `reviewedById` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/VendorPerformanceController.java`

### GET /api/v1/vendor-reviews/suppliers/{supplierId}/summary

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| supplierId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `supplierId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/VendorPerformanceController.java`

### DELETE /api/v1/vendor-reviews/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/VendorPerformanceController.java`

### GET /api/v1/vendor-reviews/{id}

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| supplierName | string | no | optional |
| rating | number | yes | required, min 1.0, max 5.0 |
| deliveryScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| qualityScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| supportScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| feedback | string | no | optional |
| periodStart | string (date) | no | optional, format `date` |
| periodEnd | string (date) | no | optional, format `date` |
| reviewedById | string (uuid) | no | optional, format `uuid` |
| reviewedByEmail | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "supplierName": "string",
  "rating": 1.0,
  "deliveryScore": "string",
  "qualityScore": "string",
  "supportScore": "string",
  "feedback": "string",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "reviewedById": "11111111-1111-1111-1111-111111111111",
  "reviewedByEmail": "jane.admin@example.com"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/VendorPerformanceController.java`

### PUT /api/v1/vendor-reviews/{id}

User type: **Org admin or system admin**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| supplierName | string | no | optional |
| rating | number | yes | required, min 1.0, max 5.0 |
| deliveryScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| qualityScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| supportScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| feedback | string | no | optional |
| periodStart | string (date) | no | optional, format `date` |
| periodEnd | string (date) | no | optional, format `date` |
| reviewedById | string (uuid) | no | optional, format `uuid` |
| reviewedByEmail | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "supplierName": "string",
  "rating": 1.0,
  "deliveryScore": "string",
  "qualityScore": "string",
  "supportScore": "string",
  "feedback": "string",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "reviewedById": "11111111-1111-1111-1111-111111111111",
  "reviewedByEmail": "jane.admin@example.com"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| supplierId | string (uuid) | no | optional, format `uuid` |
| supplierName | string | no | optional |
| rating | number | yes | required, min 1.0, max 5.0 |
| deliveryScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| qualityScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| supportScore | integer (int32) | no | optional, min 1, max 5, format `int32` |
| feedback | string | no | optional |
| periodStart | string (date) | no | optional, format `date` |
| periodEnd | string (date) | no | optional, format `date` |
| reviewedById | string (uuid) | no | optional, format `uuid` |
| reviewedByEmail | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "supplierName": "string",
  "rating": 1.0,
  "deliveryScore": "string",
  "qualityScore": "string",
  "supportScore": "string",
  "feedback": "string",
  "periodStart": "2026-03-27",
  "periodEnd": "2026-03-27",
  "reviewedById": "11111111-1111-1111-1111-111111111111",
  "reviewedByEmail": "jane.admin@example.com"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `supplierId` before submitting the payload.
- Resolve `reviewedById` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/VendorPerformanceController.java`

## Maintenance Service

Stage: **Asset Lifecycle**

### GET /api/v1/maintenance

User type: **Authenticated user or system admin**

Description: List or summarize maintenance.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| assetId | query | no | string (uuid) | optional, format `uuid` |
| vendorId | query | no | string (uuid) | optional, format `uuid` |
| dueBefore | query | no | string (date) | optional, format `date` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "assetId": "11111111-1111-1111-1111-111111111111",
    "maintenanceType": "PREVENTIVE",
    "description": "string",
    "scheduledDate": "2026-03-27",
    "performedDate": "2026-03-27",
    "vendorId": "11111111-1111-1111-1111-111111111111",
    "cost": 1000.0,
    "status": "SCHEDULED",
    "nextDueDate": "2026-03-27"
  }
]
```

Enum values:

- response `[].maintenanceType`: `PREVENTIVE` = Preventive., `CORRECTIVE` = Corrective., `EMERGENCY` = Emergency., `ROUTINE` = Routine.
- response `[].status`: `SCHEDULED` = Scheduled., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/MaintenanceController.java`

### POST /api/v1/maintenance

User type: **Authenticated user**

Description: Create or trigger a new maintenance operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| maintenanceType | enum<string> | yes | required, enum |
| description | string | no | optional |
| scheduledDate | string (date) | no | optional, format `date` |
| performedDate | string (date) | no | optional, format `date` |
| vendorId | string (uuid) | no | optional, format `uuid` |
| cost | number | no | optional |
| status | enum<string> | no | optional, enum |
| nextDueDate | string (date) | no | optional, format `date` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "maintenanceType": "PREVENTIVE",
  "description": "string",
  "scheduledDate": "2026-03-27",
  "performedDate": "2026-03-27",
  "vendorId": "11111111-1111-1111-1111-111111111111",
  "cost": 1000.0,
  "status": "SCHEDULED",
  "nextDueDate": "2026-03-27"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| maintenanceType | enum<string> | yes | required, enum |
| description | string | no | optional |
| scheduledDate | string (date) | no | optional, format `date` |
| performedDate | string (date) | no | optional, format `date` |
| vendorId | string (uuid) | no | optional, format `uuid` |
| cost | number | no | optional |
| status | enum<string> | no | optional, enum |
| nextDueDate | string (date) | no | optional, format `date` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "maintenanceType": "PREVENTIVE",
  "description": "string",
  "scheduledDate": "2026-03-27",
  "performedDate": "2026-03-27",
  "vendorId": "11111111-1111-1111-1111-111111111111",
  "cost": 1000.0,
  "status": "SCHEDULED",
  "nextDueDate": "2026-03-27"
}
```

Enum values:

- request `maintenanceType`: `PREVENTIVE` = Preventive., `CORRECTIVE` = Corrective., `EMERGENCY` = Emergency., `ROUTINE` = Routine.
- request `status`: `SCHEDULED` = Scheduled., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `CANCELLED` = Cancelled.
- response `maintenanceType`: `PREVENTIVE` = Preventive., `CORRECTIVE` = Corrective., `EMERGENCY` = Emergency., `ROUTINE` = Routine.
- response `status`: `SCHEDULED` = Scheduled., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `CANCELLED` = Cancelled.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `assetId` before submitting the payload.
- Resolve `vendorId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/MaintenanceController.java`

### DELETE /api/v1/maintenance/{id}

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/MaintenanceController.java`

### GET /api/v1/maintenance/{id}

User type: **Authenticated user or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| maintenanceType | enum<string> | yes | required, enum |
| description | string | no | optional |
| scheduledDate | string (date) | no | optional, format `date` |
| performedDate | string (date) | no | optional, format `date` |
| vendorId | string (uuid) | no | optional, format `uuid` |
| cost | number | no | optional |
| status | enum<string> | no | optional, enum |
| nextDueDate | string (date) | no | optional, format `date` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "maintenanceType": "PREVENTIVE",
  "description": "string",
  "scheduledDate": "2026-03-27",
  "performedDate": "2026-03-27",
  "vendorId": "11111111-1111-1111-1111-111111111111",
  "cost": 1000.0,
  "status": "SCHEDULED",
  "nextDueDate": "2026-03-27"
}
```

Enum values:

- response `maintenanceType`: `PREVENTIVE` = Preventive., `CORRECTIVE` = Corrective., `EMERGENCY` = Emergency., `ROUTINE` = Routine.
- response `status`: `SCHEDULED` = Scheduled., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/MaintenanceController.java`

### PATCH /api/v1/maintenance/{id}

User type: **Authenticated user**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| maintenanceType | enum<string> | yes | required, enum |
| description | string | no | optional |
| scheduledDate | string (date) | no | optional, format `date` |
| performedDate | string (date) | no | optional, format `date` |
| vendorId | string (uuid) | no | optional, format `uuid` |
| cost | number | no | optional |
| status | enum<string> | no | optional, enum |
| nextDueDate | string (date) | no | optional, format `date` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "maintenanceType": "PREVENTIVE",
  "description": "string",
  "scheduledDate": "2026-03-27",
  "performedDate": "2026-03-27",
  "vendorId": "11111111-1111-1111-1111-111111111111",
  "cost": 1000.0,
  "status": "SCHEDULED",
  "nextDueDate": "2026-03-27"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| maintenanceType | enum<string> | yes | required, enum |
| description | string | no | optional |
| scheduledDate | string (date) | no | optional, format `date` |
| performedDate | string (date) | no | optional, format `date` |
| vendorId | string (uuid) | no | optional, format `uuid` |
| cost | number | no | optional |
| status | enum<string> | no | optional, enum |
| nextDueDate | string (date) | no | optional, format `date` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "maintenanceType": "PREVENTIVE",
  "description": "string",
  "scheduledDate": "2026-03-27",
  "performedDate": "2026-03-27",
  "vendorId": "11111111-1111-1111-1111-111111111111",
  "cost": 1000.0,
  "status": "SCHEDULED",
  "nextDueDate": "2026-03-27"
}
```

Enum values:

- request `maintenanceType`: `PREVENTIVE` = Preventive., `CORRECTIVE` = Corrective., `EMERGENCY` = Emergency., `ROUTINE` = Routine.
- request `status`: `SCHEDULED` = Scheduled., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `CANCELLED` = Cancelled.
- response `maintenanceType`: `PREVENTIVE` = Preventive., `CORRECTIVE` = Corrective., `EMERGENCY` = Emergency., `ROUTINE` = Routine.
- response `status`: `SCHEDULED` = Scheduled., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `assetId` before submitting the payload.
- Resolve `vendorId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/MaintenanceController.java`

### PUT /api/v1/maintenance/{id}

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| maintenanceType | enum<string> | yes | required, enum |
| description | string | no | optional |
| scheduledDate | string (date) | no | optional, format `date` |
| performedDate | string (date) | no | optional, format `date` |
| vendorId | string (uuid) | no | optional, format `uuid` |
| cost | number | no | optional |
| status | enum<string> | no | optional, enum |
| nextDueDate | string (date) | no | optional, format `date` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "maintenanceType": "PREVENTIVE",
  "description": "string",
  "scheduledDate": "2026-03-27",
  "performedDate": "2026-03-27",
  "vendorId": "11111111-1111-1111-1111-111111111111",
  "cost": 1000.0,
  "status": "SCHEDULED",
  "nextDueDate": "2026-03-27"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| maintenanceType | enum<string> | yes | required, enum |
| description | string | no | optional |
| scheduledDate | string (date) | no | optional, format `date` |
| performedDate | string (date) | no | optional, format `date` |
| vendorId | string (uuid) | no | optional, format `uuid` |
| cost | number | no | optional |
| status | enum<string> | no | optional, enum |
| nextDueDate | string (date) | no | optional, format `date` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "maintenanceType": "PREVENTIVE",
  "description": "string",
  "scheduledDate": "2026-03-27",
  "performedDate": "2026-03-27",
  "vendorId": "11111111-1111-1111-1111-111111111111",
  "cost": 1000.0,
  "status": "SCHEDULED",
  "nextDueDate": "2026-03-27"
}
```

Enum values:

- request `maintenanceType`: `PREVENTIVE` = Preventive., `CORRECTIVE` = Corrective., `EMERGENCY` = Emergency., `ROUTINE` = Routine.
- request `status`: `SCHEDULED` = Scheduled., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `CANCELLED` = Cancelled.
- response `maintenanceType`: `PREVENTIVE` = Preventive., `CORRECTIVE` = Corrective., `EMERGENCY` = Emergency., `ROUTINE` = Routine.
- response `status`: `SCHEDULED` = Scheduled., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `CANCELLED` = Cancelled.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `assetId` before submitting the payload.
- Resolve `vendorId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/MaintenanceController.java`

### POST /api/v1/maintenance/{id}/complete

User type: **Authenticated user**

Description: Complete the targeted v1 workflow.

When to call: Call when the related workflow is finished.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| maintenanceType | enum<string> | yes | required, enum |
| description | string | no | optional |
| scheduledDate | string (date) | no | optional, format `date` |
| performedDate | string (date) | no | optional, format `date` |
| vendorId | string (uuid) | no | optional, format `uuid` |
| cost | number | no | optional |
| status | enum<string> | no | optional, enum |
| nextDueDate | string (date) | no | optional, format `date` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "maintenanceType": "PREVENTIVE",
  "description": "string",
  "scheduledDate": "2026-03-27",
  "performedDate": "2026-03-27",
  "vendorId": "11111111-1111-1111-1111-111111111111",
  "cost": 1000.0,
  "status": "SCHEDULED",
  "nextDueDate": "2026-03-27"
}
```

Enum values:

- response `maintenanceType`: `PREVENTIVE` = Preventive., `CORRECTIVE` = Corrective., `EMERGENCY` = Emergency., `ROUTINE` = Routine.
- response `status`: `SCHEDULED` = Scheduled., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/MaintenanceController.java`

## Asset Transfer Service

Stage: **Asset Lifecycle**

### GET /api/v1/asset-transfers

User type: **Authenticated user or system admin**

Description: List or summarize asset transfers.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| assetId | query | no | string (uuid) | optional, format `uuid` |
| fromDepartmentId | query | no | string (uuid) | optional, format `uuid` |
| toDepartmentId | query | no | string (uuid) | optional, format `uuid` |
| requestedById | query | no | string (uuid) | optional, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "assetId": "11111111-1111-1111-1111-111111111111",
    "fromDepartmentId": "11111111-1111-1111-1111-111111111111",
    "toDepartmentId": "11111111-1111-1111-1111-111111111111",
    "fromLocationId": "11111111-1111-1111-1111-111111111111",
    "toLocationId": "11111111-1111-1111-1111-111111111111",
    "requestedById": "11111111-1111-1111-1111-111111111111",
    "approvedById": "11111111-1111-1111-1111-111111111111",
    "transferDate": "2026-03-27",
    "status": "REQUESTED",
    "reason": "string"
  }
]
```

Enum values:

- response `[].status`: `REQUESTED` = Requested., `APPROVED` = Approved., `REJECTED` = Rejected., `IN_TRANSIT` = In transit., `COMPLETED` = Completed., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetTransferController.java`

### POST /api/v1/asset-transfers

User type: **Authenticated user or system admin**

Description: Create or trigger a new asset transfers operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| fromDepartmentId | string (uuid) | yes | required, format `uuid` |
| toDepartmentId | string (uuid) | yes | required, format `uuid` |
| fromLocationId | string (uuid) | no | optional, format `uuid` |
| toLocationId | string (uuid) | no | optional, format `uuid` |
| requestedById | string (uuid) | yes | required, format `uuid` |
| approvedById | string (uuid) | no | optional, format `uuid` |
| transferDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| reason | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "fromDepartmentId": "11111111-1111-1111-1111-111111111111",
  "toDepartmentId": "11111111-1111-1111-1111-111111111111",
  "fromLocationId": "11111111-1111-1111-1111-111111111111",
  "toLocationId": "11111111-1111-1111-1111-111111111111",
  "requestedById": "11111111-1111-1111-1111-111111111111",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "transferDate": "2026-03-27",
  "status": "REQUESTED",
  "reason": "string"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| fromDepartmentId | string (uuid) | yes | required, format `uuid` |
| toDepartmentId | string (uuid) | yes | required, format `uuid` |
| fromLocationId | string (uuid) | no | optional, format `uuid` |
| toLocationId | string (uuid) | no | optional, format `uuid` |
| requestedById | string (uuid) | yes | required, format `uuid` |
| approvedById | string (uuid) | no | optional, format `uuid` |
| transferDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| reason | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "fromDepartmentId": "11111111-1111-1111-1111-111111111111",
  "toDepartmentId": "11111111-1111-1111-1111-111111111111",
  "fromLocationId": "11111111-1111-1111-1111-111111111111",
  "toLocationId": "11111111-1111-1111-1111-111111111111",
  "requestedById": "11111111-1111-1111-1111-111111111111",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "transferDate": "2026-03-27",
  "status": "REQUESTED",
  "reason": "string"
}
```

Enum values:

- request `status`: `REQUESTED` = Requested., `APPROVED` = Approved., `REJECTED` = Rejected., `IN_TRANSIT` = In transit., `COMPLETED` = Completed., `CANCELLED` = Cancelled.
- response `status`: `REQUESTED` = Requested., `APPROVED` = Approved., `REJECTED` = Rejected., `IN_TRANSIT` = In transit., `COMPLETED` = Completed., `CANCELLED` = Cancelled.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `assetId` before submitting the payload.
- Resolve `fromDepartmentId` before submitting the payload.
- Resolve `toDepartmentId` before submitting the payload.
- Resolve `fromLocationId` before submitting the payload.
- Resolve `toLocationId` before submitting the payload.
- Resolve `requestedById` before submitting the payload.
- Resolve `approvedById` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetTransferController.java`

### DELETE /api/v1/asset-transfers/{id}

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetTransferController.java`

### GET /api/v1/asset-transfers/{id}

User type: **Authenticated user or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| fromDepartmentId | string (uuid) | yes | required, format `uuid` |
| toDepartmentId | string (uuid) | yes | required, format `uuid` |
| fromLocationId | string (uuid) | no | optional, format `uuid` |
| toLocationId | string (uuid) | no | optional, format `uuid` |
| requestedById | string (uuid) | yes | required, format `uuid` |
| approvedById | string (uuid) | no | optional, format `uuid` |
| transferDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| reason | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "fromDepartmentId": "11111111-1111-1111-1111-111111111111",
  "toDepartmentId": "11111111-1111-1111-1111-111111111111",
  "fromLocationId": "11111111-1111-1111-1111-111111111111",
  "toLocationId": "11111111-1111-1111-1111-111111111111",
  "requestedById": "11111111-1111-1111-1111-111111111111",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "transferDate": "2026-03-27",
  "status": "REQUESTED",
  "reason": "string"
}
```

Enum values:

- response `status`: `REQUESTED` = Requested., `APPROVED` = Approved., `REJECTED` = Rejected., `IN_TRANSIT` = In transit., `COMPLETED` = Completed., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetTransferController.java`

### POST /api/v1/asset-transfers/{id}/approve

User type: **Authenticated user**

Description: Approve the targeted v1 record.

When to call: Call from an approval action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| fromDepartmentId | string (uuid) | yes | required, format `uuid` |
| toDepartmentId | string (uuid) | yes | required, format `uuid` |
| fromLocationId | string (uuid) | no | optional, format `uuid` |
| toLocationId | string (uuid) | no | optional, format `uuid` |
| requestedById | string (uuid) | yes | required, format `uuid` |
| approvedById | string (uuid) | no | optional, format `uuid` |
| transferDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| reason | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "fromDepartmentId": "11111111-1111-1111-1111-111111111111",
  "toDepartmentId": "11111111-1111-1111-1111-111111111111",
  "fromLocationId": "11111111-1111-1111-1111-111111111111",
  "toLocationId": "11111111-1111-1111-1111-111111111111",
  "requestedById": "11111111-1111-1111-1111-111111111111",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "transferDate": "2026-03-27",
  "status": "REQUESTED",
  "reason": "string"
}
```

Enum values:

- response `status`: `REQUESTED` = Requested., `APPROVED` = Approved., `REJECTED` = Rejected., `IN_TRANSIT` = In transit., `COMPLETED` = Completed., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetTransferController.java`

### POST /api/v1/asset-transfers/{id}/complete

User type: **Authenticated user**

Description: Complete the targeted v1 workflow.

When to call: Call when the related workflow is finished.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| fromDepartmentId | string (uuid) | yes | required, format `uuid` |
| toDepartmentId | string (uuid) | yes | required, format `uuid` |
| fromLocationId | string (uuid) | no | optional, format `uuid` |
| toLocationId | string (uuid) | no | optional, format `uuid` |
| requestedById | string (uuid) | yes | required, format `uuid` |
| approvedById | string (uuid) | no | optional, format `uuid` |
| transferDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| reason | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "fromDepartmentId": "11111111-1111-1111-1111-111111111111",
  "toDepartmentId": "11111111-1111-1111-1111-111111111111",
  "fromLocationId": "11111111-1111-1111-1111-111111111111",
  "toLocationId": "11111111-1111-1111-1111-111111111111",
  "requestedById": "11111111-1111-1111-1111-111111111111",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "transferDate": "2026-03-27",
  "status": "REQUESTED",
  "reason": "string"
}
```

Enum values:

- response `status`: `REQUESTED` = Requested., `APPROVED` = Approved., `REJECTED` = Rejected., `IN_TRANSIT` = In transit., `COMPLETED` = Completed., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetTransferController.java`

### POST /api/v1/asset-transfers/{id}/reject

User type: **Authenticated user**

Description: Reject the targeted v1 record.

When to call: Call from an approval or review action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| fromDepartmentId | string (uuid) | yes | required, format `uuid` |
| toDepartmentId | string (uuid) | yes | required, format `uuid` |
| fromLocationId | string (uuid) | no | optional, format `uuid` |
| toLocationId | string (uuid) | no | optional, format `uuid` |
| requestedById | string (uuid) | yes | required, format `uuid` |
| approvedById | string (uuid) | no | optional, format `uuid` |
| transferDate | string (date) | no | optional, format `date` |
| status | enum<string> | no | optional, enum |
| reason | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "fromDepartmentId": "11111111-1111-1111-1111-111111111111",
  "toDepartmentId": "11111111-1111-1111-1111-111111111111",
  "fromLocationId": "11111111-1111-1111-1111-111111111111",
  "toLocationId": "11111111-1111-1111-1111-111111111111",
  "requestedById": "11111111-1111-1111-1111-111111111111",
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "transferDate": "2026-03-27",
  "status": "REQUESTED",
  "reason": "string"
}
```

Enum values:

- response `status`: `REQUESTED` = Requested., `APPROVED` = Approved., `REJECTED` = Rejected., `IN_TRANSIT` = In transit., `COMPLETED` = Completed., `CANCELLED` = Cancelled.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AssetTransferController.java`

## Audit Service

Stage: **Asset Lifecycle**

### GET /api/v1/audits

User type: **Authenticated user or system admin**

Description: List or summarize audits.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| departmentId | query | no | string (uuid) | optional, format `uuid` |
| startDate | query | no | string (date) | optional, format `date` |
| endDate | query | no | string (date) | optional, format `date` |
| conductedById | query | no | string (uuid) | optional, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "departmentId": "11111111-1111-1111-1111-111111111111",
    "auditDate": "2026-03-27",
    "conductedById": "11111111-1111-1111-1111-111111111111",
    "status": "PLANNED",
    "remarks": "string"
  }
]
```

Enum values:

- response `[].status`: `PLANNED` = Planned., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `DISCREPANCY_FOUND` = Discrepancy found., `RESOLVED` = Resolved.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuditController.java`

### POST /api/v1/audits

User type: **Authenticated user**

Description: Create or trigger a new audits operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| auditDate | string (date) | yes | required, format `date` |
| conductedById | string (uuid) | yes | required, format `uuid` |
| status | enum<string> | no | optional, enum |
| remarks | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "auditDate": "2026-03-27",
  "conductedById": "11111111-1111-1111-1111-111111111111",
  "status": "PLANNED",
  "remarks": "string"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| auditDate | string (date) | yes | required, format `date` |
| conductedById | string (uuid) | yes | required, format `uuid` |
| status | enum<string> | no | optional, enum |
| remarks | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "auditDate": "2026-03-27",
  "conductedById": "11111111-1111-1111-1111-111111111111",
  "status": "PLANNED",
  "remarks": "string"
}
```

Enum values:

- request `status`: `PLANNED` = Planned., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `DISCREPANCY_FOUND` = Discrepancy found., `RESOLVED` = Resolved.
- response `status`: `PLANNED` = Planned., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `DISCREPANCY_FOUND` = Discrepancy found., `RESOLVED` = Resolved.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `departmentId` before submitting the payload.
- Resolve `conductedById` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuditController.java`

### DELETE /api/v1/audits/{id}

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuditController.java`

### GET /api/v1/audits/{id}

User type: **Authenticated user or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| auditDate | string (date) | yes | required, format `date` |
| conductedById | string (uuid) | yes | required, format `uuid` |
| status | enum<string> | no | optional, enum |
| remarks | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "auditDate": "2026-03-27",
  "conductedById": "11111111-1111-1111-1111-111111111111",
  "status": "PLANNED",
  "remarks": "string"
}
```

Enum values:

- response `status`: `PLANNED` = Planned., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `DISCREPANCY_FOUND` = Discrepancy found., `RESOLVED` = Resolved.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuditController.java`

### PATCH /api/v1/audits/{id}/status

User type: **Authenticated user**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |
| status | query | yes | string | required |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | yes | required, format `uuid` |
| departmentId | string (uuid) | yes | required, format `uuid` |
| auditDate | string (date) | yes | required, format `date` |
| conductedById | string (uuid) | yes | required, format `uuid` |
| status | enum<string> | no | optional, enum |
| remarks | string | no | optional |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "departmentId": "11111111-1111-1111-1111-111111111111",
  "auditDate": "2026-03-27",
  "conductedById": "11111111-1111-1111-1111-111111111111",
  "status": "PLANNED",
  "remarks": "string"
}
```

Enum values:

- response `status`: `PLANNED` = Planned., `IN_PROGRESS` = In progress., `COMPLETED` = Completed., `DISCREPANCY_FOUND` = Discrepancy found., `RESOLVED` = Resolved.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuditController.java`

## Audit Event Service

Stage: **Asset Lifecycle**

### GET /api/v1/audit-events

User type: **Authenticated user**

Description: List or summarize audit events.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| actorId | query | no | string (uuid) | optional, format `uuid` |
| start | query | no | string (date-time) | optional, format `date-time` |
| end | query | no | string (date-time) | optional, format `date-time` |
| success | query | no | boolean | optional |
| method | query | no | string | optional |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "actorId": "11111111-1111-1111-1111-111111111111",
    "actorEmail": "jane.admin@example.com",
    "method": "string",
    "path": "string",
    "query": "string",
    "handler": "string",
    "responseStatus": "string",
    "success": true,
    "message": "string",
    "requestId": "11111111-1111-1111-1111-111111111111",
    "clientIp": "string",
    "userAgent": "string",
    "createdAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuditEventController.java`

### GET /api/v1/audit-events/{id}

User type: **Authenticated user**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| actorId | string (uuid) | no | optional, format `uuid` |
| actorEmail | string | no | optional |
| method | string | no | optional |
| path | string | no | optional |
| query | string | no | optional |
| handler | string | no | optional |
| responseStatus | integer (int32) | no | optional, format `int32` |
| success | boolean | no | optional |
| message | string | no | optional |
| requestId | string | no | optional |
| clientIp | string | no | optional |
| userAgent | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "actorId": "11111111-1111-1111-1111-111111111111",
  "actorEmail": "jane.admin@example.com",
  "method": "string",
  "path": "string",
  "query": "string",
  "handler": "string",
  "responseStatus": "string",
  "success": true,
  "message": "string",
  "requestId": "11111111-1111-1111-1111-111111111111",
  "clientIp": "string",
  "userAgent": "string",
  "createdAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AuditEventController.java`

## Disposal Service

Stage: **Asset Lifecycle**

### GET /api/v1/disposals

User type: **Authenticated user or system admin**

Description: List or summarize disposals.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| assetId | query | no | string (uuid) | optional, format `uuid` |
| startDate | query | no | string (date) | optional, format `date` |
| endDate | query | no | string (date) | optional, format `date` |
| approvedById | query | no | string (uuid) | optional, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "assetId": "11111111-1111-1111-1111-111111111111",
    "disposalMethod": "SALE",
    "disposalDate": "2026-03-27",
    "saleValue": 1000.0,
    "approvedById": "11111111-1111-1111-1111-111111111111",
    "reason": "string",
    "complianceDocumentUrl": "https://example.com/resource",
    "organisationId": "11111111-1111-1111-1111-111111111111"
  }
]
```

Enum values:

- response `[].disposalMethod`: `SALE` = Sale., `DONATION` = Donation., `SCRAP` = Scrap., `RECYCLING` = Recycling., `TRADE_IN` = Trade in., `RETURN` = Return.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/DisposalController.java`

### POST /api/v1/disposals

User type: **Authenticated user**

Description: Create or trigger a new disposals operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| disposalMethod | enum<string> | yes | required, enum |
| disposalDate | string (date) | yes | required, format `date` |
| saleValue | number | no | optional |
| approvedById | string (uuid) | yes | required, format `uuid` |
| reason | string | no | optional |
| complianceDocumentUrl | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "disposalMethod": "SALE",
  "disposalDate": "2026-03-27",
  "saleValue": 1000.0,
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "reason": "string",
  "complianceDocumentUrl": "https://example.com/resource",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| disposalMethod | enum<string> | yes | required, enum |
| disposalDate | string (date) | yes | required, format `date` |
| saleValue | number | no | optional |
| approvedById | string (uuid) | yes | required, format `uuid` |
| reason | string | no | optional |
| complianceDocumentUrl | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "disposalMethod": "SALE",
  "disposalDate": "2026-03-27",
  "saleValue": 1000.0,
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "reason": "string",
  "complianceDocumentUrl": "https://example.com/resource",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `disposalMethod`: `SALE` = Sale., `DONATION` = Donation., `SCRAP` = Scrap., `RECYCLING` = Recycling., `TRADE_IN` = Trade in., `RETURN` = Return.
- response `disposalMethod`: `SALE` = Sale., `DONATION` = Donation., `SCRAP` = Scrap., `RECYCLING` = Recycling., `TRADE_IN` = Trade in., `RETURN` = Return.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `assetId` before submitting the payload.
- Resolve `approvedById` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/DisposalController.java`

### DELETE /api/v1/disposals/{id}

User type: **Authenticated user**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/DisposalController.java`

### GET /api/v1/disposals/{id}

User type: **Authenticated user or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| disposalMethod | enum<string> | yes | required, enum |
| disposalDate | string (date) | yes | required, format `date` |
| saleValue | number | no | optional |
| approvedById | string (uuid) | yes | required, format `uuid` |
| reason | string | no | optional |
| complianceDocumentUrl | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "disposalMethod": "SALE",
  "disposalDate": "2026-03-27",
  "saleValue": 1000.0,
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "reason": "string",
  "complianceDocumentUrl": "https://example.com/resource",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- response `disposalMethod`: `SALE` = Sale., `DONATION` = Donation., `SCRAP` = Scrap., `RECYCLING` = Recycling., `TRADE_IN` = Trade in., `RETURN` = Return.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/DisposalController.java`

### PATCH /api/v1/disposals/{id}

User type: **Authenticated user**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| disposalMethod | enum<string> | yes | required, enum |
| disposalDate | string (date) | yes | required, format `date` |
| saleValue | number | no | optional |
| approvedById | string (uuid) | yes | required, format `uuid` |
| reason | string | no | optional |
| complianceDocumentUrl | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "disposalMethod": "SALE",
  "disposalDate": "2026-03-27",
  "saleValue": 1000.0,
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "reason": "string",
  "complianceDocumentUrl": "https://example.com/resource",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| disposalMethod | enum<string> | yes | required, enum |
| disposalDate | string (date) | yes | required, format `date` |
| saleValue | number | no | optional |
| approvedById | string (uuid) | yes | required, format `uuid` |
| reason | string | no | optional |
| complianceDocumentUrl | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "disposalMethod": "SALE",
  "disposalDate": "2026-03-27",
  "saleValue": 1000.0,
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "reason": "string",
  "complianceDocumentUrl": "https://example.com/resource",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `disposalMethod`: `SALE` = Sale., `DONATION` = Donation., `SCRAP` = Scrap., `RECYCLING` = Recycling., `TRADE_IN` = Trade in., `RETURN` = Return.
- response `disposalMethod`: `SALE` = Sale., `DONATION` = Donation., `SCRAP` = Scrap., `RECYCLING` = Recycling., `TRADE_IN` = Trade in., `RETURN` = Return.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `assetId` before submitting the payload.
- Resolve `approvedById` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/DisposalController.java`

### PUT /api/v1/disposals/{id}

User type: **Authenticated user**

Description: Replace the existing v1 record with the supplied payload.

When to call: Call from full-edit forms.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| disposalMethod | enum<string> | yes | required, enum |
| disposalDate | string (date) | yes | required, format `date` |
| saleValue | number | no | optional |
| approvedById | string (uuid) | yes | required, format `uuid` |
| reason | string | no | optional |
| complianceDocumentUrl | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "disposalMethod": "SALE",
  "disposalDate": "2026-03-27",
  "saleValue": 1000.0,
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "reason": "string",
  "complianceDocumentUrl": "https://example.com/resource",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| disposalMethod | enum<string> | yes | required, enum |
| disposalDate | string (date) | yes | required, format `date` |
| saleValue | number | no | optional |
| approvedById | string (uuid) | yes | required, format `uuid` |
| reason | string | no | optional |
| complianceDocumentUrl | string | no | optional |
| organisationId | string (uuid) | no | optional, format `uuid` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "disposalMethod": "SALE",
  "disposalDate": "2026-03-27",
  "saleValue": 1000.0,
  "approvedById": "11111111-1111-1111-1111-111111111111",
  "reason": "string",
  "complianceDocumentUrl": "https://example.com/resource",
  "organisationId": "11111111-1111-1111-1111-111111111111"
}
```

Enum values:

- request `disposalMethod`: `SALE` = Sale., `DONATION` = Donation., `SCRAP` = Scrap., `RECYCLING` = Recycling., `TRADE_IN` = Trade in., `RETURN` = Return.
- response `disposalMethod`: `SALE` = Sale., `DONATION` = Donation., `SCRAP` = Scrap., `RECYCLING` = Recycling., `TRADE_IN` = Trade in., `RETURN` = Return.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `assetId` before submitting the payload.
- Resolve `approvedById` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/DisposalController.java`

## Compliance Service

Stage: **Governance**

### GET /api/v1/compliance/bog-controls

User type: **Org admin or system admin**

Description: List or summarize bog controls.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| status | query | no | string | optional |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "directiveRef": "string",
    "requirement": "string",
    "status": "NOT_IMPLEMENTED",
    "evidenceUrl": "https://example.com/resource",
    "gapDescription": "string",
    "remediationPlan": "string",
    "targetDate": "2026-03-27T10:15:30Z",
    "ownerId": "11111111-1111-1111-1111-111111111111",
    "ownerEmail": "jane.admin@example.com",
    "createdAt": "2026-03-27T10:15:30Z",
    "updatedAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- response `[].status`: `NOT_IMPLEMENTED` = Not implemented., `PARTIAL` = Partial., `IMPLEMENTED` = Implemented., `NOT_APPLICABLE` = Not applicable.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### POST /api/v1/compliance/bog-controls

User type: **Org admin or system admin**

Description: Create or trigger a new bog controls operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| directiveRef | string | yes | required |
| requirement | string | yes | required |
| status | enum<string> | no | optional, enum |
| evidenceUrl | string | no | optional |
| gapDescription | string | no | optional |
| remediationPlan | string | no | optional |
| targetDate | string (date-time) | no | optional, format `date-time` |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "directiveRef": "string",
  "requirement": "string",
  "status": "NOT_IMPLEMENTED",
  "evidenceUrl": "https://example.com/resource",
  "gapDescription": "string",
  "remediationPlan": "string",
  "targetDate": "2026-03-27T10:15:30Z",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| directiveRef | string | yes | required |
| requirement | string | yes | required |
| status | enum<string> | no | optional, enum |
| evidenceUrl | string | no | optional |
| gapDescription | string | no | optional |
| remediationPlan | string | no | optional |
| targetDate | string (date-time) | no | optional, format `date-time` |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "directiveRef": "string",
  "requirement": "string",
  "status": "NOT_IMPLEMENTED",
  "evidenceUrl": "https://example.com/resource",
  "gapDescription": "string",
  "remediationPlan": "string",
  "targetDate": "2026-03-27T10:15:30Z",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `status`: `NOT_IMPLEMENTED` = Not implemented., `PARTIAL` = Partial., `IMPLEMENTED` = Implemented., `NOT_APPLICABLE` = Not applicable.
- response `status`: `NOT_IMPLEMENTED` = Not implemented., `PARTIAL` = Partial., `IMPLEMENTED` = Implemented., `NOT_APPLICABLE` = Not applicable.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `ownerId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### DELETE /api/v1/compliance/bog-controls/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/bog-controls/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| directiveRef | string | yes | required |
| requirement | string | yes | required |
| status | enum<string> | no | optional, enum |
| evidenceUrl | string | no | optional |
| gapDescription | string | no | optional |
| remediationPlan | string | no | optional |
| targetDate | string (date-time) | no | optional, format `date-time` |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "directiveRef": "string",
  "requirement": "string",
  "status": "NOT_IMPLEMENTED",
  "evidenceUrl": "https://example.com/resource",
  "gapDescription": "string",
  "remediationPlan": "string",
  "targetDate": "2026-03-27T10:15:30Z",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `status`: `NOT_IMPLEMENTED` = Not implemented., `PARTIAL` = Partial., `IMPLEMENTED` = Implemented., `NOT_APPLICABLE` = Not applicable.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### PATCH /api/v1/compliance/bog-controls/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| directiveRef | string | yes | required |
| requirement | string | yes | required |
| status | enum<string> | no | optional, enum |
| evidenceUrl | string | no | optional |
| gapDescription | string | no | optional |
| remediationPlan | string | no | optional |
| targetDate | string (date-time) | no | optional, format `date-time` |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "directiveRef": "string",
  "requirement": "string",
  "status": "NOT_IMPLEMENTED",
  "evidenceUrl": "https://example.com/resource",
  "gapDescription": "string",
  "remediationPlan": "string",
  "targetDate": "2026-03-27T10:15:30Z",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| directiveRef | string | yes | required |
| requirement | string | yes | required |
| status | enum<string> | no | optional, enum |
| evidenceUrl | string | no | optional |
| gapDescription | string | no | optional |
| remediationPlan | string | no | optional |
| targetDate | string (date-time) | no | optional, format `date-time` |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "directiveRef": "string",
  "requirement": "string",
  "status": "NOT_IMPLEMENTED",
  "evidenceUrl": "https://example.com/resource",
  "gapDescription": "string",
  "remediationPlan": "string",
  "targetDate": "2026-03-27T10:15:30Z",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `status`: `NOT_IMPLEMENTED` = Not implemented., `PARTIAL` = Partial., `IMPLEMENTED` = Implemented., `NOT_APPLICABLE` = Not applicable.
- response `status`: `NOT_IMPLEMENTED` = Not implemented., `PARTIAL` = Partial., `IMPLEMENTED` = Implemented., `NOT_APPLICABLE` = Not applicable.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `ownerId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/controls

User type: **Org admin or system admin**

Description: List or summarize controls.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| framework | query | no | string | optional |
| status | query | no | string | optional |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "framework": "ISO_27001",
    "controlRef": "string",
    "controlName": "string",
    "controlDescription": "string",
    "status": "NOT_IMPLEMENTED",
    "justification": "string",
    "evidenceUrl": "https://example.com/resource",
    "gapDescription": "string",
    "remediationPlan": "string",
    "ownerId": "11111111-1111-1111-1111-111111111111",
    "ownerEmail": "jane.admin@example.com",
    "reviewDueDate": "2026-03-27T10:15:30Z",
    "lastReviewedAt": "2026-03-27T10:15:30Z",
    "lastReviewedByEmail": "jane.admin@example.com",
    "createdAt": "2026-03-27T10:15:30Z",
    "updatedAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- response `[].framework`: `ISO_27001` = Iso 27001., `SOC2` = Soc2., `PCI_DSS` = Pci dss., `ICS` = Ics., `BOG` = Bog.
- response `[].status`: `NOT_IMPLEMENTED` = Not implemented., `PARTIAL` = Partial., `IMPLEMENTED` = Implemented., `NOT_APPLICABLE` = Not applicable.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### POST /api/v1/compliance/controls

User type: **Org admin or system admin**

Description: Create or trigger a new controls operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| framework | enum<string> | yes | required, enum |
| controlRef | string | yes | required |
| controlName | string | yes | required |
| controlDescription | string | no | optional |
| status | enum<string> | no | optional, enum |
| justification | string | no | optional |
| evidenceUrl | string | no | optional |
| gapDescription | string | no | optional |
| remediationPlan | string | no | optional |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| reviewDueDate | string (date-time) | no | optional, format `date-time` |
| lastReviewedAt | string (date-time) | no | optional, format `date-time` |
| lastReviewedByEmail | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "framework": "ISO_27001",
  "controlRef": "string",
  "controlName": "string",
  "controlDescription": "string",
  "status": "NOT_IMPLEMENTED",
  "justification": "string",
  "evidenceUrl": "https://example.com/resource",
  "gapDescription": "string",
  "remediationPlan": "string",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "reviewDueDate": "2026-03-27T10:15:30Z",
  "lastReviewedAt": "2026-03-27T10:15:30Z",
  "lastReviewedByEmail": "jane.admin@example.com",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| framework | enum<string> | yes | required, enum |
| controlRef | string | yes | required |
| controlName | string | yes | required |
| controlDescription | string | no | optional |
| status | enum<string> | no | optional, enum |
| justification | string | no | optional |
| evidenceUrl | string | no | optional |
| gapDescription | string | no | optional |
| remediationPlan | string | no | optional |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| reviewDueDate | string (date-time) | no | optional, format `date-time` |
| lastReviewedAt | string (date-time) | no | optional, format `date-time` |
| lastReviewedByEmail | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "framework": "ISO_27001",
  "controlRef": "string",
  "controlName": "string",
  "controlDescription": "string",
  "status": "NOT_IMPLEMENTED",
  "justification": "string",
  "evidenceUrl": "https://example.com/resource",
  "gapDescription": "string",
  "remediationPlan": "string",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "reviewDueDate": "2026-03-27T10:15:30Z",
  "lastReviewedAt": "2026-03-27T10:15:30Z",
  "lastReviewedByEmail": "jane.admin@example.com",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `framework`: `ISO_27001` = Iso 27001., `SOC2` = Soc2., `PCI_DSS` = Pci dss., `ICS` = Ics., `BOG` = Bog.
- request `status`: `NOT_IMPLEMENTED` = Not implemented., `PARTIAL` = Partial., `IMPLEMENTED` = Implemented., `NOT_APPLICABLE` = Not applicable.
- response `framework`: `ISO_27001` = Iso 27001., `SOC2` = Soc2., `PCI_DSS` = Pci dss., `ICS` = Ics., `BOG` = Bog.
- response `status`: `NOT_IMPLEMENTED` = Not implemented., `PARTIAL` = Partial., `IMPLEMENTED` = Implemented., `NOT_APPLICABLE` = Not applicable.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `ownerId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### DELETE /api/v1/compliance/controls/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/controls/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| framework | enum<string> | yes | required, enum |
| controlRef | string | yes | required |
| controlName | string | yes | required |
| controlDescription | string | no | optional |
| status | enum<string> | no | optional, enum |
| justification | string | no | optional |
| evidenceUrl | string | no | optional |
| gapDescription | string | no | optional |
| remediationPlan | string | no | optional |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| reviewDueDate | string (date-time) | no | optional, format `date-time` |
| lastReviewedAt | string (date-time) | no | optional, format `date-time` |
| lastReviewedByEmail | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "framework": "ISO_27001",
  "controlRef": "string",
  "controlName": "string",
  "controlDescription": "string",
  "status": "NOT_IMPLEMENTED",
  "justification": "string",
  "evidenceUrl": "https://example.com/resource",
  "gapDescription": "string",
  "remediationPlan": "string",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "reviewDueDate": "2026-03-27T10:15:30Z",
  "lastReviewedAt": "2026-03-27T10:15:30Z",
  "lastReviewedByEmail": "jane.admin@example.com",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `framework`: `ISO_27001` = Iso 27001., `SOC2` = Soc2., `PCI_DSS` = Pci dss., `ICS` = Ics., `BOG` = Bog.
- response `status`: `NOT_IMPLEMENTED` = Not implemented., `PARTIAL` = Partial., `IMPLEMENTED` = Implemented., `NOT_APPLICABLE` = Not applicable.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### PATCH /api/v1/compliance/controls/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| framework | enum<string> | yes | required, enum |
| controlRef | string | yes | required |
| controlName | string | yes | required |
| controlDescription | string | no | optional |
| status | enum<string> | no | optional, enum |
| justification | string | no | optional |
| evidenceUrl | string | no | optional |
| gapDescription | string | no | optional |
| remediationPlan | string | no | optional |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| reviewDueDate | string (date-time) | no | optional, format `date-time` |
| lastReviewedAt | string (date-time) | no | optional, format `date-time` |
| lastReviewedByEmail | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "framework": "ISO_27001",
  "controlRef": "string",
  "controlName": "string",
  "controlDescription": "string",
  "status": "NOT_IMPLEMENTED",
  "justification": "string",
  "evidenceUrl": "https://example.com/resource",
  "gapDescription": "string",
  "remediationPlan": "string",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "reviewDueDate": "2026-03-27T10:15:30Z",
  "lastReviewedAt": "2026-03-27T10:15:30Z",
  "lastReviewedByEmail": "jane.admin@example.com",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| framework | enum<string> | yes | required, enum |
| controlRef | string | yes | required |
| controlName | string | yes | required |
| controlDescription | string | no | optional |
| status | enum<string> | no | optional, enum |
| justification | string | no | optional |
| evidenceUrl | string | no | optional |
| gapDescription | string | no | optional |
| remediationPlan | string | no | optional |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| reviewDueDate | string (date-time) | no | optional, format `date-time` |
| lastReviewedAt | string (date-time) | no | optional, format `date-time` |
| lastReviewedByEmail | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "framework": "ISO_27001",
  "controlRef": "string",
  "controlName": "string",
  "controlDescription": "string",
  "status": "NOT_IMPLEMENTED",
  "justification": "string",
  "evidenceUrl": "https://example.com/resource",
  "gapDescription": "string",
  "remediationPlan": "string",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "reviewDueDate": "2026-03-27T10:15:30Z",
  "lastReviewedAt": "2026-03-27T10:15:30Z",
  "lastReviewedByEmail": "jane.admin@example.com",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `framework`: `ISO_27001` = Iso 27001., `SOC2` = Soc2., `PCI_DSS` = Pci dss., `ICS` = Ics., `BOG` = Bog.
- request `status`: `NOT_IMPLEMENTED` = Not implemented., `PARTIAL` = Partial., `IMPLEMENTED` = Implemented., `NOT_APPLICABLE` = Not applicable.
- response `framework`: `ISO_27001` = Iso 27001., `SOC2` = Soc2., `PCI_DSS` = Pci dss., `ICS` = Ics., `BOG` = Bog.
- response `status`: `NOT_IMPLEMENTED` = Not implemented., `PARTIAL` = Partial., `IMPLEMENTED` = Implemented., `NOT_APPLICABLE` = Not applicable.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `ownerId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/ics-assets

User type: **Org admin or system admin**

Description: List or summarize ics assets.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "assetId": "11111111-1111-1111-1111-111111111111",
    "assetName": "string",
    "securityZoneId": "11111111-1111-1111-1111-111111111111",
    "securityZoneName": "string",
    "firmwareVersion": "string",
    "protocol": "string",
    "vendorSupportStatus": "SUPPORTED",
    "lastPatchedAt": "2026-03-27T10:15:30Z",
    "knownVulnerabilities": "string",
    "isolated": true,
    "notes": "string",
    "createdAt": "2026-03-27T10:15:30Z",
    "updatedAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- response `[].vendorSupportStatus`: `SUPPORTED` = Supported., `END_OF_LIFE` = End of life., `END_OF_SUPPORT` = End of support., `UNKNOWN` = Unknown.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### POST /api/v1/compliance/ics-assets

User type: **Org admin or system admin**

Description: Create or trigger a new ics assets operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| assetName | string | no | optional |
| securityZoneId | string (uuid) | no | optional, format `uuid` |
| securityZoneName | string | no | optional |
| firmwareVersion | string | no | optional |
| protocol | string | no | optional |
| vendorSupportStatus | enum<string> | no | optional, enum |
| lastPatchedAt | string (date-time) | no | optional, format `date-time` |
| knownVulnerabilities | string | no | optional |
| isolated | boolean | no | optional |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "securityZoneId": "11111111-1111-1111-1111-111111111111",
  "securityZoneName": "string",
  "firmwareVersion": "string",
  "protocol": "string",
  "vendorSupportStatus": "SUPPORTED",
  "lastPatchedAt": "2026-03-27T10:15:30Z",
  "knownVulnerabilities": "string",
  "isolated": true,
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| assetName | string | no | optional |
| securityZoneId | string (uuid) | no | optional, format `uuid` |
| securityZoneName | string | no | optional |
| firmwareVersion | string | no | optional |
| protocol | string | no | optional |
| vendorSupportStatus | enum<string> | no | optional, enum |
| lastPatchedAt | string (date-time) | no | optional, format `date-time` |
| knownVulnerabilities | string | no | optional |
| isolated | boolean | no | optional |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "securityZoneId": "11111111-1111-1111-1111-111111111111",
  "securityZoneName": "string",
  "firmwareVersion": "string",
  "protocol": "string",
  "vendorSupportStatus": "SUPPORTED",
  "lastPatchedAt": "2026-03-27T10:15:30Z",
  "knownVulnerabilities": "string",
  "isolated": true,
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `vendorSupportStatus`: `SUPPORTED` = Supported., `END_OF_LIFE` = End of life., `END_OF_SUPPORT` = End of support., `UNKNOWN` = Unknown.
- response `vendorSupportStatus`: `SUPPORTED` = Supported., `END_OF_LIFE` = End of life., `END_OF_SUPPORT` = End of support., `UNKNOWN` = Unknown.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `assetId` before submitting the payload.
- Resolve `securityZoneId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### DELETE /api/v1/compliance/ics-assets/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/ics-assets/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| assetName | string | no | optional |
| securityZoneId | string (uuid) | no | optional, format `uuid` |
| securityZoneName | string | no | optional |
| firmwareVersion | string | no | optional |
| protocol | string | no | optional |
| vendorSupportStatus | enum<string> | no | optional, enum |
| lastPatchedAt | string (date-time) | no | optional, format `date-time` |
| knownVulnerabilities | string | no | optional |
| isolated | boolean | no | optional |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "securityZoneId": "11111111-1111-1111-1111-111111111111",
  "securityZoneName": "string",
  "firmwareVersion": "string",
  "protocol": "string",
  "vendorSupportStatus": "SUPPORTED",
  "lastPatchedAt": "2026-03-27T10:15:30Z",
  "knownVulnerabilities": "string",
  "isolated": true,
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `vendorSupportStatus`: `SUPPORTED` = Supported., `END_OF_LIFE` = End of life., `END_OF_SUPPORT` = End of support., `UNKNOWN` = Unknown.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### PATCH /api/v1/compliance/ics-assets/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| assetName | string | no | optional |
| securityZoneId | string (uuid) | no | optional, format `uuid` |
| securityZoneName | string | no | optional |
| firmwareVersion | string | no | optional |
| protocol | string | no | optional |
| vendorSupportStatus | enum<string> | no | optional, enum |
| lastPatchedAt | string (date-time) | no | optional, format `date-time` |
| knownVulnerabilities | string | no | optional |
| isolated | boolean | no | optional |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "securityZoneId": "11111111-1111-1111-1111-111111111111",
  "securityZoneName": "string",
  "firmwareVersion": "string",
  "protocol": "string",
  "vendorSupportStatus": "SUPPORTED",
  "lastPatchedAt": "2026-03-27T10:15:30Z",
  "knownVulnerabilities": "string",
  "isolated": true,
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| assetName | string | no | optional |
| securityZoneId | string (uuid) | no | optional, format `uuid` |
| securityZoneName | string | no | optional |
| firmwareVersion | string | no | optional |
| protocol | string | no | optional |
| vendorSupportStatus | enum<string> | no | optional, enum |
| lastPatchedAt | string (date-time) | no | optional, format `date-time` |
| knownVulnerabilities | string | no | optional |
| isolated | boolean | no | optional |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "securityZoneId": "11111111-1111-1111-1111-111111111111",
  "securityZoneName": "string",
  "firmwareVersion": "string",
  "protocol": "string",
  "vendorSupportStatus": "SUPPORTED",
  "lastPatchedAt": "2026-03-27T10:15:30Z",
  "knownVulnerabilities": "string",
  "isolated": true,
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `vendorSupportStatus`: `SUPPORTED` = Supported., `END_OF_LIFE` = End of life., `END_OF_SUPPORT` = End of support., `UNKNOWN` = Unknown.
- response `vendorSupportStatus`: `SUPPORTED` = Supported., `END_OF_LIFE` = End of life., `END_OF_SUPPORT` = End of support., `UNKNOWN` = Unknown.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `assetId` before submitting the payload.
- Resolve `securityZoneId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/incidents

User type: **Org admin or system admin**

Description: List or summarize incidents.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| limit | query | no | integer (int32) | optional, format `int32` |
| offset | query | no | integer (int64) | optional, format `int64` |
| pageable | query | yes | Pageable | required |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| total | integer (int64) | no | optional, format `int64` |
| limit | integer (int32) | no | optional, format `int32` |
| offset | integer (int64) | no | optional, format `int64` |
| items | array<object> | no | optional |
| items[].id | string (uuid) | no | optional, format `uuid` |
| items[].organisationId | string (uuid) | no | optional, format `uuid` |
| items[].title | string | yes | required |
| items[].description | string | no | optional |
| items[].severity | enum<string> | yes | required, enum |
| items[].category | string | no | optional |
| items[].reportedById | string (uuid) | no | optional, format `uuid` |
| items[].reportedByEmail | string | no | optional |
| items[].assignedToId | string (uuid) | no | optional, format `uuid` |
| items[].assignedToEmail | string | no | optional |
| items[].detectedAt | string (date-time) | no | optional, format `date-time` |
| items[].resolvedAt | string (date-time) | no | optional, format `date-time` |
| items[].rootCause | string | no | optional |
| items[].lessonsLearned | string | no | optional |
| items[].status | enum<string> | no | optional, enum |
| items[].createdAt | string (date-time) | no | optional, format `date-time` |
| items[].updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "total": "string",
  "limit": "string",
  "offset": "string",
  "items": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "organisationId": "11111111-1111-1111-1111-111111111111",
      "title": "string",
      "description": "string",
      "severity": "P1_CRITICAL",
      "category": "string",
      "reportedById": "11111111-1111-1111-1111-111111111111",
      "reportedByEmail": "jane.admin@example.com",
      "assignedToId": "11111111-1111-1111-1111-111111111111",
      "assignedToEmail": "jane.admin@example.com",
      "detectedAt": "2026-03-27T10:15:30Z",
      "resolvedAt": "2026-03-27T10:15:30Z",
      "rootCause": "string",
      "lessonsLearned": "string",
      "status": "OPEN",
      "createdAt": "2026-03-27T10:15:30Z",
      "updatedAt": "2026-03-27T10:15:30Z"
    }
  ]
}
```

Enum values:

- response `items.[].severity`: `P1_CRITICAL` = P1 critical., `P2_HIGH` = P2 high., `P3_MEDIUM` = P3 medium., `P4_LOW` = P4 low.
- response `items.[].status`: `OPEN` = Open., `IN_PROGRESS` = In progress., `RESOLVED` = Resolved., `CLOSED` = Closed.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### POST /api/v1/compliance/incidents

User type: **Org admin or system admin**

Description: Create or trigger a new incidents operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| description | string | no | optional |
| severity | enum<string> | yes | required, enum |
| category | string | no | optional |
| reportedById | string (uuid) | no | optional, format `uuid` |
| reportedByEmail | string | no | optional |
| assignedToId | string (uuid) | no | optional, format `uuid` |
| assignedToEmail | string | no | optional |
| detectedAt | string (date-time) | no | optional, format `date-time` |
| resolvedAt | string (date-time) | no | optional, format `date-time` |
| rootCause | string | no | optional |
| lessonsLearned | string | no | optional |
| status | enum<string> | no | optional, enum |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "description": "string",
  "severity": "P1_CRITICAL",
  "category": "string",
  "reportedById": "11111111-1111-1111-1111-111111111111",
  "reportedByEmail": "jane.admin@example.com",
  "assignedToId": "11111111-1111-1111-1111-111111111111",
  "assignedToEmail": "jane.admin@example.com",
  "detectedAt": "2026-03-27T10:15:30Z",
  "resolvedAt": "2026-03-27T10:15:30Z",
  "rootCause": "string",
  "lessonsLearned": "string",
  "status": "OPEN",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| description | string | no | optional |
| severity | enum<string> | yes | required, enum |
| category | string | no | optional |
| reportedById | string (uuid) | no | optional, format `uuid` |
| reportedByEmail | string | no | optional |
| assignedToId | string (uuid) | no | optional, format `uuid` |
| assignedToEmail | string | no | optional |
| detectedAt | string (date-time) | no | optional, format `date-time` |
| resolvedAt | string (date-time) | no | optional, format `date-time` |
| rootCause | string | no | optional |
| lessonsLearned | string | no | optional |
| status | enum<string> | no | optional, enum |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "description": "string",
  "severity": "P1_CRITICAL",
  "category": "string",
  "reportedById": "11111111-1111-1111-1111-111111111111",
  "reportedByEmail": "jane.admin@example.com",
  "assignedToId": "11111111-1111-1111-1111-111111111111",
  "assignedToEmail": "jane.admin@example.com",
  "detectedAt": "2026-03-27T10:15:30Z",
  "resolvedAt": "2026-03-27T10:15:30Z",
  "rootCause": "string",
  "lessonsLearned": "string",
  "status": "OPEN",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `severity`: `P1_CRITICAL` = P1 critical., `P2_HIGH` = P2 high., `P3_MEDIUM` = P3 medium., `P4_LOW` = P4 low.
- request `status`: `OPEN` = Open., `IN_PROGRESS` = In progress., `RESOLVED` = Resolved., `CLOSED` = Closed.
- response `severity`: `P1_CRITICAL` = P1 critical., `P2_HIGH` = P2 high., `P3_MEDIUM` = P3 medium., `P4_LOW` = P4 low.
- response `status`: `OPEN` = Open., `IN_PROGRESS` = In progress., `RESOLVED` = Resolved., `CLOSED` = Closed.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `reportedById` before submitting the payload.
- Resolve `assignedToId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### DELETE /api/v1/compliance/incidents/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/incidents/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| description | string | no | optional |
| severity | enum<string> | yes | required, enum |
| category | string | no | optional |
| reportedById | string (uuid) | no | optional, format `uuid` |
| reportedByEmail | string | no | optional |
| assignedToId | string (uuid) | no | optional, format `uuid` |
| assignedToEmail | string | no | optional |
| detectedAt | string (date-time) | no | optional, format `date-time` |
| resolvedAt | string (date-time) | no | optional, format `date-time` |
| rootCause | string | no | optional |
| lessonsLearned | string | no | optional |
| status | enum<string> | no | optional, enum |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "description": "string",
  "severity": "P1_CRITICAL",
  "category": "string",
  "reportedById": "11111111-1111-1111-1111-111111111111",
  "reportedByEmail": "jane.admin@example.com",
  "assignedToId": "11111111-1111-1111-1111-111111111111",
  "assignedToEmail": "jane.admin@example.com",
  "detectedAt": "2026-03-27T10:15:30Z",
  "resolvedAt": "2026-03-27T10:15:30Z",
  "rootCause": "string",
  "lessonsLearned": "string",
  "status": "OPEN",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `severity`: `P1_CRITICAL` = P1 critical., `P2_HIGH` = P2 high., `P3_MEDIUM` = P3 medium., `P4_LOW` = P4 low.
- response `status`: `OPEN` = Open., `IN_PROGRESS` = In progress., `RESOLVED` = Resolved., `CLOSED` = Closed.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### PATCH /api/v1/compliance/incidents/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| description | string | no | optional |
| severity | enum<string> | yes | required, enum |
| category | string | no | optional |
| reportedById | string (uuid) | no | optional, format `uuid` |
| reportedByEmail | string | no | optional |
| assignedToId | string (uuid) | no | optional, format `uuid` |
| assignedToEmail | string | no | optional |
| detectedAt | string (date-time) | no | optional, format `date-time` |
| resolvedAt | string (date-time) | no | optional, format `date-time` |
| rootCause | string | no | optional |
| lessonsLearned | string | no | optional |
| status | enum<string> | no | optional, enum |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "description": "string",
  "severity": "P1_CRITICAL",
  "category": "string",
  "reportedById": "11111111-1111-1111-1111-111111111111",
  "reportedByEmail": "jane.admin@example.com",
  "assignedToId": "11111111-1111-1111-1111-111111111111",
  "assignedToEmail": "jane.admin@example.com",
  "detectedAt": "2026-03-27T10:15:30Z",
  "resolvedAt": "2026-03-27T10:15:30Z",
  "rootCause": "string",
  "lessonsLearned": "string",
  "status": "OPEN",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| description | string | no | optional |
| severity | enum<string> | yes | required, enum |
| category | string | no | optional |
| reportedById | string (uuid) | no | optional, format `uuid` |
| reportedByEmail | string | no | optional |
| assignedToId | string (uuid) | no | optional, format `uuid` |
| assignedToEmail | string | no | optional |
| detectedAt | string (date-time) | no | optional, format `date-time` |
| resolvedAt | string (date-time) | no | optional, format `date-time` |
| rootCause | string | no | optional |
| lessonsLearned | string | no | optional |
| status | enum<string> | no | optional, enum |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "description": "string",
  "severity": "P1_CRITICAL",
  "category": "string",
  "reportedById": "11111111-1111-1111-1111-111111111111",
  "reportedByEmail": "jane.admin@example.com",
  "assignedToId": "11111111-1111-1111-1111-111111111111",
  "assignedToEmail": "jane.admin@example.com",
  "detectedAt": "2026-03-27T10:15:30Z",
  "resolvedAt": "2026-03-27T10:15:30Z",
  "rootCause": "string",
  "lessonsLearned": "string",
  "status": "OPEN",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `severity`: `P1_CRITICAL` = P1 critical., `P2_HIGH` = P2 high., `P3_MEDIUM` = P3 medium., `P4_LOW` = P4 low.
- request `status`: `OPEN` = Open., `IN_PROGRESS` = In progress., `RESOLVED` = Resolved., `CLOSED` = Closed.
- response `severity`: `P1_CRITICAL` = P1 critical., `P2_HIGH` = P2 high., `P3_MEDIUM` = P3 medium., `P4_LOW` = P4 low.
- response `status`: `OPEN` = Open., `IN_PROGRESS` = In progress., `RESOLVED` = Resolved., `CLOSED` = Closed.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `reportedById` before submitting the payload.
- Resolve `assignedToId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/patch-records

User type: **Org admin or system admin**

Description: List or summarize patch records.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| assetId | query | no | string (uuid) | optional, format `uuid` |
| limit | query | no | integer (int32) | optional, format `int32` |
| offset | query | no | integer (int64) | optional, format `int64` |
| pageable | query | yes | Pageable | required |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| total | integer (int64) | no | optional, format `int64` |
| limit | integer (int32) | no | optional, format `int32` |
| offset | integer (int64) | no | optional, format `int64` |
| items | array<object> | no | optional |
| items[].id | string (uuid) | no | optional, format `uuid` |
| items[].organisationId | string (uuid) | no | optional, format `uuid` |
| items[].assetId | string (uuid) | yes | required, format `uuid` |
| items[].assetName | string | no | optional |
| items[].patchName | string | yes | required |
| items[].version | string | no | optional |
| items[].appliedAt | string (date-time) | no | optional, format `date-time` |
| items[].appliedByEmail | string | no | optional |
| items[].testEnvironmentValidated | boolean | no | optional |
| items[].rollbackPlan | string | no | optional |
| items[].status | enum<string> | no | optional, enum |
| items[].notes | string | no | optional |
| items[].createdAt | string (date-time) | no | optional, format `date-time` |
| items[].updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "total": "string",
  "limit": "string",
  "offset": "string",
  "items": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "organisationId": "11111111-1111-1111-1111-111111111111",
      "assetId": "11111111-1111-1111-1111-111111111111",
      "assetName": "string",
      "patchName": "string",
      "version": "string",
      "appliedAt": "2026-03-27T10:15:30Z",
      "appliedByEmail": "jane.admin@example.com",
      "testEnvironmentValidated": true,
      "rollbackPlan": "string",
      "status": "PLANNED",
      "notes": "string",
      "createdAt": "2026-03-27T10:15:30Z",
      "updatedAt": "2026-03-27T10:15:30Z"
    }
  ]
}
```

Enum values:

- response `items.[].status`: `PLANNED` = Planned., `APPLIED` = Applied., `FAILED` = Failed., `ROLLED_BACK` = Rolled back.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### POST /api/v1/compliance/patch-records

User type: **Org admin or system admin**

Description: Create or trigger a new patch records operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| assetName | string | no | optional |
| patchName | string | yes | required |
| version | string | no | optional |
| appliedAt | string (date-time) | no | optional, format `date-time` |
| appliedByEmail | string | no | optional |
| testEnvironmentValidated | boolean | no | optional |
| rollbackPlan | string | no | optional |
| status | enum<string> | no | optional, enum |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "patchName": "string",
  "version": "string",
  "appliedAt": "2026-03-27T10:15:30Z",
  "appliedByEmail": "jane.admin@example.com",
  "testEnvironmentValidated": true,
  "rollbackPlan": "string",
  "status": "PLANNED",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| assetName | string | no | optional |
| patchName | string | yes | required |
| version | string | no | optional |
| appliedAt | string (date-time) | no | optional, format `date-time` |
| appliedByEmail | string | no | optional |
| testEnvironmentValidated | boolean | no | optional |
| rollbackPlan | string | no | optional |
| status | enum<string> | no | optional, enum |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "patchName": "string",
  "version": "string",
  "appliedAt": "2026-03-27T10:15:30Z",
  "appliedByEmail": "jane.admin@example.com",
  "testEnvironmentValidated": true,
  "rollbackPlan": "string",
  "status": "PLANNED",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `status`: `PLANNED` = Planned., `APPLIED` = Applied., `FAILED` = Failed., `ROLLED_BACK` = Rolled back.
- response `status`: `PLANNED` = Planned., `APPLIED` = Applied., `FAILED` = Failed., `ROLLED_BACK` = Rolled back.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `assetId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### DELETE /api/v1/compliance/patch-records/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/patch-records/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| assetName | string | no | optional |
| patchName | string | yes | required |
| version | string | no | optional |
| appliedAt | string (date-time) | no | optional, format `date-time` |
| appliedByEmail | string | no | optional |
| testEnvironmentValidated | boolean | no | optional |
| rollbackPlan | string | no | optional |
| status | enum<string> | no | optional, enum |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "patchName": "string",
  "version": "string",
  "appliedAt": "2026-03-27T10:15:30Z",
  "appliedByEmail": "jane.admin@example.com",
  "testEnvironmentValidated": true,
  "rollbackPlan": "string",
  "status": "PLANNED",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `status`: `PLANNED` = Planned., `APPLIED` = Applied., `FAILED` = Failed., `ROLLED_BACK` = Rolled back.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### PATCH /api/v1/compliance/patch-records/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| assetName | string | no | optional |
| patchName | string | yes | required |
| version | string | no | optional |
| appliedAt | string (date-time) | no | optional, format `date-time` |
| appliedByEmail | string | no | optional |
| testEnvironmentValidated | boolean | no | optional |
| rollbackPlan | string | no | optional |
| status | enum<string> | no | optional, enum |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "patchName": "string",
  "version": "string",
  "appliedAt": "2026-03-27T10:15:30Z",
  "appliedByEmail": "jane.admin@example.com",
  "testEnvironmentValidated": true,
  "rollbackPlan": "string",
  "status": "PLANNED",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | yes | required, format `uuid` |
| assetName | string | no | optional |
| patchName | string | yes | required |
| version | string | no | optional |
| appliedAt | string (date-time) | no | optional, format `date-time` |
| appliedByEmail | string | no | optional |
| testEnvironmentValidated | boolean | no | optional |
| rollbackPlan | string | no | optional |
| status | enum<string> | no | optional, enum |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "patchName": "string",
  "version": "string",
  "appliedAt": "2026-03-27T10:15:30Z",
  "appliedByEmail": "jane.admin@example.com",
  "testEnvironmentValidated": true,
  "rollbackPlan": "string",
  "status": "PLANNED",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `status`: `PLANNED` = Planned., `APPLIED` = Applied., `FAILED` = Failed., `ROLLED_BACK` = Rolled back.
- response `status`: `PLANNED` = Planned., `APPLIED` = Applied., `FAILED` = Failed., `ROLLED_BACK` = Rolled back.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `assetId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/pci-saq

User type: **Org admin or system admin**

Description: List or summarize pci saq.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "requirementNumber": "string",
    "requirementText": "string",
    "complianceStatus": "YES",
    "compensatingControl": "string",
    "evidenceUrl": "https://example.com/resource",
    "targetDate": "2026-03-27T10:15:30Z",
    "notes": "string",
    "createdAt": "2026-03-27T10:15:30Z",
    "updatedAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- response `[].complianceStatus`: `YES` = Yes., `NO` = No., `NOT_APPLICABLE` = Not applicable., `COMPENSATING_CONTROL` = Compensating control.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### POST /api/v1/compliance/pci-saq

User type: **Org admin or system admin**

Description: Create or trigger a new pci saq operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| requirementNumber | string | yes | required |
| requirementText | string | no | optional |
| complianceStatus | enum<string> | no | optional, enum |
| compensatingControl | string | no | optional |
| evidenceUrl | string | no | optional |
| targetDate | string (date-time) | no | optional, format `date-time` |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "requirementNumber": "string",
  "requirementText": "string",
  "complianceStatus": "YES",
  "compensatingControl": "string",
  "evidenceUrl": "https://example.com/resource",
  "targetDate": "2026-03-27T10:15:30Z",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| requirementNumber | string | yes | required |
| requirementText | string | no | optional |
| complianceStatus | enum<string> | no | optional, enum |
| compensatingControl | string | no | optional |
| evidenceUrl | string | no | optional |
| targetDate | string (date-time) | no | optional, format `date-time` |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "requirementNumber": "string",
  "requirementText": "string",
  "complianceStatus": "YES",
  "compensatingControl": "string",
  "evidenceUrl": "https://example.com/resource",
  "targetDate": "2026-03-27T10:15:30Z",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `complianceStatus`: `YES` = Yes., `NO` = No., `NOT_APPLICABLE` = Not applicable., `COMPENSATING_CONTROL` = Compensating control.
- response `complianceStatus`: `YES` = Yes., `NO` = No., `NOT_APPLICABLE` = Not applicable., `COMPENSATING_CONTROL` = Compensating control.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/pci-saq/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| requirementNumber | string | yes | required |
| requirementText | string | no | optional |
| complianceStatus | enum<string> | no | optional, enum |
| compensatingControl | string | no | optional |
| evidenceUrl | string | no | optional |
| targetDate | string (date-time) | no | optional, format `date-time` |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "requirementNumber": "string",
  "requirementText": "string",
  "complianceStatus": "YES",
  "compensatingControl": "string",
  "evidenceUrl": "https://example.com/resource",
  "targetDate": "2026-03-27T10:15:30Z",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `complianceStatus`: `YES` = Yes., `NO` = No., `NOT_APPLICABLE` = Not applicable., `COMPENSATING_CONTROL` = Compensating control.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### PATCH /api/v1/compliance/pci-saq/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| requirementNumber | string | yes | required |
| requirementText | string | no | optional |
| complianceStatus | enum<string> | no | optional, enum |
| compensatingControl | string | no | optional |
| evidenceUrl | string | no | optional |
| targetDate | string (date-time) | no | optional, format `date-time` |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "requirementNumber": "string",
  "requirementText": "string",
  "complianceStatus": "YES",
  "compensatingControl": "string",
  "evidenceUrl": "https://example.com/resource",
  "targetDate": "2026-03-27T10:15:30Z",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| requirementNumber | string | yes | required |
| requirementText | string | no | optional |
| complianceStatus | enum<string> | no | optional, enum |
| compensatingControl | string | no | optional |
| evidenceUrl | string | no | optional |
| targetDate | string (date-time) | no | optional, format `date-time` |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "requirementNumber": "string",
  "requirementText": "string",
  "complianceStatus": "YES",
  "compensatingControl": "string",
  "evidenceUrl": "https://example.com/resource",
  "targetDate": "2026-03-27T10:15:30Z",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `complianceStatus`: `YES` = Yes., `NO` = No., `NOT_APPLICABLE` = Not applicable., `COMPENSATING_CONTROL` = Compensating control.
- response `complianceStatus`: `YES` = Yes., `NO` = No., `NOT_APPLICABLE` = Not applicable., `COMPENSATING_CONTROL` = Compensating control.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/policies

User type: **Org admin or system admin**

Description: List or summarize policies.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "title": "string",
    "version": "string",
    "documentUrl": "https://example.com/resource",
    "ownerId": "11111111-1111-1111-1111-111111111111",
    "ownerEmail": "jane.admin@example.com",
    "approvedByEmail": "jane.admin@example.com",
    "effectiveDate": "2026-03-27T10:15:30Z",
    "reviewDueDate": "2026-03-27T10:15:30Z",
    "status": "DRAFT",
    "createdAt": "2026-03-27T10:15:30Z",
    "updatedAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- response `[].status`: `DRAFT` = Draft., `UNDER_REVIEW` = Under review., `APPROVED` = Approved., `RETIRED` = Retired.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### POST /api/v1/compliance/policies

User type: **Org admin or system admin**

Description: Create or trigger a new policies operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| version | string | no | optional |
| documentUrl | string | no | optional |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| approvedByEmail | string | no | optional |
| effectiveDate | string (date-time) | no | optional, format `date-time` |
| reviewDueDate | string (date-time) | no | optional, format `date-time` |
| status | enum<string> | no | optional, enum |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "version": "string",
  "documentUrl": "https://example.com/resource",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "approvedByEmail": "jane.admin@example.com",
  "effectiveDate": "2026-03-27T10:15:30Z",
  "reviewDueDate": "2026-03-27T10:15:30Z",
  "status": "DRAFT",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| version | string | no | optional |
| documentUrl | string | no | optional |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| approvedByEmail | string | no | optional |
| effectiveDate | string (date-time) | no | optional, format `date-time` |
| reviewDueDate | string (date-time) | no | optional, format `date-time` |
| status | enum<string> | no | optional, enum |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "version": "string",
  "documentUrl": "https://example.com/resource",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "approvedByEmail": "jane.admin@example.com",
  "effectiveDate": "2026-03-27T10:15:30Z",
  "reviewDueDate": "2026-03-27T10:15:30Z",
  "status": "DRAFT",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `status`: `DRAFT` = Draft., `UNDER_REVIEW` = Under review., `APPROVED` = Approved., `RETIRED` = Retired.
- response `status`: `DRAFT` = Draft., `UNDER_REVIEW` = Under review., `APPROVED` = Approved., `RETIRED` = Retired.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `ownerId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### DELETE /api/v1/compliance/policies/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/policies/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| version | string | no | optional |
| documentUrl | string | no | optional |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| approvedByEmail | string | no | optional |
| effectiveDate | string (date-time) | no | optional, format `date-time` |
| reviewDueDate | string (date-time) | no | optional, format `date-time` |
| status | enum<string> | no | optional, enum |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "version": "string",
  "documentUrl": "https://example.com/resource",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "approvedByEmail": "jane.admin@example.com",
  "effectiveDate": "2026-03-27T10:15:30Z",
  "reviewDueDate": "2026-03-27T10:15:30Z",
  "status": "DRAFT",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `status`: `DRAFT` = Draft., `UNDER_REVIEW` = Under review., `APPROVED` = Approved., `RETIRED` = Retired.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### PATCH /api/v1/compliance/policies/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| version | string | no | optional |
| documentUrl | string | no | optional |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| approvedByEmail | string | no | optional |
| effectiveDate | string (date-time) | no | optional, format `date-time` |
| reviewDueDate | string (date-time) | no | optional, format `date-time` |
| status | enum<string> | no | optional, enum |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "version": "string",
  "documentUrl": "https://example.com/resource",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "approvedByEmail": "jane.admin@example.com",
  "effectiveDate": "2026-03-27T10:15:30Z",
  "reviewDueDate": "2026-03-27T10:15:30Z",
  "status": "DRAFT",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| title | string | yes | required |
| version | string | no | optional |
| documentUrl | string | no | optional |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| approvedByEmail | string | no | optional |
| effectiveDate | string (date-time) | no | optional, format `date-time` |
| reviewDueDate | string (date-time) | no | optional, format `date-time` |
| status | enum<string> | no | optional, enum |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "version": "string",
  "documentUrl": "https://example.com/resource",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "approvedByEmail": "jane.admin@example.com",
  "effectiveDate": "2026-03-27T10:15:30Z",
  "reviewDueDate": "2026-03-27T10:15:30Z",
  "status": "DRAFT",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `status`: `DRAFT` = Draft., `UNDER_REVIEW` = Under review., `APPROVED` = Approved., `RETIRED` = Retired.
- response `status`: `DRAFT` = Draft., `UNDER_REVIEW` = Under review., `APPROVED` = Approved., `RETIRED` = Retired.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `ownerId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/regulatory-filings

User type: **Org admin or system admin**

Description: List or summarize regulatory filings.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| status | query | no | string | optional |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "filingType": "string",
    "regulator": "string",
    "dueDate": "2026-03-27T10:15:30Z",
    "submittedAt": "2026-03-27T10:15:30Z",
    "reference": "string",
    "status": "PENDING",
    "notes": "string",
    "createdAt": "2026-03-27T10:15:30Z",
    "updatedAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- response `[].status`: `PENDING` = Pending., `SUBMITTED` = Submitted., `OVERDUE` = Overdue., `ACKNOWLEDGED` = Acknowledged., `REJECTED` = Rejected.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### POST /api/v1/compliance/regulatory-filings

User type: **Org admin or system admin**

Description: Create or trigger a new regulatory filings operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| filingType | string | yes | required |
| regulator | string | yes | required |
| dueDate | string (date-time) | yes | required, format `date-time` |
| submittedAt | string (date-time) | no | optional, format `date-time` |
| reference | string | no | optional |
| status | enum<string> | no | optional, enum |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "filingType": "string",
  "regulator": "string",
  "dueDate": "2026-03-27T10:15:30Z",
  "submittedAt": "2026-03-27T10:15:30Z",
  "reference": "string",
  "status": "PENDING",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| filingType | string | yes | required |
| regulator | string | yes | required |
| dueDate | string (date-time) | yes | required, format `date-time` |
| submittedAt | string (date-time) | no | optional, format `date-time` |
| reference | string | no | optional |
| status | enum<string> | no | optional, enum |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "filingType": "string",
  "regulator": "string",
  "dueDate": "2026-03-27T10:15:30Z",
  "submittedAt": "2026-03-27T10:15:30Z",
  "reference": "string",
  "status": "PENDING",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `status`: `PENDING` = Pending., `SUBMITTED` = Submitted., `OVERDUE` = Overdue., `ACKNOWLEDGED` = Acknowledged., `REJECTED` = Rejected.
- response `status`: `PENDING` = Pending., `SUBMITTED` = Submitted., `OVERDUE` = Overdue., `ACKNOWLEDGED` = Acknowledged., `REJECTED` = Rejected.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### DELETE /api/v1/compliance/regulatory-filings/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/regulatory-filings/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| filingType | string | yes | required |
| regulator | string | yes | required |
| dueDate | string (date-time) | yes | required, format `date-time` |
| submittedAt | string (date-time) | no | optional, format `date-time` |
| reference | string | no | optional |
| status | enum<string> | no | optional, enum |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "filingType": "string",
  "regulator": "string",
  "dueDate": "2026-03-27T10:15:30Z",
  "submittedAt": "2026-03-27T10:15:30Z",
  "reference": "string",
  "status": "PENDING",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `status`: `PENDING` = Pending., `SUBMITTED` = Submitted., `OVERDUE` = Overdue., `ACKNOWLEDGED` = Acknowledged., `REJECTED` = Rejected.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### PATCH /api/v1/compliance/regulatory-filings/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| filingType | string | yes | required |
| regulator | string | yes | required |
| dueDate | string (date-time) | yes | required, format `date-time` |
| submittedAt | string (date-time) | no | optional, format `date-time` |
| reference | string | no | optional |
| status | enum<string> | no | optional, enum |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "filingType": "string",
  "regulator": "string",
  "dueDate": "2026-03-27T10:15:30Z",
  "submittedAt": "2026-03-27T10:15:30Z",
  "reference": "string",
  "status": "PENDING",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| filingType | string | yes | required |
| regulator | string | yes | required |
| dueDate | string (date-time) | yes | required, format `date-time` |
| submittedAt | string (date-time) | no | optional, format `date-time` |
| reference | string | no | optional |
| status | enum<string> | no | optional, enum |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "filingType": "string",
  "regulator": "string",
  "dueDate": "2026-03-27T10:15:30Z",
  "submittedAt": "2026-03-27T10:15:30Z",
  "reference": "string",
  "status": "PENDING",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `status`: `PENDING` = Pending., `SUBMITTED` = Submitted., `OVERDUE` = Overdue., `ACKNOWLEDGED` = Acknowledged., `REJECTED` = Rejected.
- response `status`: `PENDING` = Pending., `SUBMITTED` = Submitted., `OVERDUE` = Overdue., `ACKNOWLEDGED` = Acknowledged., `REJECTED` = Rejected.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/risks

User type: **Org admin or system admin**

Description: List or summarize risks.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| status | query | no | string | optional |
| limit | query | no | integer (int32) | optional, format `int32` |
| offset | query | no | integer (int64) | optional, format `int64` |
| pageable | query | yes | Pageable | required |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| total | integer (int64) | no | optional, format `int64` |
| limit | integer (int32) | no | optional, format `int32` |
| offset | integer (int64) | no | optional, format `int64` |
| items | array<object> | no | optional |
| items[].id | string (uuid) | no | optional, format `uuid` |
| items[].organisationId | string (uuid) | no | optional, format `uuid` |
| items[].framework | enum<string> | no | optional, enum |
| items[].riskId | string | no | optional |
| items[].title | string | yes | required |
| items[].description | string | no | optional |
| items[].likelihood | integer (int32) | yes | required, min 1, max 5, format `int32` |
| items[].impact | integer (int32) | yes | required, min 1, max 5, format `int32` |
| items[].riskScore | integer (int32) | no | optional, format `int32` |
| items[].treatment | enum<string> | no | optional, enum |
| items[].mitigationPlan | string | no | optional |
| items[].residualRisk | integer (int32) | no | optional, format `int32` |
| items[].status | enum<string> | no | optional, enum |
| items[].ownerId | string (uuid) | no | optional, format `uuid` |
| items[].ownerEmail | string | no | optional |
| items[].reviewDate | string (date-time) | no | optional, format `date-time` |
| items[].createdAt | string (date-time) | no | optional, format `date-time` |
| items[].updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "total": "string",
  "limit": "string",
  "offset": "string",
  "items": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "organisationId": "11111111-1111-1111-1111-111111111111",
      "framework": "ISO_27001",
      "riskId": "11111111-1111-1111-1111-111111111111",
      "title": "string",
      "description": "string",
      "likelihood": "string",
      "impact": "string",
      "riskScore": "string",
      "treatment": "ACCEPT",
      "mitigationPlan": "string",
      "residualRisk": "string",
      "status": "OPEN",
      "ownerId": "11111111-1111-1111-1111-111111111111",
      "ownerEmail": "jane.admin@example.com",
      "reviewDate": "2026-03-27T10:15:30Z",
      "createdAt": "2026-03-27T10:15:30Z",
      "updatedAt": "2026-03-27T10:15:30Z"
    }
  ]
}
```

Enum values:

- response `items.[].framework`: `ISO_27001` = Iso 27001., `SOC2` = Soc2., `PCI_DSS` = Pci dss., `ICS` = Ics., `BOG` = Bog.
- response `items.[].treatment`: `ACCEPT` = Accept., `MITIGATE` = Mitigate., `TRANSFER` = Transfer., `AVOID` = Avoid.
- response `items.[].status`: `OPEN` = Open., `IN_TREATMENT` = In treatment., `CLOSED` = Closed., `ACCEPTED` = Accepted.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### POST /api/v1/compliance/risks

User type: **Org admin or system admin**

Description: Create or trigger a new risks operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| framework | enum<string> | no | optional, enum |
| riskId | string | no | optional |
| title | string | yes | required |
| description | string | no | optional |
| likelihood | integer (int32) | yes | required, min 1, max 5, format `int32` |
| impact | integer (int32) | yes | required, min 1, max 5, format `int32` |
| riskScore | integer (int32) | no | optional, format `int32` |
| treatment | enum<string> | no | optional, enum |
| mitigationPlan | string | no | optional |
| residualRisk | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| reviewDate | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "framework": "ISO_27001",
  "riskId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "description": "string",
  "likelihood": "string",
  "impact": "string",
  "riskScore": "string",
  "treatment": "ACCEPT",
  "mitigationPlan": "string",
  "residualRisk": "string",
  "status": "OPEN",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "reviewDate": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| framework | enum<string> | no | optional, enum |
| riskId | string | no | optional |
| title | string | yes | required |
| description | string | no | optional |
| likelihood | integer (int32) | yes | required, min 1, max 5, format `int32` |
| impact | integer (int32) | yes | required, min 1, max 5, format `int32` |
| riskScore | integer (int32) | no | optional, format `int32` |
| treatment | enum<string> | no | optional, enum |
| mitigationPlan | string | no | optional |
| residualRisk | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| reviewDate | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "framework": "ISO_27001",
  "riskId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "description": "string",
  "likelihood": "string",
  "impact": "string",
  "riskScore": "string",
  "treatment": "ACCEPT",
  "mitigationPlan": "string",
  "residualRisk": "string",
  "status": "OPEN",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "reviewDate": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `framework`: `ISO_27001` = Iso 27001., `SOC2` = Soc2., `PCI_DSS` = Pci dss., `ICS` = Ics., `BOG` = Bog.
- request `treatment`: `ACCEPT` = Accept., `MITIGATE` = Mitigate., `TRANSFER` = Transfer., `AVOID` = Avoid.
- request `status`: `OPEN` = Open., `IN_TREATMENT` = In treatment., `CLOSED` = Closed., `ACCEPTED` = Accepted.
- response `framework`: `ISO_27001` = Iso 27001., `SOC2` = Soc2., `PCI_DSS` = Pci dss., `ICS` = Ics., `BOG` = Bog.
- response `treatment`: `ACCEPT` = Accept., `MITIGATE` = Mitigate., `TRANSFER` = Transfer., `AVOID` = Avoid.
- response `status`: `OPEN` = Open., `IN_TREATMENT` = In treatment., `CLOSED` = Closed., `ACCEPTED` = Accepted.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `riskId` before submitting the payload.
- Resolve `ownerId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### DELETE /api/v1/compliance/risks/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/risks/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| framework | enum<string> | no | optional, enum |
| riskId | string | no | optional |
| title | string | yes | required |
| description | string | no | optional |
| likelihood | integer (int32) | yes | required, min 1, max 5, format `int32` |
| impact | integer (int32) | yes | required, min 1, max 5, format `int32` |
| riskScore | integer (int32) | no | optional, format `int32` |
| treatment | enum<string> | no | optional, enum |
| mitigationPlan | string | no | optional |
| residualRisk | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| reviewDate | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "framework": "ISO_27001",
  "riskId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "description": "string",
  "likelihood": "string",
  "impact": "string",
  "riskScore": "string",
  "treatment": "ACCEPT",
  "mitigationPlan": "string",
  "residualRisk": "string",
  "status": "OPEN",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "reviewDate": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `framework`: `ISO_27001` = Iso 27001., `SOC2` = Soc2., `PCI_DSS` = Pci dss., `ICS` = Ics., `BOG` = Bog.
- response `treatment`: `ACCEPT` = Accept., `MITIGATE` = Mitigate., `TRANSFER` = Transfer., `AVOID` = Avoid.
- response `status`: `OPEN` = Open., `IN_TREATMENT` = In treatment., `CLOSED` = Closed., `ACCEPTED` = Accepted.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### PATCH /api/v1/compliance/risks/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| framework | enum<string> | no | optional, enum |
| riskId | string | no | optional |
| title | string | yes | required |
| description | string | no | optional |
| likelihood | integer (int32) | yes | required, min 1, max 5, format `int32` |
| impact | integer (int32) | yes | required, min 1, max 5, format `int32` |
| riskScore | integer (int32) | no | optional, format `int32` |
| treatment | enum<string> | no | optional, enum |
| mitigationPlan | string | no | optional |
| residualRisk | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| reviewDate | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "framework": "ISO_27001",
  "riskId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "description": "string",
  "likelihood": "string",
  "impact": "string",
  "riskScore": "string",
  "treatment": "ACCEPT",
  "mitigationPlan": "string",
  "residualRisk": "string",
  "status": "OPEN",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "reviewDate": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| framework | enum<string> | no | optional, enum |
| riskId | string | no | optional |
| title | string | yes | required |
| description | string | no | optional |
| likelihood | integer (int32) | yes | required, min 1, max 5, format `int32` |
| impact | integer (int32) | yes | required, min 1, max 5, format `int32` |
| riskScore | integer (int32) | no | optional, format `int32` |
| treatment | enum<string> | no | optional, enum |
| mitigationPlan | string | no | optional |
| residualRisk | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| ownerId | string (uuid) | no | optional, format `uuid` |
| ownerEmail | string | no | optional |
| reviewDate | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "framework": "ISO_27001",
  "riskId": "11111111-1111-1111-1111-111111111111",
  "title": "string",
  "description": "string",
  "likelihood": "string",
  "impact": "string",
  "riskScore": "string",
  "treatment": "ACCEPT",
  "mitigationPlan": "string",
  "residualRisk": "string",
  "status": "OPEN",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "ownerEmail": "jane.admin@example.com",
  "reviewDate": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `framework`: `ISO_27001` = Iso 27001., `SOC2` = Soc2., `PCI_DSS` = Pci dss., `ICS` = Ics., `BOG` = Bog.
- request `treatment`: `ACCEPT` = Accept., `MITIGATE` = Mitigate., `TRANSFER` = Transfer., `AVOID` = Avoid.
- request `status`: `OPEN` = Open., `IN_TREATMENT` = In treatment., `CLOSED` = Closed., `ACCEPTED` = Accepted.
- response `framework`: `ISO_27001` = Iso 27001., `SOC2` = Soc2., `PCI_DSS` = Pci dss., `ICS` = Ics., `BOG` = Bog.
- response `treatment`: `ACCEPT` = Accept., `MITIGATE` = Mitigate., `TRANSFER` = Transfer., `AVOID` = Avoid.
- response `status`: `OPEN` = Open., `IN_TREATMENT` = In treatment., `CLOSED` = Closed., `ACCEPTED` = Accepted.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.
- Resolve `riskId` before submitting the payload.
- Resolve `ownerId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/security-zones

User type: **Org admin or system admin**

Description: List or summarize security zones.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "purdueLevel": "string",
    "description": "string",
    "allowedProtocols": "string",
    "assetCount": "string",
    "networkRange": "string",
    "createdAt": "2026-03-27T10:15:30Z",
    "updatedAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### POST /api/v1/compliance/security-zones

User type: **Org admin or system admin**

Description: Create or trigger a new security zones operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| purdueLevel | integer (int32) | yes | required, min 0, max 5, format `int32` |
| description | string | no | optional |
| allowedProtocols | string | no | optional |
| assetCount | integer (int32) | no | optional, format `int32` |
| networkRange | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "purdueLevel": "string",
  "description": "string",
  "allowedProtocols": "string",
  "assetCount": "string",
  "networkRange": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| purdueLevel | integer (int32) | yes | required, min 0, max 5, format `int32` |
| description | string | no | optional |
| allowedProtocols | string | no | optional |
| assetCount | integer (int32) | no | optional, format `int32` |
| networkRange | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "purdueLevel": "string",
  "description": "string",
  "allowedProtocols": "string",
  "assetCount": "string",
  "networkRange": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### DELETE /api/v1/compliance/security-zones/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/security-zones/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| purdueLevel | integer (int32) | yes | required, min 0, max 5, format `int32` |
| description | string | no | optional |
| allowedProtocols | string | no | optional |
| assetCount | integer (int32) | no | optional, format `int32` |
| networkRange | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "purdueLevel": "string",
  "description": "string",
  "allowedProtocols": "string",
  "assetCount": "string",
  "networkRange": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### PATCH /api/v1/compliance/security-zones/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| purdueLevel | integer (int32) | yes | required, min 0, max 5, format `int32` |
| description | string | no | optional |
| allowedProtocols | string | no | optional |
| assetCount | integer (int32) | no | optional, format `int32` |
| networkRange | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "purdueLevel": "string",
  "description": "string",
  "allowedProtocols": "string",
  "assetCount": "string",
  "networkRange": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required |
| purdueLevel | integer (int32) | yes | required, min 0, max 5, format `int32` |
| description | string | no | optional |
| allowedProtocols | string | no | optional |
| assetCount | integer (int32) | no | optional, format `int32` |
| networkRange | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "purdueLevel": "string",
  "description": "string",
  "allowedProtocols": "string",
  "assetCount": "string",
  "networkRange": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/sla-metrics

User type: **Org admin or system admin**

Description: List or summarize sla metrics.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "organisationId": "11111111-1111-1111-1111-111111111111",
    "month": "2026-03",
    "year": "string",
    "uptimePercent": "09:00",
    "plannedDowntimeMinutes": "09:00",
    "unplannedDowntimeMinutes": "09:00",
    "incidentCount": "string",
    "rtoMinutes": "string",
    "rpoMinutes": "string",
    "slaBreached": true,
    "notes": "string",
    "createdAt": "2026-03-27T10:15:30Z",
    "updatedAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### POST /api/v1/compliance/sla-metrics

User type: **Org admin or system admin**

Description: Create or trigger a new sla metrics operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| month | integer (int32) | yes | required, min 1, max 12, format `int32` |
| year | integer (int32) | yes | required, format `int32` |
| uptimePercent | number (double) | yes | required, format `double` |
| plannedDowntimeMinutes | integer (int32) | no | optional, format `int32` |
| unplannedDowntimeMinutes | integer (int32) | no | optional, format `int32` |
| incidentCount | integer (int32) | no | optional, format `int32` |
| rtoMinutes | integer (int32) | no | optional, format `int32` |
| rpoMinutes | integer (int32) | no | optional, format `int32` |
| slaBreached | boolean | no | optional |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "month": "2026-03",
  "year": "string",
  "uptimePercent": "09:00",
  "plannedDowntimeMinutes": "09:00",
  "unplannedDowntimeMinutes": "09:00",
  "incidentCount": "string",
  "rtoMinutes": "string",
  "rpoMinutes": "string",
  "slaBreached": true,
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| month | integer (int32) | yes | required, min 1, max 12, format `int32` |
| year | integer (int32) | yes | required, format `int32` |
| uptimePercent | number (double) | yes | required, format `double` |
| plannedDowntimeMinutes | integer (int32) | no | optional, format `int32` |
| unplannedDowntimeMinutes | integer (int32) | no | optional, format `int32` |
| incidentCount | integer (int32) | no | optional, format `int32` |
| rtoMinutes | integer (int32) | no | optional, format `int32` |
| rpoMinutes | integer (int32) | no | optional, format `int32` |
| slaBreached | boolean | no | optional |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "month": "2026-03",
  "year": "string",
  "uptimePercent": "09:00",
  "plannedDowntimeMinutes": "09:00",
  "unplannedDowntimeMinutes": "09:00",
  "incidentCount": "string",
  "rtoMinutes": "string",
  "rpoMinutes": "string",
  "slaBreached": true,
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/sla-metrics/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| month | integer (int32) | yes | required, min 1, max 12, format `int32` |
| year | integer (int32) | yes | required, format `int32` |
| uptimePercent | number (double) | yes | required, format `double` |
| plannedDowntimeMinutes | integer (int32) | no | optional, format `int32` |
| unplannedDowntimeMinutes | integer (int32) | no | optional, format `int32` |
| incidentCount | integer (int32) | no | optional, format `int32` |
| rtoMinutes | integer (int32) | no | optional, format `int32` |
| rpoMinutes | integer (int32) | no | optional, format `int32` |
| slaBreached | boolean | no | optional |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "month": "2026-03",
  "year": "string",
  "uptimePercent": "09:00",
  "plannedDowntimeMinutes": "09:00",
  "unplannedDowntimeMinutes": "09:00",
  "incidentCount": "string",
  "rtoMinutes": "string",
  "rpoMinutes": "string",
  "slaBreached": true,
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### PATCH /api/v1/compliance/sla-metrics/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| month | integer (int32) | yes | required, min 1, max 12, format `int32` |
| year | integer (int32) | yes | required, format `int32` |
| uptimePercent | number (double) | yes | required, format `double` |
| plannedDowntimeMinutes | integer (int32) | no | optional, format `int32` |
| unplannedDowntimeMinutes | integer (int32) | no | optional, format `int32` |
| incidentCount | integer (int32) | no | optional, format `int32` |
| rtoMinutes | integer (int32) | no | optional, format `int32` |
| rpoMinutes | integer (int32) | no | optional, format `int32` |
| slaBreached | boolean | no | optional |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "month": "2026-03",
  "year": "string",
  "uptimePercent": "09:00",
  "plannedDowntimeMinutes": "09:00",
  "unplannedDowntimeMinutes": "09:00",
  "incidentCount": "string",
  "rtoMinutes": "string",
  "rpoMinutes": "string",
  "slaBreached": true,
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| month | integer (int32) | yes | required, min 1, max 12, format `int32` |
| year | integer (int32) | yes | required, format `int32` |
| uptimePercent | number (double) | yes | required, format `double` |
| plannedDowntimeMinutes | integer (int32) | no | optional, format `int32` |
| unplannedDowntimeMinutes | integer (int32) | no | optional, format `int32` |
| incidentCount | integer (int32) | no | optional, format `int32` |
| rtoMinutes | integer (int32) | no | optional, format `int32` |
| rpoMinutes | integer (int32) | no | optional, format `int32` |
| slaBreached | boolean | no | optional |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "month": "2026-03",
  "year": "string",
  "uptimePercent": "09:00",
  "plannedDowntimeMinutes": "09:00",
  "unplannedDowntimeMinutes": "09:00",
  "incidentCount": "string",
  "rtoMinutes": "string",
  "rpoMinutes": "string",
  "slaBreached": true,
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/vulnerability-scans

User type: **Org admin or system admin**

Description: List or summarize vulnerability scans.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| limit | query | no | integer (int32) | optional, format `int32` |
| offset | query | no | integer (int64) | optional, format `int64` |
| pageable | query | yes | Pageable | required |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| total | integer (int64) | no | optional, format `int64` |
| limit | integer (int32) | no | optional, format `int32` |
| offset | integer (int64) | no | optional, format `int64` |
| items | array<object> | no | optional |
| items[].id | string (uuid) | no | optional, format `uuid` |
| items[].organisationId | string (uuid) | no | optional, format `uuid` |
| items[].scanDate | string (date-time) | yes | required, format `date-time` |
| items[].scannerTool | string | no | optional |
| items[].scanType | enum<string> | yes | required, enum |
| items[].criticalCount | integer (int32) | no | optional, format `int32` |
| items[].highCount | integer (int32) | no | optional, format `int32` |
| items[].mediumCount | integer (int32) | no | optional, format `int32` |
| items[].lowCount | integer (int32) | no | optional, format `int32` |
| items[].status | enum<string> | no | optional, enum |
| items[].reportUrl | string | no | optional |
| items[].nextScanDue | string (date-time) | no | optional, format `date-time` |
| items[].notes | string | no | optional |
| items[].createdAt | string (date-time) | no | optional, format `date-time` |
| items[].updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "total": "string",
  "limit": "string",
  "offset": "string",
  "items": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "organisationId": "11111111-1111-1111-1111-111111111111",
      "scanDate": "2026-03-27T10:15:30Z",
      "scannerTool": "string",
      "scanType": "INTERNAL",
      "criticalCount": "string",
      "highCount": "string",
      "mediumCount": "string",
      "lowCount": "string",
      "status": "PASS",
      "reportUrl": "https://example.com/resource",
      "nextScanDue": "2026-03-27T10:15:30Z",
      "notes": "string",
      "createdAt": "2026-03-27T10:15:30Z",
      "updatedAt": "2026-03-27T10:15:30Z"
    }
  ]
}
```

Enum values:

- response `items.[].scanType`: `INTERNAL` = Internal., `EXTERNAL` = External., `ASV` = Asv., `ICS_OT` = Ics ot.
- response `items.[].status`: `PASS` = Pass., `FAIL` = Fail., `PENDING_REMEDIATION` = Pending remediation.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### POST /api/v1/compliance/vulnerability-scans

User type: **Org admin or system admin**

Description: Create or trigger a new vulnerability scans operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| scanDate | string (date-time) | yes | required, format `date-time` |
| scannerTool | string | no | optional |
| scanType | enum<string> | yes | required, enum |
| criticalCount | integer (int32) | no | optional, format `int32` |
| highCount | integer (int32) | no | optional, format `int32` |
| mediumCount | integer (int32) | no | optional, format `int32` |
| lowCount | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| reportUrl | string | no | optional |
| nextScanDue | string (date-time) | no | optional, format `date-time` |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "scanDate": "2026-03-27T10:15:30Z",
  "scannerTool": "string",
  "scanType": "INTERNAL",
  "criticalCount": "string",
  "highCount": "string",
  "mediumCount": "string",
  "lowCount": "string",
  "status": "PASS",
  "reportUrl": "https://example.com/resource",
  "nextScanDue": "2026-03-27T10:15:30Z",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| scanDate | string (date-time) | yes | required, format `date-time` |
| scannerTool | string | no | optional |
| scanType | enum<string> | yes | required, enum |
| criticalCount | integer (int32) | no | optional, format `int32` |
| highCount | integer (int32) | no | optional, format `int32` |
| mediumCount | integer (int32) | no | optional, format `int32` |
| lowCount | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| reportUrl | string | no | optional |
| nextScanDue | string (date-time) | no | optional, format `date-time` |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "scanDate": "2026-03-27T10:15:30Z",
  "scannerTool": "string",
  "scanType": "INTERNAL",
  "criticalCount": "string",
  "highCount": "string",
  "mediumCount": "string",
  "lowCount": "string",
  "status": "PASS",
  "reportUrl": "https://example.com/resource",
  "nextScanDue": "2026-03-27T10:15:30Z",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `scanType`: `INTERNAL` = Internal., `EXTERNAL` = External., `ASV` = Asv., `ICS_OT` = Ics ot.
- request `status`: `PASS` = Pass., `FAIL` = Fail., `PENDING_REMEDIATION` = Pending remediation.
- response `scanType`: `INTERNAL` = Internal., `EXTERNAL` = External., `ASV` = Asv., `ICS_OT` = Ics ot.
- response `status`: `PASS` = Pass., `FAIL` = Fail., `PENDING_REMEDIATION` = Pending remediation.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### DELETE /api/v1/compliance/vulnerability-scans/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### GET /api/v1/compliance/vulnerability-scans/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| scanDate | string (date-time) | yes | required, format `date-time` |
| scannerTool | string | no | optional |
| scanType | enum<string> | yes | required, enum |
| criticalCount | integer (int32) | no | optional, format `int32` |
| highCount | integer (int32) | no | optional, format `int32` |
| mediumCount | integer (int32) | no | optional, format `int32` |
| lowCount | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| reportUrl | string | no | optional |
| nextScanDue | string (date-time) | no | optional, format `date-time` |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "scanDate": "2026-03-27T10:15:30Z",
  "scannerTool": "string",
  "scanType": "INTERNAL",
  "criticalCount": "string",
  "highCount": "string",
  "mediumCount": "string",
  "lowCount": "string",
  "status": "PASS",
  "reportUrl": "https://example.com/resource",
  "nextScanDue": "2026-03-27T10:15:30Z",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `scanType`: `INTERNAL` = Internal., `EXTERNAL` = External., `ASV` = Asv., `ICS_OT` = Ics ot.
- response `status`: `PASS` = Pass., `FAIL` = Fail., `PENDING_REMEDIATION` = Pending remediation.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

### PATCH /api/v1/compliance/vulnerability-scans/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| scanDate | string (date-time) | yes | required, format `date-time` |
| scannerTool | string | no | optional |
| scanType | enum<string> | yes | required, enum |
| criticalCount | integer (int32) | no | optional, format `int32` |
| highCount | integer (int32) | no | optional, format `int32` |
| mediumCount | integer (int32) | no | optional, format `int32` |
| lowCount | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| reportUrl | string | no | optional |
| nextScanDue | string (date-time) | no | optional, format `date-time` |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "scanDate": "2026-03-27T10:15:30Z",
  "scannerTool": "string",
  "scanType": "INTERNAL",
  "criticalCount": "string",
  "highCount": "string",
  "mediumCount": "string",
  "lowCount": "string",
  "status": "PASS",
  "reportUrl": "https://example.com/resource",
  "nextScanDue": "2026-03-27T10:15:30Z",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| scanDate | string (date-time) | yes | required, format `date-time` |
| scannerTool | string | no | optional |
| scanType | enum<string> | yes | required, enum |
| criticalCount | integer (int32) | no | optional, format `int32` |
| highCount | integer (int32) | no | optional, format `int32` |
| mediumCount | integer (int32) | no | optional, format `int32` |
| lowCount | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| reportUrl | string | no | optional |
| nextScanDue | string (date-time) | no | optional, format `date-time` |
| notes | string | no | optional |
| createdAt | string (date-time) | no | optional, format `date-time` |
| updatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "scanDate": "2026-03-27T10:15:30Z",
  "scannerTool": "string",
  "scanType": "INTERNAL",
  "criticalCount": "string",
  "highCount": "string",
  "mediumCount": "string",
  "lowCount": "string",
  "status": "PASS",
  "reportUrl": "https://example.com/resource",
  "nextScanDue": "2026-03-27T10:15:30Z",
  "notes": "string",
  "createdAt": "2026-03-27T10:15:30Z",
  "updatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `scanType`: `INTERNAL` = Internal., `EXTERNAL` = External., `ASV` = Asv., `ICS_OT` = Ics ot.
- request `status`: `PASS` = Pass., `FAIL` = Fail., `PENDING_REMEDIATION` = Pending remediation.
- response `scanType`: `INTERNAL` = Internal., `EXTERNAL` = External., `ASV` = Asv., `ICS_OT` = Ics ot.
- response `status`: `PASS` = Pass., `FAIL` = Fail., `PENDING_REMEDIATION` = Pending remediation.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.
- Resolve `organisationId` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/ComplianceController.java`

## AI Insights Service

Stage: **Operations & Insights**

### GET /api/v1/ai/insights

User type: **Authenticated user**

Description: List or summarize insights.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| type | query | no | string | optional |
| severity | query | no | string | optional |
| unresolvedOnly | query | no | boolean | optional |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "assetId": "11111111-1111-1111-1111-111111111111",
    "assetName": "string",
    "assetTag": "string",
    "insightType": "MAINTENANCE_DUE",
    "severity": "LOW",
    "title": "string",
    "description": "string",
    "confidence": "string",
    "predictedDate": "2026-03-27",
    "resolved": true,
    "resolvedAt": "2026-03-27T10:15:30Z",
    "createdAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- response `[].insightType`: `MAINTENANCE_DUE` = Maintenance due., `FAILURE_RISK` = Failure risk., `WARRANTY_EXPIRY` = Warranty expiry., `DEPRECIATION_COMPLETE` = Depreciation complete., `ASSET_AGING` = Asset aging., `ANOMALY` = Anomaly., `UNDERUTILIZED` = Underutilized., `LICENSE_EXPIRY` = License expiry.
- response `[].severity`: `LOW` = Low., `MEDIUM` = Medium., `HIGH` = High., `CRITICAL` = Critical.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AIInsightsController.java`

### POST /api/v1/ai/insights/generate

User type: **Org admin or system admin**

Description: Create or trigger a new generate operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "assetId": "11111111-1111-1111-1111-111111111111",
    "assetName": "string",
    "assetTag": "string",
    "insightType": "MAINTENANCE_DUE",
    "severity": "LOW",
    "title": "string",
    "description": "string",
    "confidence": "string",
    "predictedDate": "2026-03-27",
    "resolved": true,
    "resolvedAt": "2026-03-27T10:15:30Z",
    "createdAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- response `[].insightType`: `MAINTENANCE_DUE` = Maintenance due., `FAILURE_RISK` = Failure risk., `WARRANTY_EXPIRY` = Warranty expiry., `DEPRECIATION_COMPLETE` = Depreciation complete., `ASSET_AGING` = Asset aging., `ANOMALY` = Anomaly., `UNDERUTILIZED` = Underutilized., `LICENSE_EXPIRY` = License expiry.
- response `[].severity`: `LOW` = Low., `MEDIUM` = Medium., `HIGH` = High., `CRITICAL` = Critical.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AIInsightsController.java`

### GET /api/v1/ai/insights/summary

User type: **Authenticated user**

Description: List or summarize summary.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AIInsightsController.java`

### GET /api/v1/ai/insights/{id}

User type: **Authenticated user**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| assetId | string (uuid) | no | optional, format `uuid` |
| assetName | string | no | optional |
| assetTag | string | no | optional |
| insightType | enum<string> | no | optional, enum |
| severity | enum<string> | no | optional, enum |
| title | string | no | optional |
| description | string | no | optional |
| confidence | number (double) | no | optional, format `double` |
| predictedDate | string (date) | no | optional, format `date` |
| resolved | boolean | no | optional |
| resolvedAt | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "assetId": "11111111-1111-1111-1111-111111111111",
  "assetName": "string",
  "assetTag": "string",
  "insightType": "MAINTENANCE_DUE",
  "severity": "LOW",
  "title": "string",
  "description": "string",
  "confidence": "string",
  "predictedDate": "2026-03-27",
  "resolved": true,
  "resolvedAt": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- response `insightType`: `MAINTENANCE_DUE` = Maintenance due., `FAILURE_RISK` = Failure risk., `WARRANTY_EXPIRY` = Warranty expiry., `DEPRECIATION_COMPLETE` = Depreciation complete., `ASSET_AGING` = Asset aging., `ANOMALY` = Anomaly., `UNDERUTILIZED` = Underutilized., `LICENSE_EXPIRY` = License expiry.
- response `severity`: `LOW` = Low., `MEDIUM` = Medium., `HIGH` = High., `CRITICAL` = Critical.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AIInsightsController.java`

### POST /api/v1/ai/insights/{id}/resolve

User type: **Authenticated user**

Description: Create or trigger a new resolve operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/AIInsightsController.java`

## Dashboard Service

Stage: **Operations & Insights**

### GET /api/v1/dashboard/assets-by-department

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize assets by department.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/DashboardController.java`

### GET /api/v1/dashboard/assets-by-status

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize assets by status.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/DashboardController.java`

### GET /api/v1/dashboard/depreciation-summary

User type: **Org admin or system admin**

Description: List or summarize depreciation summary.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/DashboardController.java`

### GET /api/v1/dashboard/maintenance-alerts

User type: **Org admin or system admin**

Description: List or summarize maintenance alerts.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/DashboardController.java`

### GET /api/v1/dashboard/summary

User type: **Org admin or system admin**

Description: List or summarize summary.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/DashboardController.java`

## Analytics Service

Stage: **Operations & Insights**

### GET /api/v1/analytics/assets

User type: **Org admin or system admin**

Description: List or summarize assets.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| period | query | no | string | optional |
| groupBy | query | no | string | optional |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AnalyticsController.java`

### GET /api/v1/analytics/depreciation-trends

User type: **Org admin or system admin**

Description: List or summarize depreciation trends.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| months | query | no | integer (int32) | optional, format `int32` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AnalyticsController.java`

### GET /api/v1/analytics/financial

User type: **Org admin or system admin**

Description: List or summarize financial.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| period | query | no | string | optional |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AnalyticsController.java`

### GET /api/v1/analytics/maintenance

User type: **Org admin or system admin**

Description: List or summarize maintenance.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AnalyticsController.java`

### GET /api/v1/analytics/purchase-orders

User type: **Org admin or system admin**

Description: List or summarize purchase orders.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| period | query | no | string | optional |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/AnalyticsController.java`

## Reports Service

Stage: **Operations & Insights**

### POST /api/v1/reports/assets

User type: **Org admin or system admin**

Description: Create or trigger a new assets operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| format | enum<string> | no | optional, enum |

```json
{
  "format": "PDF"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| reportId | string (uuid) | no | optional, format `uuid` |
| format | string | no | optional |
| reportType | string | no | optional |
| status | string | no | optional |
| downloadUrl | string | no | optional |
| generatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "reportId": "11111111-1111-1111-1111-111111111111",
  "format": "string",
  "reportType": "string",
  "status": "string",
  "downloadUrl": "https://example.com/resource",
  "generatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `format`: `PDF` = Pdf., `EXCEL` = Excel., `CSV` = Csv.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ReportsController.java`

### GET /api/v1/reports/assets/{reportId}/download

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| reportId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `reportId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ReportsController.java`

### POST /api/v1/reports/financial

User type: **Org admin or system admin**

Description: Create or trigger a new financial operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| format | enum<string> | no | optional, enum |

```json
{
  "format": "PDF"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| reportId | string (uuid) | no | optional, format `uuid` |
| format | string | no | optional |
| reportType | string | no | optional |
| status | string | no | optional |
| downloadUrl | string | no | optional |
| generatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "reportId": "11111111-1111-1111-1111-111111111111",
  "format": "string",
  "reportType": "string",
  "status": "string",
  "downloadUrl": "https://example.com/resource",
  "generatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `format`: `PDF` = Pdf., `EXCEL` = Excel., `CSV` = Csv.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ReportsController.java`

### GET /api/v1/reports/financial/{reportId}/download

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| reportId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `reportId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ReportsController.java`

### GET /api/v1/reports/history

User type: **Org admin or system admin**

Description: List or summarize history.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| limit | query | no | integer (int32) | optional, format `int32` |
| offset | query | no | integer (int32) | optional, format `int32` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ReportsController.java`

### POST /api/v1/reports/maintenance

User type: **Org admin or system admin**

Description: Create or trigger a new maintenance operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| format | enum<string> | no | optional, enum |

```json
{
  "format": "PDF"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| reportId | string (uuid) | no | optional, format `uuid` |
| format | string | no | optional |
| reportType | string | no | optional |
| status | string | no | optional |
| downloadUrl | string | no | optional |
| generatedAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "reportId": "11111111-1111-1111-1111-111111111111",
  "format": "string",
  "reportType": "string",
  "status": "string",
  "downloadUrl": "https://example.com/resource",
  "generatedAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- request `format`: `PDF` = Pdf., `EXCEL` = Excel., `CSV` = Csv.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/ReportsController.java`

### GET /api/v1/reports/maintenance/{reportId}/download

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| reportId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `reportId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ReportsController.java`

### DELETE /api/v1/reports/{reportId}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| reportId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **404** (Not found envelope or no body)

```json
{
  "status": 404,
  "message": "Resource not found",
  "errorCode": "NOT_FOUND",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `reportId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ReportsController.java`

### GET /api/v1/reports/{reportId}/download

User type: **Authenticated user, org admin, or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| reportId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `reportId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/ReportsController.java`

## Notifications Service

Stage: **Operations & Insights**

### DELETE /api/v1/notifications

User type: **Authenticated user, org admin, or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/NotificationsController.java`

### GET /api/v1/notifications

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize notifications.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| type | query | no | string | optional |
| status | query | no | string | optional |
| limit | query | no | integer (int32) | optional, format `int32` |
| offset | query | no | integer (int64) | optional, format `int64` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| total | integer (int64) | no | optional, format `int64` |
| limit | integer (int32) | no | optional, format `int32` |
| offset | integer (int64) | no | optional, format `int64` |
| items | array<object> | no | optional |
| items[].notificationId | string (uuid) | no | optional, format `uuid` |
| items[].type | enum<string> | no | optional, enum |
| items[].title | string | no | optional |
| items[].message | string | no | optional |
| items[].entityId | string (uuid) | no | optional, format `uuid` |
| items[].actionUrl | string | no | optional |
| items[].read | boolean | no | optional |
| items[].readAt | string (date-time) | no | optional, format `date-time` |
| items[].createdAt | string (date-time) | no | optional, format `date-time` |
| unreadCount | integer (int64) | no | optional, format `int64` |

```json
{
  "total": "string",
  "limit": "string",
  "offset": "string",
  "items": [
    {
      "notificationId": "11111111-1111-1111-1111-111111111111",
      "type": "DEPRECATION",
      "title": "string",
      "message": "string",
      "entityId": "11111111-1111-1111-1111-111111111111",
      "actionUrl": "https://example.com/resource",
      "read": true,
      "readAt": "2026-03-27T10:15:30Z",
      "createdAt": "2026-03-27T10:15:30Z"
    }
  ],
  "unreadCount": "string"
}
```

Enum values:

- response `items.[].type`: `DEPRECATION` = Deprecation., `MAINTENANCE` = Maintenance., `APPROVAL` = Approval., `SYSTEM` = System., `TRANSFER` = Transfer., `DISPOSAL` = Disposal., `PURCHASE_ORDER` = Purchase order.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/NotificationsController.java`

### PATCH /api/v1/notifications/mark-all-read

User type: **Authenticated user, org admin, or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/NotificationsController.java`

### GET /api/v1/notifications/preferences

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize preferences.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| emailNotifications | object<string, boolean> | no | optional |
| pushNotifications | boolean | no | optional |
| inAppNotifications | boolean | no | optional |
| dailyDigest | boolean | no | optional |
| digestTime | string | no | optional |

```json
{
  "emailNotifications": {
    "key": true
  },
  "pushNotifications": true,
  "inAppNotifications": true,
  "dailyDigest": true,
  "digestTime": "09:00"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/NotificationsController.java`

### PATCH /api/v1/notifications/preferences

User type: **Authenticated user, org admin, or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| emailNotifications | object | no | optional |
| pushNotifications | boolean | no | optional |
| inAppNotifications | boolean | no | optional |
| dailyDigest | boolean | no | optional |
| digestTime | string | no | optional, pattern `HH:mm` |

```json
{
  "emailNotifications": {},
  "pushNotifications": true,
  "inAppNotifications": true,
  "dailyDigest": true,
  "digestTime": "09:00"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| emailNotifications | object<string, boolean> | no | optional |
| pushNotifications | boolean | no | optional |
| inAppNotifications | boolean | no | optional |
| dailyDigest | boolean | no | optional |
| digestTime | string | no | optional |

```json
{
  "emailNotifications": {
    "key": true
  },
  "pushNotifications": true,
  "inAppNotifications": true,
  "dailyDigest": true,
  "digestTime": "09:00"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/NotificationsController.java`

### GET /api/v1/notifications/summary

User type: **Authenticated user, org admin, or system admin**

Description: List or summarize summary.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/NotificationsController.java`

### DELETE /api/v1/notifications/{notificationId}

User type: **Authenticated user, org admin, or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| notificationId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `notificationId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/NotificationsController.java`

### PATCH /api/v1/notifications/{notificationId}/read

User type: **Authenticated user, org admin, or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Authenticated user, org admin, or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| notificationId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| additionalProperties | object | no | dynamic object map |

```json
{
  "key": {}
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `notificationId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/NotificationsController.java`

## Webhooks Service

Stage: **Integrations**

### GET /api/v1/webhooks

User type: **Org admin or system admin**

Description: List or summarize webhooks.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "string",
    "url": "https://example.com/resource",
    "events": [
      "string"
    ],
    "active": true,
    "secret": "string",
    "deliveryCount": "string",
    "failureCount": "string",
    "lastTriggeredAt": "2026-03-27T10:15:30Z",
    "createdAt": "2026-03-27T10:15:30Z"
  }
]
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/WebhooksController.java`

### POST /api/v1/webhooks

User type: **Org admin or system admin**

Description: Create or trigger a new webhooks operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required, minLength 0, maxLength 200 |
| url | string | yes | required, minLength 0, maxLength 2048 |
| events | array<string> | no | optional |
| active | boolean | no | optional |
| secret | string | no | optional |
| deliveryCount | integer (int64) | no | optional, format `int64` |
| failureCount | integer (int64) | no | optional, format `int64` |
| lastTriggeredAt | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "url": "https://example.com/resource",
  "events": [
    "string"
  ],
  "active": true,
  "secret": "string",
  "deliveryCount": "string",
  "failureCount": "string",
  "lastTriggeredAt": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required, minLength 0, maxLength 200 |
| url | string | yes | required, minLength 0, maxLength 2048 |
| events | array<string> | no | optional |
| active | boolean | no | optional |
| secret | string | no | optional |
| deliveryCount | integer (int64) | no | optional, format `int64` |
| failureCount | integer (int64) | no | optional, format `int64` |
| lastTriggeredAt | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "url": "https://example.com/resource",
  "events": [
    "string"
  ],
  "active": true,
  "secret": "string",
  "deliveryCount": "string",
  "failureCount": "string",
  "lastTriggeredAt": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/WebhooksController.java`

### DELETE /api/v1/webhooks/{id}

User type: **Org admin or system admin**

Description: Delete or disable the targeted v1 record.

When to call: Call after the user confirms a destructive action.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 204**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/WebhooksController.java`

### GET /api/v1/webhooks/{id}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required, minLength 0, maxLength 200 |
| url | string | yes | required, minLength 0, maxLength 2048 |
| events | array<string> | no | optional |
| active | boolean | no | optional |
| secret | string | no | optional |
| deliveryCount | integer (int64) | no | optional, format `int64` |
| failureCount | integer (int64) | no | optional, format `int64` |
| lastTriggeredAt | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "url": "https://example.com/resource",
  "events": [
    "string"
  ],
  "active": true,
  "secret": "string",
  "deliveryCount": "string",
  "failureCount": "string",
  "lastTriggeredAt": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/WebhooksController.java`

### PATCH /api/v1/webhooks/{id}

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required, minLength 0, maxLength 200 |
| url | string | yes | required, minLength 0, maxLength 2048 |
| events | array<string> | no | optional |
| active | boolean | no | optional |
| secret | string | no | optional |
| deliveryCount | integer (int64) | no | optional, format `int64` |
| failureCount | integer (int64) | no | optional, format `int64` |
| lastTriggeredAt | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "url": "https://example.com/resource",
  "events": [
    "string"
  ],
  "active": true,
  "secret": "string",
  "deliveryCount": "string",
  "failureCount": "string",
  "lastTriggeredAt": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z"
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| name | string | yes | required, minLength 0, maxLength 200 |
| url | string | yes | required, minLength 0, maxLength 2048 |
| events | array<string> | no | optional |
| active | boolean | no | optional |
| secret | string | no | optional |
| deliveryCount | integer (int64) | no | optional, format `int64` |
| failureCount | integer (int64) | no | optional, format `int64` |
| lastTriggeredAt | string (date-time) | no | optional, format `date-time` |
| createdAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "name": "string",
  "url": "https://example.com/resource",
  "events": [
    "string"
  ],
  "active": true,
  "secret": "string",
  "deliveryCount": "string",
  "failureCount": "string",
  "lastTriggeredAt": "2026-03-27T10:15:30Z",
  "createdAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `id` before submitting the payload.

Source reference: `src/main/java/com/example/demo/controllers/v1/WebhooksController.java`

### GET /api/v1/webhooks/{id}/deliveries

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |
| status | query | no | string | optional |
| limit | query | no | integer (int32) | optional, format `int32` |
| offset | query | no | integer (int64) | optional, format `int64` |
| pageable | query | yes | Pageable | required |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| total | integer (int64) | no | optional, format `int64` |
| limit | integer (int32) | no | optional, format `int32` |
| offset | integer (int64) | no | optional, format `int64` |
| items | array<object> | no | optional |
| items[].deliveryId | string (uuid) | no | optional, format `uuid` |
| items[].webhookId | string (uuid) | no | optional, format `uuid` |
| items[].eventName | string | no | optional |
| items[].payload | string | no | optional |
| items[].statusCode | integer (int32) | no | optional, format `int32` |
| items[].responseBody | string | no | optional |
| items[].responseTimeMs | integer (int64) | no | optional, format `int64` |
| items[].attempts | integer (int32) | no | optional, format `int32` |
| items[].status | string | no | optional |
| items[].triggeredAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "total": "string",
  "limit": "string",
  "offset": "string",
  "items": [
    {
      "deliveryId": "11111111-1111-1111-1111-111111111111",
      "webhookId": "11111111-1111-1111-1111-111111111111",
      "eventName": "string",
      "payload": "string",
      "statusCode": "123456",
      "responseBody": "string",
      "responseTimeMs": "09:00",
      "attempts": "string",
      "status": "string",
      "triggeredAt": "2026-03-27T10:15:30Z"
    }
  ]
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/WebhooksController.java`

### GET /api/v1/webhooks/{id}/deliveries/{deliveryId}

User type: **Org admin or system admin**

Description: Fetch one v1 record by identifier.

When to call: Call when opening a detail page or refreshing a single record.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |
| deliveryId | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| deliveryId | string (uuid) | no | optional, format `uuid` |
| webhookId | string (uuid) | no | optional, format `uuid` |
| eventName | string | no | optional |
| payload | string | no | optional |
| statusCode | integer (int32) | no | optional, format `int32` |
| responseBody | string | no | optional |
| responseTimeMs | integer (int64) | no | optional, format `int64` |
| attempts | integer (int32) | no | optional, format `int32` |
| status | string | no | optional |
| triggeredAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "deliveryId": "11111111-1111-1111-1111-111111111111",
  "webhookId": "11111111-1111-1111-1111-111111111111",
  "eventName": "string",
  "payload": "string",
  "statusCode": "123456",
  "responseBody": "string",
  "responseTimeMs": "09:00",
  "attempts": "string",
  "status": "string",
  "triggeredAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.
- Resolve `deliveryId` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/WebhooksController.java`

### POST /api/v1/webhooks/{id}/test

User type: **Org admin or system admin**

Description: Create or trigger a new test operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| id | path | yes | string (uuid) | required, format `uuid` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| deliveryId | string (uuid) | no | optional, format `uuid` |
| webhookId | string (uuid) | no | optional, format `uuid` |
| eventName | string | no | optional |
| payload | string | no | optional |
| statusCode | integer (int32) | no | optional, format `int32` |
| responseBody | string | no | optional |
| responseTimeMs | integer (int64) | no | optional, format `int64` |
| attempts | integer (int32) | no | optional, format `int32` |
| status | string | no | optional |
| triggeredAt | string (date-time) | no | optional, format `date-time` |

```json
{
  "deliveryId": "11111111-1111-1111-1111-111111111111",
  "webhookId": "11111111-1111-1111-1111-111111111111",
  "eventName": "string",
  "payload": "string",
  "statusCode": "123456",
  "responseBody": "string",
  "responseTimeMs": "09:00",
  "attempts": "string",
  "status": "string",
  "triggeredAt": "2026-03-27T10:15:30Z"
}
```

Enum values:

- None.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.
- Resolve `id` before calling this route.

Source reference: `src/main/java/com/example/demo/controllers/v1/WebhooksController.java`

## Billing Service

Stage: **Commercial**

### POST /api/v1/billing/checkout

User type: **Org admin or system admin**

Description: Create or trigger a new checkout operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| planCode | string | yes | required |
| callbackUrl | string | no | optional |

```json
{
  "planCode": "123456",
  "callbackUrl": "https://example.com/resource"
}
```

Response payload: success returns **HTTP 201**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| authorizationUrl | string | no | optional |
| accessCode | string | no | optional |
| reference | string | no | optional |

```json
{
  "authorizationUrl": "https://example.com/resource",
  "accessCode": "123456",
  "reference": "string"
}
```

Enum values:

- None.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/BillingController.java`

### POST /api/v1/billing/checkout/verify

User type: **Org admin or system admin**

Description: Create or trigger a new verify operation.

When to call: Call when the user submits a create form or action trigger.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| reference | query | yes | string | required |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| plan | object | no | optional |
| plan.code | string | no | optional |
| plan.name | string | no | optional |
| plan.tier | enum<string> | no | optional, enum |
| plan.interval | enum<string> | no | optional, enum |
| plan.amountMinor | integer (int64) | no | optional, format `int64` |
| plan.currency | string | no | optional |
| plan.maxAssets | integer (int32) | no | optional, format `int32` |
| plan.maxEmployees | integer (int32) | no | optional, format `int32` |
| plan.analyticsEnabled | boolean | no | optional |
| plan.auditRetentionDays | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| autoRenew | boolean | no | optional |
| currentPeriodStart | string (date-time) | no | optional, format `date-time` |
| currentPeriodEnd | string (date-time) | no | optional, format `date-time` |
| nextBillingAt | string (date-time) | no | optional, format `date-time` |
| currentAssetCount | integer (int64) | no | optional, format `int64` |
| currentEmployeeCount | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "plan": {
    "code": "123456",
    "name": "string",
    "tier": "FREEMIUM",
    "interval": "MONTHLY",
    "amountMinor": "string",
    "currency": "USD",
    "maxAssets": "string",
    "maxEmployees": "string",
    "analyticsEnabled": true,
    "auditRetentionDays": "string"
  },
  "status": "ACTIVE",
  "autoRenew": true,
  "currentPeriodStart": "2026-03-27T10:15:30Z",
  "currentPeriodEnd": "2026-03-27T10:15:30Z",
  "nextBillingAt": "2026-03-27T10:15:30Z",
  "currentAssetCount": "string",
  "currentEmployeeCount": "string"
}
```

Enum values:

- response `plan.tier`: `FREEMIUM` = Freemium., `BASIC` = Basic., `PREMIUM` = Premium.
- response `plan.interval`: `MONTHLY` = Monthly., `ANNUALLY` = Annually.
- response `status`: `ACTIVE` = Active., `PAST_DUE` = Past due., `CANCELED` = Canceled., `EXPIRED` = Expired.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/BillingController.java`

### GET /api/v1/billing/plans

User type: **Org admin or system admin**

Description: List or summarize plans.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| items | object | yes | array item |

```json
[
  {
    "code": "123456",
    "name": "string",
    "tier": "FREEMIUM",
    "interval": "MONTHLY",
    "amountMinor": "string",
    "currency": "USD",
    "maxAssets": "string",
    "maxEmployees": "string",
    "analyticsEnabled": true,
    "auditRetentionDays": "string"
  }
]
```

Enum values:

- response `[].tier`: `FREEMIUM` = Freemium., `BASIC` = Basic., `PREMIUM` = Premium.
- response `[].interval`: `MONTHLY` = Monthly., `ANNUALLY` = Annually.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/BillingController.java`

### GET /api/v1/billing/subscription

User type: **Org admin or system admin**

Description: List or summarize subscription.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| plan | object | no | optional |
| plan.code | string | no | optional |
| plan.name | string | no | optional |
| plan.tier | enum<string> | no | optional, enum |
| plan.interval | enum<string> | no | optional, enum |
| plan.amountMinor | integer (int64) | no | optional, format `int64` |
| plan.currency | string | no | optional |
| plan.maxAssets | integer (int32) | no | optional, format `int32` |
| plan.maxEmployees | integer (int32) | no | optional, format `int32` |
| plan.analyticsEnabled | boolean | no | optional |
| plan.auditRetentionDays | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| autoRenew | boolean | no | optional |
| currentPeriodStart | string (date-time) | no | optional, format `date-time` |
| currentPeriodEnd | string (date-time) | no | optional, format `date-time` |
| nextBillingAt | string (date-time) | no | optional, format `date-time` |
| currentAssetCount | integer (int64) | no | optional, format `int64` |
| currentEmployeeCount | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "plan": {
    "code": "123456",
    "name": "string",
    "tier": "FREEMIUM",
    "interval": "MONTHLY",
    "amountMinor": "string",
    "currency": "USD",
    "maxAssets": "string",
    "maxEmployees": "string",
    "analyticsEnabled": true,
    "auditRetentionDays": "string"
  },
  "status": "ACTIVE",
  "autoRenew": true,
  "currentPeriodStart": "2026-03-27T10:15:30Z",
  "currentPeriodEnd": "2026-03-27T10:15:30Z",
  "nextBillingAt": "2026-03-27T10:15:30Z",
  "currentAssetCount": "string",
  "currentEmployeeCount": "string"
}
```

Enum values:

- response `plan.tier`: `FREEMIUM` = Freemium., `BASIC` = Basic., `PREMIUM` = Premium.
- response `plan.interval`: `MONTHLY` = Monthly., `ANNUALLY` = Annually.
- response `status`: `ACTIVE` = Active., `PAST_DUE` = Past due., `CANCELED` = Canceled., `EXPIRED` = Expired.

Error responses:

- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/BillingController.java`

### PATCH /api/v1/billing/subscription/auto-renew

User type: **Org admin or system admin**

Description: Partially update the targeted v1 record.

When to call: Call from inline-edit or partial-save flows.

Authentication & headers:

- Auth mechanism: JWT bearer auth enforced by Spring Security. Allowed user type: Org admin or system admin.
- `Authorization` (required): Bearer <jwt>
- `X-Organisation-Id` (recommended): Tenant context header for deterministic organisation routing.
- `Content-Type` (required): application/json
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| enabled | boolean | yes | required |

```json
{
  "enabled": true
}
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| id | string (uuid) | no | optional, format `uuid` |
| organisationId | string (uuid) | no | optional, format `uuid` |
| plan | object | no | optional |
| plan.code | string | no | optional |
| plan.name | string | no | optional |
| plan.tier | enum<string> | no | optional, enum |
| plan.interval | enum<string> | no | optional, enum |
| plan.amountMinor | integer (int64) | no | optional, format `int64` |
| plan.currency | string | no | optional |
| plan.maxAssets | integer (int32) | no | optional, format `int32` |
| plan.maxEmployees | integer (int32) | no | optional, format `int32` |
| plan.analyticsEnabled | boolean | no | optional |
| plan.auditRetentionDays | integer (int32) | no | optional, format `int32` |
| status | enum<string> | no | optional, enum |
| autoRenew | boolean | no | optional |
| currentPeriodStart | string (date-time) | no | optional, format `date-time` |
| currentPeriodEnd | string (date-time) | no | optional, format `date-time` |
| nextBillingAt | string (date-time) | no | optional, format `date-time` |
| currentAssetCount | integer (int64) | no | optional, format `int64` |
| currentEmployeeCount | integer (int64) | no | optional, format `int64` |

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "organisationId": "11111111-1111-1111-1111-111111111111",
  "plan": {
    "code": "123456",
    "name": "string",
    "tier": "FREEMIUM",
    "interval": "MONTHLY",
    "amountMinor": "string",
    "currency": "USD",
    "maxAssets": "string",
    "maxEmployees": "string",
    "analyticsEnabled": true,
    "auditRetentionDays": "string"
  },
  "status": "ACTIVE",
  "autoRenew": true,
  "currentPeriodStart": "2026-03-27T10:15:30Z",
  "currentPeriodEnd": "2026-03-27T10:15:30Z",
  "nextBillingAt": "2026-03-27T10:15:30Z",
  "currentAssetCount": "string",
  "currentEmployeeCount": "string"
}
```

Enum values:

- response `plan.tier`: `FREEMIUM` = Freemium., `BASIC` = Basic., `PREMIUM` = Premium.
- response `plan.interval`: `MONTHLY` = Monthly., `ANNUALLY` = Annually.
- response `status`: `ACTIVE` = Active., `PAST_DUE` = Past due., `CANCELED` = Canceled., `EXPIRED` = Expired.

Error responses:

- **400** (Shared validation envelope)

```json
{
  "status": 400,
  "message": "Validation failed",
  "errorCode": "VALIDATION_FAILED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123",
  "errors": {
    "field": "must not be blank"
  }
}
```
- **401** (Servlet/security error or simple error map)

```json
{
  "error": "Invalid or expired token"
}
```
- **403** (Forbidden envelope or servlet error)

```json
{
  "status": 403,
  "message": "Access denied",
  "errorCode": "FORBIDDEN",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```
- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- Authenticate first with `/api/v1/auth/login`, the JWT from `/api/v1/tenant/register`, or an SSO callback endpoint.

Source reference: `src/main/java/com/example/demo/controllers/v1/BillingController.java`

### POST /api/v1/billing/webhooks/paystack

User type: **Guest**

Description: Receive Paystack webhook events after signature verification.

When to call: Frontend should not call this endpoint directly.

Authentication & headers:

- Auth mechanism: Signature-based webhook validation; no JWT.
- `x-paystack-signature` (required): SHA512 signature for the raw webhook body.
- `Content-Type` (required): Paystack sends JSON; controller consumes the raw string body.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| x-paystack-signature | header | no | string | optional |

Request payload:

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | string | yes | See example below |

```json
"string"
```

Response payload: success returns **HTTP 200**.

This route returns no JSON body on success.

```json
null
```

Enum values:

- None.

Error responses:

- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- No extra dependency beyond the current authentication context.

Source reference: `src/main/java/com/example/demo/controllers/v1/BillingController.java`

## Health & Monitoring Service

Stage: **Platform**

### GET /api/v1/health

User type: **Guest**

Description: List or summarize health.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- No extra dependency beyond the current authentication context.

Source reference: `src/main/java/com/example/demo/controllers/v1/HealthMonitoringController.java`

### GET /api/v1/health/detailed

User type: **Guest**

Description: List or summarize detailed.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- No extra dependency beyond the current authentication context.

Source reference: `src/main/java/com/example/demo/controllers/v1/HealthMonitoringController.java`

### GET /api/v1/metrics

User type: **Guest**

Description: List or summarize metrics.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| period | query | no | string | optional |
| metric | query | no | string | optional |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- No extra dependency beyond the current authentication context.

Source reference: `src/main/java/com/example/demo/controllers/v1/HealthMonitoringController.java`

### GET /api/v1/metrics/database

User type: **Guest**

Description: List or summarize database.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- No extra dependency beyond the current authentication context.

Source reference: `src/main/java/com/example/demo/controllers/v1/HealthMonitoringController.java`

### GET /api/v1/metrics/endpoints

User type: **Guest**

Description: List or summarize endpoints.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| sortBy | query | no | string | optional |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- No extra dependency beyond the current authentication context.

Source reference: `src/main/java/com/example/demo/controllers/v1/HealthMonitoringController.java`

### GET /api/v1/metrics/errors

User type: **Guest**

Description: List or summarize errors.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- No extra dependency beyond the current authentication context.

Source reference: `src/main/java/com/example/demo/controllers/v1/HealthMonitoringController.java`

### GET /api/v1/metrics/throughput

User type: **Guest**

Description: List or summarize throughput.

When to call: Call when the UI needs list data, dashboards, or summary widgets.

Authentication & headers:

- Auth mechanism: No JWT required.
- `Accept` (recommended): application/json unless the endpoint explicitly returns binary content.
- `X-Request-ID` (optional): Request correlation id echoed back by the API.

Request parameters:

| Name | In | Required | Type | Validation / Notes |
| --- | --- | --- | --- | --- |
| hours | query | no | integer (int32) | optional, format `int32` |

Request payload:

No request body.

```json
null
```

Response payload: success returns **HTTP 200**.

| Field | Type | Required | Validation / Notes |
| --- | --- | --- | --- |
| body | object | yes | See example below |

```json
{}
```

Enum values:

- None.

Error responses:

- **429** (Servlet error; body is not stable JSON)
- **500** (Shared internal envelope)

```json
{
  "status": 500,
  "message": "An unexpected error occurred. Please try again later.",
  "errorCode": "INTERNAL_SERVER_ERROR_UNEXPECTED",
  "timestamp": "2026-03-27T10:15:30Z",
  "requestId": "req-123"
}
```

Call dependencies:

- No extra dependency beyond the current authentication context.

Source reference: `src/main/java/com/example/demo/controllers/v1/HealthMonitoringController.java`

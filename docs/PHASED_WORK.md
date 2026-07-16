# Phased Work — Traceability

Maps every concrete code change called out in the market-readiness report (§9) to a phase and a ticket in `ASSETIQ_PHASED_FIX_PLAN.md`.

Use this file when triaging a change: look up the file, find the phase, open the ticket.

---

## Backend (`Enterprise-Asset-Manager/`)

| File | Change | Phase | Ticket |
|---|---|---|---|
| `src/main/java/com/assetiq/services/impl/BillingServiceImpl.java` | Add `channels=[card,mobile_money,bank,ussd]` to Paystack init payload | 1 | P1-6 |
| `src/main/java/com/assetiq/config/BillingPlanSeeder.java` | Per-plan currency, seed GHS plans | 1 | P1-3 |
| `src/main/java/com/assetiq/models/Organisation.java` | Add `billingCurrency`, `dataResidencyRegion`, `dpoName`, `dpoEmail`, `dpoPhone`, `processingPurposes` | 1 / 2 | P1-1, P2-1 |
| `src/main/resources/db/migration/V7__baseline.sql` | Schema baseline generated from `pg_dump -s` | 0 | P0-2 |
| `src/main/resources/db/migration/V7a__feature_flags.sql` | Feature flag table | 0 | P0-8 |
| `src/main/resources/db/migration/V8__organisation_billing_currency.sql` | Add `billing_currency` column (nullable, GHS default) | 1 | P1-1 |
| `src/main/resources/db/migration/V9__org_dpa_fields.sql` | DPO + residency columns | 2 | P2-1 |
| `src/main/resources/db/migration/V10__consent_records.sql` | Consent record table | 2 | P2-2 |
| `src/main/resources/db/migration/V11__retention_policies.sql` | Retention policy engine | 2 | P2-5 |
| `src/main/resources/db/migration/V12__push_tokens.sql` | Push token registration | 4 | P4-5 |
| `src/main/java/com/assetiq/controllers/v1/PrivacyController.java` (new) | DSAR export + delete, consent CRUD | 2 | P2-3, P2-4 |
| `src/main/java/com/assetiq/services/TenantExportService.java` (new) | Async tenant-wide export | 2 | P2-6 |
| `src/main/java/com/assetiq/services/FeatureFlagService.java` (new) | Flag lookup + cache | 0 | P0-8 |
| `src/main/java/com/assetiq/security/annotation/FeatureFlag.java` (new) | Method-level annotation | 0 | P0-8 |
| `src/main/java/com/assetiq/services/impl/AssetServiceImpl.java` | Drop `"USD"` default; read from `Organisation.billingCurrency` | 1 | P1-2 |
| `src/main/java/com/assetiq/services/impl/PurchaseOrderServiceImpl.java` | same | 1 | P1-2 |
| `src/main/java/com/assetiq/services/impl/ExpenseServiceImpl.java` | same | 1 | P1-2 |
| `src/main/java/com/assetiq/services/impl/ContractServiceImpl.java` | same | 1 | P1-2 |
| `src/main/java/com/assetiq/services/impl/BudgetServiceImpl.java` | same | 1 | P1-2 |
| `src/main/java/com/assetiq/services/impl/LeaseRecordServiceImpl.java` | same | 1 | P1-2 |
| `src/main/java/com/assetiq/services/impl/AssetImportService.java` | same | 1 | P1-2 |
| `src/main/java/com/assetiq/services/impl/ExchangeRateServiceImpl.java` | same | 1 | P1-2 |
| `src/main/java/com/assetiq/config/DevDataSeeder.java` | Ghanaianise demo data | 1 | P1-10 |
| `src/main/java/com/assetiq/config/StartupSecurityValidator.java` | Refuse `ddl-auto=update` outside dev | 0 | P0-3 |
| `pom.xml` | Add Jacoco plugin + 30% line gate | 0 | P0-5 |

## Web customer portal (`assetiq-customer-portal/`)

| File | Change | Phase | Ticket |
|---|---|---|---|
| `src/lib/plans.ts` | GHS prices, `priceKobo` → `priceMinor`, per-plan currency | 1 | P1-4 |
| `src/lib/paystack.ts` | Pass `channels: [card, mobile_money, bank, ussd]` | 1 | P1-7 |
| `src/app/checkout/page.tsx` | MoMo badges + copy | 1 | P1-8 |
| `src/app/api/checkout/route.ts` | Read plan currency from signed reference | 1 | P1-5 |
| `src/app/api/trial/route.ts` (new) | 30-day trial key issuance | 2 | P2-12 |
| `src/app/trial/page.tsx` (new) | Trial CTA + form | 2 | P2-12 |
| `src/app/(legal)/privacy/page.tsx` (new) | Privacy policy | 2 | P2-7 |
| `src/app/(legal)/terms/page.tsx` (new) | Terms of service | 2 | P2-7 |
| `src/app/(legal)/refund/page.tsx` (new) | Refund policy | 2 | P2-7 |
| `src/app/(legal)/dpa/page.tsx` (new) | DPA addendum | 2 | P2-8 |
| `src/components/CookieBanner.tsx` (new) | Cookie consent banner | 2 | P2-11 |

## Mobile (`Enterprise-Asset-Mobile/`)

| File | Change | Phase | Ticket |
|---|---|---|---|
| `src/shared/sync/outbox.ts` (new) | Offline write queue | 4 | P4-1 |
| `src/shared/sync/types.ts` (new) | Outbox op shape | 4 | P4-1 |
| `src/shared/hooks/useNetworkStatus.ts` (new) | `@react-native-community/netinfo` | 4 | P4-2 |
| `src/shared/hooks/useBackgroundSync.ts` (new) | Drain outbox on reconnect | 4 | P4-3 |
| `src/shared/hooks/useBootstrapSession.ts` | Register Expo push token | 4 | P4-6 |
| `src/features/dashboard/DashboardScreen.tsx` | 3 `victory-native` charts | 4 | P4-8 |
| `src/features/assets/AssetFormScreen.tsx` | Full CRUD form | 4 | P4-9 |
| `app/(app)/asset/new.tsx` | Create screen | 4 | P4-9 |
| `app/(app)/asset/[id]/edit.tsx` | Edit screen | 4 | P4-9 |
| `app/(app)/asset/[id]/qr.tsx` | Render QR visually (svg) | 4 | P4-14 |
| `src/features/maintenance/MaintenanceFormScreen.tsx` (new) | Create / complete | 4 | P4-10 |
| `src/features/transfers/TransferDetailScreen.tsx` | Approve / reject | 4 | P4-11 |
| `src/features/procurement/PurchaseOrderDetailScreen.tsx` | Approve / reject | 4 | P4-12 |
| `src/shared/ui/FilterSheet.tsx` (new) | Reused filter sheet | 4 | P4-15 |
| `src/shared/utils/currency.ts` (new) | Org-aware currency formatter | 1 | P1-11 |
| `package.json` | Add `@react-native-community/netinfo` | 4 | P4-2 |

## Desktop (`Enterprise-Asset-manager-desktop-app/`)

Desktop is **parked** at end of Phase 0 unless a pilot requires it. When re-activated:

| File | Change | Phase | Ticket |
|---|---|---|---|
| `package.json` | Add `electron-builder`, `electron-updater`, `better-sqlite3` | 8 | — |
| `main/autoUpdater.ts` (new) | Auto-update | 8 | — |
| `main/offlineStore.ts` (new) | Local SQLite cache | 8 | — |
| `renderer/src/lib/currency.ts` (new) | Currency formatter | 1 | P1-11 |

## Standalone (`assetiq-standalone/`)

| File | Change | Phase | Ticket |
|---|---|---|---|
| `docker-compose.standalone.yml` | Support prebuilt images via `image:` | 0 | P0-1 |
| `docker-compose.standalone.images.yml` (new) | Image-mode compose file | 0 | P0-1 |
| `nginx/` | Caddy or certbot sidecar for Let's Encrypt | 5 | P5-5 |
| `scripts/setup-standalone.sh` | Prompt for country / currency / industry | 5 | P5-4 |
| `scripts/backup.sh` | Optional `--upload s3://...` | 5 | P5-6 |

## Cross-cutting

| File | Change | Phase | Ticket |
|---|---|---|---|
| `.github/workflows/ci.yml` (per repo) | Lint + typecheck + test + build | 0 | P0-4 |
| `status-page/docker-compose.yml` (new) | Uptime Kuma stack | 0 | P0-7 |
| `docs/runbooks/*.md` | 5 runbooks | 3 | P3-18 |
| `load/*.js` | k6 scripts | 3 | P3-7 |

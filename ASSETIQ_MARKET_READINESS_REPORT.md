# AssetIQ — Market Readiness Report

**Audit date:** 18 April 2026
**Auditor:** Architecture & go-to-market review
**Scope:** Backend (Spring Boot), Web Customer Portal (Next.js), Standalone (Docker), Mobile (Expo/React Native), Desktop (Electron)
**Primary launch target asked for:** SaaS (hosted web + mobile) — Ghana / broader African market
**Decision requested:** Is AssetIQ ready to ship? If not, what must change?

---

## 1. Executive Verdict

**Not launch-ready as a paid SaaS in its current state. Target: 8–12 weeks of focused work to a credible Ghana launch, 4–6 months to a defensible regional play.**

The platform is **architecturally mature and feature-broad** — the backend alone exposes 45+ REST controllers covering everything from asset lifecycle, maintenance, depreciation, procurement, compliance, audits, SSO/SAML, webhooks, AI insights, and multi-tenancy with Paystack billing. The standalone edition is genuinely thoughtful: RSA-signed license keys, grace periods, Docker Compose packaging, and a separate customer portal for key issuance and renewal.

However, **the shipping surface has visible inconsistencies that will hurt you in front of paying customers**:

- **Pricing is in Naira (NGN) in the customer portal, Cedis (GHS) is the backend default, and most in-app monetary fields default to USD.** Three currencies in one product is a launch-blocker.
- **Mobile money is not explicitly wired.** Paystack will accept MoMo if the merchant account enables it, but the checkout payload does not specify `channels`, the UI never mentions MoMo, and customers cannot tell they can pay with MTN / Telecel / AirtelTigo Money — the #1 payment method in Ghana.
- **Backend tests cover ~3% of the code (13 test files for 417 Java files).** The standalone QA checklist is thorough; the unit/integration test baseline is not.
- **Only 6 Flyway migrations for a schema that backs 40+ entities** — the rest is implicitly managed by Hibernate. That works in dev; it becomes a production liability the first time you need a backfill.
- **Mobile app is ~40% complete** per its own `MOBILE_IMPLEMENTATION_PLAN.md`: most create/edit forms, charts, workflows, offline sync, and push notifications are still pending.
- **Desktop app has no installer, no auto-update, no tests, and no offline store.** It is a browser UI wrapped in Electron, which is not what a customer thinks "desktop" means.
- **Self-hosted bundle references a sibling `Enterprise-Asset-manager-Frontend/` directory that is not present** in the repositories provided. The stack will not build until that is resolved.
- **No Ghana Data Protection Act (Act 843) tooling**: no consent records, no data subject request workflow, no retention/erasure policies, no DPO fields. Compliance with the Act is mandatory for any organisation processing personal data in Ghana and is routinely asked about in bank/telco/government procurement.

A strong Bank of Ghana (BOG) ICT Directive compliance module is already in place (`BogControl`, `ComplianceFramework.BOG`). That is a real differentiator for Ghanaian banks and Tier-1 non-bank financial institutions and should anchor your go-to-market.

**Overall readiness score, by surface:**

| Surface | Ready? | Score | One-line reason |
|---|---|---|---|
| Backend API | Yes, with hardening | **78 / 100** | Broad feature coverage, solid security posture, weak test baseline and thin Flyway history |
| Web Customer Portal (license sales) | Nearly | **70 / 100** | Works end-to-end for Paystack; wrong currency, no MoMo copy, no live plan page / marketing site |
| Standalone / Self-hosted | Nearly | **72 / 100** | Architecture is excellent; bundle references a missing frontend repo; no signed installers; pilot-only |
| Mobile (iOS / Android) | No | **45 / 100** | ~40% feature-complete, no offline, no push, no production store listings |
| Desktop (Electron) | No | **35 / 100** | No installer, no auto-update, no offline store, not differentiated from web |
| Ghana / Africa market fit | No | **40 / 100** | No MoMo, mixed currency, no DPA compliance, no localised onboarding, no pricing in GHS |

---

## 2. Architecture & Feature Inventory

### 2.1 Backend — `Enterprise-Asset-Manager/` (Java 21, Spring Boot 3.3)

**What exists (strong):**

- 45 REST controllers under `/api/v1/*` covering: assets, categories, maintenance, transfers, disposals, audits, departments, locations, suppliers, purchase orders, contracts, budgets, leases, licenses (software), checkouts, expenses, depreciation policies, disposal records, vendor performance, cloud assets, network discovery, reports, dashboards, analytics, AI insights, notifications, webhooks, MFA, SSO (SAML + OAuth2), organisations, tenants, users, roles, exchange rates, import jobs, bulk operations, health monitoring, predictive maintenance, compliance controls (generic + BOG directive), and billing.
- Multi-tenancy via `TenantFilter` + `X-Organisation-Id` header + JWT org-id claim (correctly reads org from token, not from header alone — good).
- Security: JWT with blacklist (`JwtBlacklist`), RBAC with `@PreAuthorize`, permission cache, startup secret validator, webhook signature validator (Paystack HMAC), rate limiting (Bucket4j), SAML2 + OAuth2 resource server, sensitive-data log filter, CORS with explicit allowed origins.
- Observability: Spring Actuator, Sentry integration, request correlation IDs, custom health indicators.
- Performance: Virtual threads enabled (Java 21 Loom), HikariCP tuned, Hibernate batching and 2nd-level fetch sizing configured, Redis cache type.
- Auditing: Hibernate Envers for row history + a separate `AuditEvent` stream for RBAC-sensitive actions.
- Standalone mode: `APP_MODE=standalone` toggles a `LicenseGuardFilter` (conditional bean — inert in cloud mode, which is the right pattern).
- Storage: pluggable (`FileStorageService` with S3 and in-memory implementations; per-org override supported).
- AI: pluggable provider (Groq/Anthropic/Ollama) for insights + chat — Ollama option is a credible offline story.
- DB: PostgreSQL 16, Flyway, `ddl-auto=validate` in prod (enforces migration discipline once you actually have migrations).

**What is weak or missing:**

- **Tests: 13 test files for 417 Java files (~3 % ratio).** Four integration/controller tests across a 45-controller surface is not enough to ship. Industry baseline for a paid B2B SaaS at this size is 40–60 % line coverage plus contract tests for all money-touching and webhook paths.
- **Flyway migration history is almost empty (6 files).** The data model is driven almost entirely by JPA annotations + `ddl-auto=update` in dev. You will not be able to ship safe schema changes to paying customers without baselining the full schema into Flyway first.
- **No explicit data-residency controls.** S3 endpoint is configurable (good — can point at a Ghanaian / African S3-compatible provider), but there is no tenant-level residency flag and no documentation on "your data stays in Accra / Lagos / Johannesburg."
- **No GDPR / Ghana DPA surface area.** No endpoints for data subject access requests (DSAR), no consent records, no retention-policy engine, no `dataDeletedAt` flow for users, no DPO contact field on `Organisation`.
- **No bulk export for an entire tenant's data.** Customers must be able to download everything they gave you — both as a selling point and as a DPA obligation.
- **API versioning is `/v1` only.** `ApiVersioningConfig` exists but there is no `/v2` contract. Fine for launch; flag for the first breaking change.
- **Swagger/OpenAPI is served on the backend** — good in dev, disabled in prod; confirm before launch.
- **Health monitoring endpoints** include Redis, DB, and license server — confirm Grafana / Uptime Robot / Better Stack dashboards exist before you announce.

### 2.2 Web Customer Portal — `assetiq-customer-portal/` (Next.js 14)

Small, focused Next.js app whose only job is: sell license keys for the standalone edition via Paystack, email the signed JWT key, and let customers renew.

**Strong:**

- Paystack `/transaction/initialize` with signed HMAC reference, webhook handler, rate-limited checkout (5/IP/min), resend email delivery, renewal flow end-to-end. Unit tests exist for `checkout`, `plans`, `rate-limit`, `verify` — a rare bright spot.
- Clear plan model (`STARTER / PROFESSIONAL / ENTERPRISE`) with 365-day duration and feature list.

**Weak:**

- **Currency is hard-coded to NGN and the displays are in ₦.** This is the single most customer-facing bug. Ghanaian buyers will think they are on the wrong product.
- **No live plan/pricing page for public browsing.** `/` exists but the marketing story (why AssetIQ, who it is for, screenshots, case studies, compliance badges) is not in the repo.
- **No MoMo / USSD / bank transfer copy.** Paystack supports MTN MoMo, Telecel Cash, AirtelTigo Money, bank, USSD, and card for Ghana — customers need to see this on the checkout page.
- **No legal pages (Terms, Privacy, Refund, Data Processing Addendum, DPO contact).** Mandatory for DPA compliance and almost every corporate procurement form will ask for these.
- **Resend is the only email channel** — good; confirm the sending domain (`portal.assetiq.io`) has DKIM/DMARC/SPF configured before launch or emails will be junked by Gmail/Microsoft 365 for Ghanaian corporates.

### 2.3 Standalone / Self-Hosted — `assetiq-standalone/`

Docker Compose bundle: Postgres + License Server + Backend + Frontend + Nginx. `STANDALONE_PLAN.md` declares all 6 phases complete; `LAUNCH_CHECKLIST.md` is comprehensive; `QA_CHECKLIST.md` covers 10 categories including security, grace period, and upgrade testing.

**Strong:**

- RSA-2048 signing keys, license JWT validation, grace periods, scheduled remote validation with offline tolerance, read-only mode on expiry, renewal via the customer portal — all the pieces of a well-designed license system.
- Installer scripts: `setup-standalone.sh`, `generate-rsa-keys.sh`, `generate-self-signed-cert.sh`, `backup.sh`, `smoke-test.sh`, `health-check.sh`. The operational maturity is unusual for a product at this stage.
- Internal services have no host port bindings (good — only nginx is public).
- Security audit section of the QA checklist covers JWT forgery, replay, admin-key enforcement, and Docker image hygiene.

**Weak:**

- **`docker-compose.standalone.yml` builds from `../../assetiq-license-server` and `../../Enterprise-Asset-manager-Frontend`, neither of which is present in the three folders audited.** Until those two sibling repos are co-located and buildable, the stack does not come up. This is the first thing to verify before any pilot.
- **No signed binary installers.** Customers in banks, mines, and government will expect either a signed Windows MSI for the optional desktop, signed APKs/AABs for Android, and checksummed bundle zips for Linux/macOS. Only the zip checksum is in the checklist.
- **No clustering / HA guidance.** Single-node docker-compose is fine for a 50-user NGO; not fine for a 2,000-seat bank deployment. You should have a documented "scale-up path" from single-node compose → external Postgres + Redis + multi-replica backend → Kubernetes helm chart.
- **Backup script retains 30 days local dumps only.** Good default but there's no documented off-site/S3 upload step and no restore rehearsal in the checklist.
- **Self-signed TLS as the default install** means every first-time visit shows the scary "Not Private" browser warning. Ship a Let's Encrypt automation path (certbot container or a pre-wired Caddy).
- **No trial / time-limited demo key issuance.** Standalone only sells via Paystack. You need a 30-day demo key that sales can issue without charging a card — this removes a huge amount of friction in enterprise sales cycles.

### 2.4 Mobile App — `Enterprise-Asset-Mobile/` (Expo 54, React Native 0.81, React 19)

**Strong:**

- All 43 backend APIs already have typed service wrappers (`src/services/*`). API coverage is effectively 100 %.
- Auth (login, MFA, SSO, register-tenant, forgot/reset password) is production-grade.
- Zustand + React Query + AsyncStorage persister configured; useful in low-bandwidth scenarios.
- NativeWind design system, shared UI (`FormSheet`, `PaginatedList`, `PermissionGate`, `EntityCard`, `StatCard`), Expo Router v6 file-based navigation, Sentry, expo-secure-store for JWT.
- QR scanner screen is wired to the backend's QR-payload lookup endpoint.
- EAS build profiles (dev/staging/production) with bundle-id suffixing per environment.

**Weak (from the plan's own self-assessment):**

- **Most create/edit forms are missing or read-only placeholders.** A mobile app whose main verb is "look at things" rather than "take field action" will not be valued by a maintenance technician or an auditor.
- **No dashboard charts** — the web has analytics, mobile has raw numbers.
- **Analytics screen doesn't exist** on mobile.
- **Approval/workflow actions are missing**: approve a purchase order, approve an asset transfer, complete a work order, close a maintenance task. These are the *killer* mobile features for a CEO or department head.
- **No push notifications are actually wired.** The hook `useBootstrapSession` is "supposed" to register with `expo-notifications` but nothing receives the token on the backend side — no `push_tokens` endpoint is visible.
- **No offline queue / background sync.** Grepping for "offline" in `src/` returns zero hits. For Ghana, Nigeria, rural Kenya, remote mines, field technicians, or any 3G-first user, this is the single largest missing feature.
- **Bulk operations, filter/sort controls, location map view, and QR rendering** are flagged as pending.
- **Tests: 12 test files** — thin but targeted. The harder problem is the missing feature surface, not the test count.
- **No App Store / Play Store listing assets yet visible** (screenshots, descriptions, privacy policy link, data-safety form answers). Allow 2–3 weeks for review and copy.

### 2.5 Desktop — `Enterprise-Asset-manager-desktop-app/` (Electron + React + Vite)

55 page components under `renderer/src/pages/` mirror the web frontend. The main process uses `safeStorage` to encrypt the session at rest, `contextIsolation: true`, and IPC handlers for every API call.

**Strong:**

- Security posture is correct: tokens live in the main process, not in `localStorage`; renderer gets sanitized user info only.
- Feature surface (55 pages) is the most complete of any client — actually more pages than mobile.

**Weak — in fact the desktop is the weakest shipping surface:**

- **No `electron-builder` / `electron-forge` / packaging config anywhere.** There are no Windows `.exe`/`.msi`, macOS `.dmg`/`.pkg` (signed & notarized), or Linux `.AppImage`/`.deb` builds. Customers cannot install it.
- **No auto-update (`electron-updater`) configured.** Shipping an unpatched Electron installer in 2026 is a CVE liability — Electron 41.x is currently only supported until approximately Electron 44 lands.
- **No tests at all** outside `node_modules`.
- **No offline / local-first store.** The desktop app's reason to exist (offline operation, barcode/label printer integration, bulk import from a USB drive, kiosk mode for stock-taking days) is not implemented — it just wraps HTTPS calls to the same backend the web and mobile use.
- **No printer / peripheral integration** — for an asset-management desktop to pay its rent it should print QR labels to Zebra / Brother / Dymo printers, read handheld scanners as HID, and drive barcode label design. None of this is present.
- **Routing / deep links / Windows notifications / tray icon / taskbar badge / "minimize to tray"** — none of the Windows UX affordances a native desktop user expects.

In the current state the desktop adds no value over the web. Unless you have a concrete customer commitment that requires desktop, **I would defer or cancel the desktop surface** and redirect that engineering into mobile offline + web polish.

---

## 3. Ghana / Africa Market Fit

The BOG ICT Directive compliance module already in the codebase is a genuinely differentiating asset for the Ghana market. Everything else needs localisation work.

### 3.1 Payments

| Requirement | Status | Action |
|---|---|---|
| Paystack integration (card, bank) | Present | Keep |
| Paystack `channels: ["card","mobile_money","bank","ussd"]` explicitly requested in `initializeTransaction` payload | **Missing** | Add to `BillingServiceImpl#initializeCheckout` and customer portal `initTransaction` |
| MoMo copy on checkout page ("Pay with MTN MoMo / Telecel Cash / AirtelTigo Money") | **Missing** | Add to `assetiq-customer-portal/src/app/checkout/page.tsx` |
| Flutterwave as secondary gateway (for Kenya M-Pesa, South Africa, pan-African fallback) | **Missing** | Add `FlutterwaveGatewayService` behind a feature flag once Paystack is stable |
| Bank transfer / pro-forma invoice flow for enterprise procurement (government/bank buyers who cannot pay with a card) | **Missing** | Add a "Request invoice" option to the pricing page; manual reconciliation on Paystack's Bank Transfer channel covers 80 % of this |
| GHS pricing with rate card pegged to a stable currency (USD backing, displayed in GHS) | **Missing** | See section 4 |
| Per-org currency preference stored on `Organisation` | **Partial** (code has `currency = "USD"` defaults scattered across 8 entities) | Add `Organisation.billingCurrency`, default GHS for Ghana tenants, NGN for Nigeria, KES for Kenya, ZAR for SA |

### 3.2 Compliance & Legal

| Framework / law | Status | Action |
|---|---|---|
| Bank of Ghana ICT Directive tracking (`BogControl`) | **Present** — differentiator | Market it: "BOG ICT Directive out-of-the-box" is a real sales line to every licensed bank and NBFI |
| ISO 27001, SOC 2, PCI DSS, ICS | **Present** (`ComplianceFramework` enum) | Add policy templates, pre-seeded control libraries |
| Ghana Data Protection Act (Act 843 of 2012) | **Missing** | Add DPO fields on `Organisation`, consent records, DSAR endpoints, retention policies, `purposeOfProcessing` on personal-data fields |
| Cybersecurity Authority registration (licensable CIIs) | **Missing** | Add evidence storage + export for CSA-mandated incident reporting |
| Nigeria NDPR, Kenya DPA 2019, South Africa POPIA | **Missing** | Same scaffolding as Ghana DPA, different controller file per jurisdiction |
| Public Procurement Authority (Ghana) e-tender format exports | **Missing** | Low priority; only matters if you want government procurement, in which case it is a requirement |

### 3.3 Connectivity & Performance

Ghanaian infrastructure reality: the office has fibre, the warehouse has 4G, the regional depot has 3G on a good day, and the field tech at the cocoa shed has one bar of EDGE. Your mobile story must handle that.

| Concern | Status | Action |
|---|---|---|
| Offline asset create / scan / update with background sync | **Missing** | Add an outbox pattern in the mobile app: writes go into an AsyncStorage queue, flushed when `@react-native-community/netinfo` reports connectivity |
| Low-bandwidth images (compress at capture, progressive upload) | **Missing** | Use `expo-image-manipulator` to downscale to 1280 px / 60 % JPEG before upload |
| Bundle size on the mobile app | Unknown | Target < 30 MB APK; run `eas build --profile staging` and check |
| Backend cold-start time | Likely OK (Spring Boot 3.3 + Loom) | Measure from Accra on a fresh container |
| CDN for the web + customer portal | **Missing in repo** | Cloudflare (free tier) or Railway's edge |

### 3.4 Language & UX

| Concern | Status | Action |
|---|---|---|
| English (Ghanaian / pan-African business English) | **Default** | Fine — no i18n needed for launch |
| French (Côte d'Ivoire, Senegal, Francophone expansion) | Not required at launch | Defer until you have paying francophone customers |
| Currency & date formats | Mixed USD / NGN / GHS | Consolidate; see §4.1 |
| Local sector terminology (e.g. "kiosks", "containerised gen-sets", "school furniture batches" for government buyers) | Neutral | Fine for horizontal asset management; add sector packs later |
| Demo data that looks Ghanaian (names, addresses, Cedi amounts) | **DevDataSeeder exists** — unverified content | Audit `DevDataSeeder.java`; if it seeds "Acme Corp / 123 Main St / $1,000" replace with "Kwabenya Depot / East Legon / GHS 12,500" |

### 3.5 Go-to-Market in Ghana

**Target segments, ranked by closability in months 1–6:**

1. **Tier-2 private schools and universities** with 500–5,000 assets (computers, projectors, furniture, lab equipment). Short sales cycle, ITAM pain is real, decision-maker is the bursar or IT head.
2. **Fintechs, payment aggregators, MFIs** — any BoG-supervised entity that must report on the ICT Directive. Your BOG compliance module *is* the product.
3. **NGOs & donor-funded programmes** (USAID, GIZ, World Bank) with laptop/vehicle fleets across regional offices. Strong self-hosted story fits their data-residency requirements.
4. **Tier-2 banks, rural & community banks, savings & loans** — same BOG story, smaller deal sizes, less procurement theatre than tier-1.
5. **Mines & quarries** — high asset values, harsh field conditions, they genuinely need the mobile app with offline support. Don't target until mobile offline is shipped.
6. **Government ministries / MMDAs** — slowest cycle, highest prize, usually gated by PPA. Go here only after you have 3+ logos.

**Pricing suggestion (GHS annual, ex-VAT):**

| Tier | Price (GHS / yr) | USD equiv @ 15 GHS | Assets | Users | Right for |
|---|---|---|---|---|---|
| Starter | 3,600 | ~$240 | 250 | 10 | small schools, NGOs, SME back-office |
| Growth | 14,400 | ~$960 | 2,000 | 50 | mid-size schools, fintechs, rural banks |
| Business | 36,000 | ~$2,400 | 10,000 | 250 | Tier-2 banks, medium mines, large NGOs |
| Enterprise | custom (floor 120,000 GHS / yr) | $8,000+ | unlimited | unlimited | Tier-1 banks, telcos, mining houses, government |

Rationale: the current portal prices (₦120k / ₦360k / ₦1,200,000 ≈ USD 80 / 240 / 800) are too low for the feature surface and will leave money on the table. Ghanaian corporates benchmark against SAP EAM, IBM Maximo, UpKeep, Asset Panda — all of which charge USD 35–150 / user / month. You can credibly sit at USD 5–10 / user / month.

---

## 4. Top 15 Launch-Blocking Issues (ranked)

1. **Currency fragmentation (NGN portal / GHS backend default / USD in code).** Pick one default per tenant, let the user pick at tenant-creation time, store it on the `Organisation`, and remove every `String currency = "USD"` hard-coded default in favour of reading the org's preference. *[2 – 3 days]*

2. **Mobile money not first-class in checkout.** Add `"channels":["card","mobile_money","bank","ussd"]` to the Paystack init payload in both `BillingServiceImpl` and the customer portal; add MoMo badges and copy on the pricing/checkout pages. *[1 day]*

3. **Standalone compose references a missing `Enterprise-Asset-manager-Frontend/` sibling repo.** Either include that repo in the audit set, switch to a pre-built Docker image hosted on GHCR/DockerHub, or document the build order. Until resolved, nobody can actually install the standalone edition. *[0.5 – 2 days depending on which option]*

4. **Flyway migration history doesn't describe the real schema.** Baseline the current schema into a single `V7__baseline.sql` (generated from `pg_dump -s`) and make all future changes go through Flyway only. Flip `HIBERNATE_DDL_AUTO` to `validate` in every non-dev env. *[1 – 2 days]*

5. **Backend test coverage is ~3 %.** Target 40 % line coverage, with 100 % of money-touching paths (BillingService, PaystackGatewayService, LicenseGuardFilter, webhook validator, tenant filter, JWT filter) covered by both unit and integration tests. *[2 – 3 weeks]*

6. **No Ghana DPA compliance surface.** Ship: DPO contact on `Organisation`; DSAR endpoint (`GET /api/v1/privacy/export`, `DELETE /api/v1/privacy/me`); retention policy engine at the category level; cookie/consent banner on the portal and web. *[1 – 2 weeks]*

7. **Mobile app is ~40 % complete with no offline sync and no push.** Ship the offline outbox (AsyncStorage queue + NetInfo-driven flush), wire Expo push tokens to a backend `push_tokens` table, and complete Phases 2–4 of `MOBILE_IMPLEMENTATION_PLAN.md` (dashboard charts, asset CRUD forms, operations approvals). Defer analytics and bulk ops to v1.1. *[4 – 6 weeks]*

8. **Desktop has no installer and no offline value.** Decide: deprecate or productise. If deprecate, redirect work. If productise, add `electron-builder`, code signing (Windows + Apple notarization), `electron-updater`, a local SQLite cache (via `better-sqlite3`), and Zebra/Brother printer drivers. *[4 weeks productising; 0 deprecating]*

9. **No public pricing page / marketing site / demo request flow.** The customer portal's `/` page is a placeholder. Ship `assetiq.io` with hero, features, screenshots, BOG-compliance callout, pricing, testimonials (even pilot ones), "Request demo" form, docs, trust/security page with your sub-processors list. *[1 – 2 weeks]*

10. **No signed terms / privacy / DPA templates** on the portal or the in-app. Corporate procurement departments will never sign without these. Draft with a Ghanaian-qualified lawyer; the DPA addendum alone is non-negotiable for any bank or insurance customer. *[2 weeks including legal review]*

11. **No 30-day trial / demo license key.** Sales friction at its highest. Add a "Request trial" route that issues a signed 30-day key without payment, rate-limited per email and per IP. *[3 days]*

12. **No live dashboards / uptime page / status.assetiq.io.** Corporate buyers will ask. Use Better Stack, StatusPage.io, or a free Cloudflare-hosted status page fed by the backend `/actuator/health` endpoint. *[2 days]*

13. **No tenant-level data export / one-click "give me everything you have on me".** A DPA obligation and also a trust signal. Add `POST /api/v1/tenants/{id}/export` → async job → signed S3 URL → zip of CSVs. *[4 – 5 days]*

14. **No load test / capacity plan.** Before first paying customer, run k6 / Gatling against `/api/v1/assets` and `/api/v1/dashboard/*` at 50, 200, 500 concurrent users and publish the numbers. *[3 days]*

15. **No localised demo data.** Replace Acme / dollar / US-address defaults in `DevDataSeeder.java` with Ghanaian names, cedis, Accra/Kumasi/Tamale addresses, BOG directive samples, KNUST/Legon-style departments. First-impression detail that matters more than you'd think. *[1 day]*

---

## 5. 30 / 60 / 90-Day Plan

**Week 1–2 (blockers, parallelisable):**
- Currency unification (#1) + MoMo channels (#2)
- Resolve standalone bundle dependency (#3)
- Flyway baseline migration (#4)
- 30-day demo key (#11)
- Localised demo data (#15)
- Customer portal: GHS pricing, MoMo copy, legal page placeholders (#1, #10)

**Week 3–6 (must-have features):**
- DPA compliance surface (#6)
- Tenant-level export (#13)
- Backend test coverage ramp to 40 % on money paths (#5)
- Mobile: push notifications + dashboard charts + asset CRUD forms + operations approvals (#7 partial)
- Marketing site v1 at assetiq.io + pricing page + demo form (#9)

**Week 7–12 (launch readiness):**
- Mobile offline outbox + store listings + beta testflight (#7 complete)
- Load test + publish numbers + status page (#12, #14)
- Standalone: signed bundle on a downloads page, 30-day trial key, Let's Encrypt automation
- First 2–3 pilot organisations: 1 bank, 1 school/university, 1 NGO
- Desktop: deprecate or commit — no middle ground (#8)
- Final security review; DPA legal pages shipped (#10)

**Day 90 — launch criteria:**
- All three surfaces (web, mobile, standalone) buildable with one command
- At least one paying pilot per segment (bank + school/NGO)
- Backend test coverage ≥ 40 %, zero critical Flyway debt, status page green for 30 consecutive days
- Ghana-first pricing page with GHS rates and MoMo checkout
- DPA-ready legal pages and DSAR endpoints live

---

## 6. Risk Register (top 10)

| # | Risk | Probability | Impact | Mitigation |
|---|---|---|---|---|
| 1 | Currency mixup charges a Ghanaian customer in Naira and Paystack settles in NGN | High | Critical | Unified per-tenant currency (#1 above) |
| 2 | Standalone bundle doesn't build for first pilot | High | Critical | Resolve sibling-repo dependency before any pilot demo (#3) |
| 3 | Bank procurement rejects AssetIQ for lack of DPA addendum | High | High | Legal pages + DPO fields (#6, #10) |
| 4 | Unsigned Electron app triggers Windows SmartScreen / macOS Gatekeeper warnings | Certain if shipped as-is | High | Sign + notarize or deprecate desktop (#8) |
| 5 | Paystack webhook HMAC mismatch silently drops license activations | Medium | Critical | Add a replay tool + Sentry alert on webhook 401s; rotate secrets per env |
| 6 | Schema drift between Hibernate and Postgres in production | Medium | High | Baseline Flyway + forbid `ddl-auto=update` outside dev (#4) |
| 7 | Mobile app rejected by Apple for missing privacy manifest / data-safety form | Medium | Medium | Fill in Apple Privacy Manifest and Google Play Data Safety forms during Phase 7 |
| 8 | Customer's fingerprint limit hits 2 on standalone and sales escalates | Low-medium | Medium | Raise default `LICENSE_ABUSE_MAX_FINGERPRINTS` to 3; self-serve reset in portal |
| 9 | MoMo fluctuations (USSD outages during month-end) block renewals | Medium | Medium | 14- to 30-day grace period already supported; keep it visible |
| 10 | Competitor (UpKeep, Asset Panda, SAP EAM) enters Ghana channel | Low (next 12 months) | Medium | Anchor on BOG module + self-hosted + local support; none of the internationals will self-host in Ghana |

---

## 7. What AssetIQ Already Has That Competitors Do Not

Be explicit about these in every sales conversation:

- **BOG ICT Directive module out-of-the-box.** No international EAM has this.
- **Self-hosted edition with paid license keys, grace period, offline validation, and BYO-storage.** Most competitors are either pure SaaS (UpKeep, Asset Panda) or require expensive enterprise deals (Maximo, SAP EAM). You have a credible middle ground that fits Ghanaian banks' data-residency preferences.
- **Pluggable AI** (Groq / Anthropic / Ollama). Ollama means "runs fully offline in the bank's own data centre." That is a unique sentence in African EAM marketing today.
- **Full SAML + OAuth2 SSO** on a product at this stage is ahead of the market.
- **Multi-tenant with proper tenant-scoped authorisation** (JWT org claim + header cross-check) — many African SaaS startups get this wrong.
- **Hibernate Envers row-history + separate AuditEvent stream for RBAC-sensitive operations.** Auditor-friendly out of the box.

---

## 8. What To Cut Before Launch

A lean launch is a cheaper launch. Seriously consider cutting or deferring:

- **Desktop app** — unless you have a pre-sold customer who asked for it by name.
- **Network discovery** (`NetworkDiscoveryController`) — nice-to-have, high support burden, low willingness-to-pay in SMEs.
- **Cloud asset management** (`CloudAssetController`) — different buyer than IT asset management; different sales motion.
- **AI Chat / Insights** as a paid feature — keep it free / Groq-backed for now; don't let it become a support headache in v1.
- **Vendor performance reviews** — most Ghanaian SMEs do not have a procurement maturity high enough to pay for this.

Ship narrow, ship proud: assets + maintenance + transfers + disposals + departments/locations + users/roles + basic analytics + BOG compliance + mobile scan + self-hosted edition.

---

## 9. Recommended File-Level Changes (code-level)

### Backend

- `src/main/java/com/assetiq/services/impl/BillingServiceImpl.java` — add `payload.put("channels", List.of("card","mobile_money","bank","ussd"));` around line 115 and expose a config property `app.billing.paystack.channels` with that default.
- `src/main/java/com/assetiq/config/BillingPlanSeeder.java` — parameterise default currency per plan instead of the single `${app.billing.default-currency:GHS}` feed so Starter can be GHS while Enterprise is USD if required.
- `src/main/java/com/assetiq/models/Organisation.java` — add `billingCurrency`, `dataResidencyRegion`, `dpoContactEmail`, `dpoContactPhone` columns.
- `src/main/resources/db/migration/V7__baseline.sql` — one-time baseline from `pg_dump -s`.
- `src/main/resources/db/migration/V8__organisation_locale_dpa_fields.sql` — add the four columns above.
- New controller: `src/main/java/com/assetiq/controllers/v1/PrivacyController.java` — DSAR export + delete, consent record list, retention policy CRUD.
- New service: `src/main/java/com/assetiq/services/TenantExportService.java` — async job → S3 → signed URL.
- Remove `String currency = "USD"` defaults in `AssetServiceImpl`, `PurchaseOrderServiceImpl`, `ExpenseServiceImpl`, `ContractServiceImpl`, `BudgetServiceImpl`, `LeaseRecordServiceImpl` (8 occurrences). Read from `Organisation.billingCurrency` instead.
- `src/main/java/com/assetiq/config/DevDataSeeder.java` — ghanaianise demo data.
- `pom.xml` — add Pact / Spring Cloud Contract for consumer-driven contract tests; add Jacoco with 40 % line-coverage gate.

### Web customer portal

- `assetiq-customer-portal/src/lib/plans.ts` — rewrite `PLANS` with GHS prices (`priceKobo` renamed to `priceMinor`, currency per plan).
- `assetiq-customer-portal/src/lib/paystack.ts` — pass `channels: ["card","mobile_money","bank","ussd"]`.
- `assetiq-customer-portal/src/app/checkout/page.tsx` — add MoMo logos + "Pay with MTN MoMo / Telecel Cash / AirtelTigo Money" copy.
- New pages: `/pricing`, `/trial`, `/privacy`, `/terms`, `/dpa`, `/status`.
- `middleware.ts` — add a cookie consent banner.

### Mobile

- New module: `src/shared/sync/outbox.ts` — queue writes, drain on connectivity.
- New hook: `src/shared/hooks/useBackgroundSync.ts`.
- `src/shared/hooks/useBootstrapSession.ts` — register `expo-notifications` token and POST to `/api/v1/notifications/push-tokens` (new backend endpoint).
- Finish `src/features/assets/AssetFormScreen.tsx`, `MaintenanceFormScreen.tsx`, `TransferFormScreen.tsx`, `PurchaseOrderFormScreen.tsx` — all the Phase 2–4 items in `MOBILE_IMPLEMENTATION_PLAN.md`.
- `src/features/dashboard/DashboardScreen.tsx` — add 3 charts using `victory-native` (already installed).
- `app/(app)/asset/[id]/qr.tsx` — render the actual QR visually (`react-native-svg` is installed).
- Add `@react-native-community/netinfo` as a dependency.

### Desktop

- If productising: add `electron-builder` + `electron-updater`, a `build/` folder with Windows icon + code-sign cert references, `better-sqlite3` for local cache, a USB/HID bridge for barcode scanners.
- If deprecating: add a banner on `LandingPage.tsx` pointing to the web app.

### Standalone

- `assetiq-standalone/docker-compose.standalone.yml` — switch `license-server` and `frontend` from `build:` to `image: ghcr.io/yourorg/assetiq-license-server:tag` and `image: ghcr.io/yourorg/assetiq-frontend:tag`.
- `assetiq-standalone/nginx/nginx.conf` — add a Caddy alternative doc or a certbot sidecar for automatic Let's Encrypt.
- `assetiq-standalone/scripts/setup-standalone.sh` — prompt for currency / country / industry and seed defaults accordingly.

---

## 10. One-Page Summary for the Board

AssetIQ is a **broad, architecturally sound enterprise asset management platform** with three differentiating bets that map directly to the Ghanaian and broader African market: (1) a **Bank of Ghana ICT Directive compliance module**, (2) a genuinely **self-hostable paid-license edition** suited to banks, mines, and government, and (3) **pluggable AI with a local/offline (Ollama) option** suitable for data-sensitive environments.

It is **not shipping-ready today**, primarily because of three shipping inconsistencies (currency, MoMo, a missing sibling repo in the standalone bundle), a thin test baseline, no Ghana Data Protection Act surface, an incomplete mobile app, and a desktop app that cannot be installed. None of those are design flaws — all are execution gaps that a focused team can close in 60–90 days.

The recommended play is a **lean, Ghana-first, bank-and-school GTM anchored on BOG compliance and self-hosted deployment**, with a mobile-offline wedge for field ops, and deprecation (or serious commitment) of the desktop surface. At the proposed pricing (Starter GHS 3,600 / Growth GHS 14,400 / Business GHS 36,000 / Enterprise 120,000+), a 10-logo year-one result is realistic and would generate roughly **GHS 600,000 – 1,200,000 ARR** from Ghana alone, with Nigeria / Kenya / South Africa as natural follow-ups once the Ghana motion is proven.

**Recommendation: 8–12 weeks of the work laid out above, then a soft launch with 2–3 pilot logos, then a paid-GA announcement at week 16.**

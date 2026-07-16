# AssetIQ — Phased Fix & Launch Plan

**Companion to:** `ASSETIQ_MARKET_READINESS_REPORT.md`
**Plan date:** 18 April 2026
**Target soft launch:** week 12 (11 July 2026)
**Target paid GA:** week 16 (8 August 2026)
**Primary surface:** SaaS (web + mobile, hosted), Ghana-first
**Secondary surface:** Self-hosted / standalone (banks, mines, government, NGOs)

---

## 0. How to read this plan

- **Phases run in parallel where noted.** Treat them as swim-lanes, not a single waterfall.
- **Every work item has:** a goal, the specific files / endpoints / screens it touches, an acceptance criterion, an owner role, and an effort estimate in person-days.
- **"Owner" is a role, not a person.** Assign real names at kick-off: BE (backend engineer), FE-W (web frontend), FE-M (mobile frontend), FE-D (desktop), DevOps, QA, PM, Sales, Legal, Design, Marketing.
- **Minimum team shape for this plan:** 2 backend, 1 web FE, 1 mobile FE, 1 DevOps, 1 QA, 1 PM/founder, 1 design (fractional), 1 legal (fractional Ghanaian counsel), 1 sales/GTM.
- **If the team is smaller**, de-scope Phase 4 mobile to "scan + read + approve" only, cut desktop entirely, cut Phase 8, and stretch each phase by ~50%.

---

## 1. Operating model

### 1.1 Cadence

- **Daily 15-min standup**, 9:00 Accra.
- **Weekly product review**, Thursday 16:00 — go/no-go on the current phase.
- **Bi-weekly stakeholder demo**, Friday — founder, sales, 1 pilot customer on rotation.
- **One-week sprint, shipped behind feature flags.** Nothing merges to `main` without a flag unless it is a bug fix.

### 1.2 Branching & environments

- `main` → staging auto-deploy
- tagged release → production
- Three persistent environments: `dev` (ephemeral PR previews), `staging` (mirrors prod, seeded demo data), `prod`
- Feature flags via a simple `ff_` table in Postgres + a `FeatureFlagService` bean — no external dependency needed at this scale

### 1.3 Definition of Done (applies to every task in this plan)

1. Code merged, linted, typechecked.
2. Unit test(s) added or updated.
3. Integration/contract test added for anything that touches money, auth, tenants, or webhooks.
4. Feature-flagged if user-visible.
5. Observability: at minimum a structured log line + a Sentry breadcrumb on the failure path.
6. Docs updated: README, `docs/api-integration-reference.md`, or the relevant `*_PLAN.md`.
7. Demo-able in staging.
8. Reviewed by one peer (and by security lead for money/auth/tenant changes).

### 1.4 Launch gates

Each phase ends with a **gate meeting**. No phase can close while a P0 or P1 from the previous phase is still open.

- **P0**: breaks money, auth, tenancy, or customer data safety
- **P1**: blocks a pilot customer workflow
- **P2**: observable bug, no workaround
- **P3**: paper cut

---

## 2. Phase map (at a glance)

```
Weeks:       0    1   2   3   4   5   6   7   8   9   10  11  12  13  14  15  16
             |    |   |   |   |   |   |   |   |   |   |   |   |   |   |   |   |
Phase 0  ████
Phase 1       ████████
Phase 2             ████████████
Phase 3             ████████████████████████████
Phase 4                 ████████████████████████████
Phase 5                         ████████████████
Phase 6                                         ████████████
Phase 7                                                         ████████
                                                                        ↑ Paid GA
Phase 8 (post-launch, ongoing): desktop decision, regional expansion, sector packs
```

---

## 3. Phase 0 — Foundations (Week 0, 5 working days)

**Goal:** stop the bleeding, make decisions, remove blockers before they compound.

### 3.1 Decisions to make on day 1

| # | Decision | Owner | Default recommendation |
|---|---|---|---|
| D1 | Desktop: productise, deprecate, or pause? | Founder | **Pause**, revisit at week 14 |
| D2 | Primary Paystack account (currency it settles in) | Founder | **GHS** — Ghana-first |
| D3 | Cloud region for hosted SaaS | Founder + DevOps | **AWS af-south-1 (Cape Town)** or **Hetzner FSN/HEL** depending on budget; S3-compat for uploads |
| D4 | Do we ship a Nigerian price too, or Ghana-only for v1? | Founder + Sales | **Ghana only at GA**, add NGN and KES after 90 days with a paying logo in each |
| D5 | Team shape for 12 weeks | Founder | See §0 minimum shape |
| D6 | One-person legal contact for DPA review | Founder | Ghanaian data-protection counsel, retainer |

### 3.2 Work items — Week 0

| ID | Title | Files / surface | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P0-1 | Resolve standalone missing-repo dependency | `assetiq-standalone/docker-compose.standalone.yml` | DevOps | 1.5 | Either (a) `Enterprise-Asset-manager-Frontend/` is co-located and `docker compose build` succeeds on a fresh Ubuntu 22, or (b) the compose file switches `build:` → `image: ghcr.io/<org>/<name>:<tag>` and images are pushed to GHCR with checksums |
| P0-2 | Baseline Flyway migration | `src/main/resources/db/migration/V7__baseline.sql` generated from `pg_dump -s`; `application.yml` stays `ddl-auto: validate` | BE | 1 | `FLYWAY_ENABLED=true` + `ddl-auto=validate` boots cleanly on a fresh Postgres; staging re-applies the baseline without error |
| P0-3 | Set `HIBERNATE_DDL_AUTO=validate` everywhere except `dev` profile; document in README | `application-prod.yml`, `application-dev.yml`, `README.md` | BE | 0.25 | Any dev attempting to rely on `update` in staging/prod fails loudly with a startup log line |
| P0-4 | Stand up CI on GitHub Actions (or GitLab CI): lint, typecheck, unit tests, Maven build | `.github/workflows/ci.yml` per repo | DevOps | 1 | PRs to each of the 4 repos run a green CI pipeline; red PRs cannot merge |
| P0-5 | Add Jacoco with a **30 %** line-coverage gate (ramp later) | `pom.xml` | BE | 0.5 | Build fails below 30 % on the backend |
| P0-6 | Confirm Sentry DSN + sampling for all 4 surfaces (backend, portal, mobile, desktop) | `application.yml`, `next.config.ts`, `app.config.ts`, `main/main.ts` | DevOps | 0.5 | A thrown exception on each surface surfaces in the Sentry project within 60 s |
| P0-7 | Stand up `status.assetiq.io` | Better Stack or Cloudflare status page fed by `/actuator/health` | DevOps | 0.5 | Public page with 4 components (backend, portal, license server, mobile API) updates within 60 s |
| P0-8 | Create a feature-flag table + helper bean | `V7a__feature_flags.sql`, `FeatureFlagService.java`, `@FeatureFlag` annotation | BE | 1 | Can toggle a flag in SQL and see it change behaviour without redeploy |
| P0-9 | Spec repo: one-page READMEs that map every code change in §9 of the market report to a phase below | `docs/PHASED_WORK.md` | PM | 0.5 | Every item in §9 has a phase and a ticket |

### 3.3 Phase 0 exit criteria (all must be true)

- Standalone compose builds clean on a fresh machine (P0-1)
- Flyway baseline applied and `validate` gate enforced (P0-2, P0-3)
- CI green across all four repos (P0-4, P0-5)
- Sentry + status page live (P0-6, P0-7)
- Feature flags usable (P0-8)

---

## 4. Phase 1 — Payments, pricing & localization (Weeks 1–2)

**Goal:** fix the three customer-facing embarrassments: currency confusion, no MoMo, no Ghana look-and-feel.

### 4.1 Currency unification

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P1-1 | Add `Organisation.billingCurrency` (default GHS) | `Organisation.java`, `V8__organisation_billing_currency.sql`, `OrganisationDto`, `OrganisationService` | BE | 1 | New orgs get `GHS` unless overridden; legacy orgs back-filled by country heuristic |
| P1-2 | Remove all `String currency = "USD"` defaults | `AssetServiceImpl`, `PurchaseOrderServiceImpl`, `ExpenseServiceImpl`, `ContractServiceImpl`, `BudgetServiceImpl`, `LeaseRecordServiceImpl`, `AssetImportService`, `ExchangeRateServiceImpl` (8 places) | BE | 1 | Every money field defaults to `org.billingCurrency` at creation time; unit test enforces it |
| P1-3 | Per-plan currency on `SubscriptionPlan` | `SubscriptionPlan.currency` already exists; make `BillingPlanSeeder` seed a **GHS Starter / GHS Growth / GHS Business** set | BE | 0.5 | `GET /api/v1/billing/plans` for a Ghanaian org returns GHS prices |
| P1-4 | Rewrite customer portal plans | `assetiq-customer-portal/src/lib/plans.ts` (rename `priceKobo` → `priceMinor`; `currency` per plan; add `STARTER_GHS`, `GROWTH_GHS`, `BUSINESS_GHS`, `ENTERPRISE` custom) | FE-W | 1 | Portal `/` shows GHS prices and a "Nigeria / Kenya" toggle (disabled v1 but the structure is there) |
| P1-5 | Portal checkout reads the plan's currency from the signed reference | `assetiq-customer-portal/src/app/api/checkout/route.ts`, `verify/route.ts`, `lib/paystack.ts` | FE-W | 0.5 | A Ghanaian buys Starter at GHS 3,600; Paystack receipt shows GHS, not NGN |

### 4.2 Mobile money (MoMo)

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P1-6 | Add `channels` config property | `application.yml` → `app.billing.paystack.channels=card,mobile_money,bank,ussd`; `BillingServiceImpl` reads it and puts it in `payload` | BE | 0.5 | A test checkout initiates with all 4 channels available on the hosted Paystack page |
| P1-7 | Portal `lib/paystack.ts` passes the same channels | FE-W | 0.25 | Same as above on the portal checkout |
| P1-8 | MoMo badges + copy on checkout page | `assetiq-customer-portal/src/app/checkout/page.tsx` (MTN, Telecel, AirtelTigo logos; "Pay with Mobile Money, card, bank, or USSD") | FE-W + Design | 0.75 | Customers can see they can pay with MoMo before they click "Pay" |
| P1-9 | Test MoMo end-to-end with a Paystack test account | QA | QA | 0.5 | A full MoMo paystack test transaction completes and renders a GHS receipt |

### 4.3 Ghanaian look-and-feel

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P1-10 | Ghanaianise demo data | `DevDataSeeder.java` (org: "Kwabenya Depot Ltd"; locations: Accra, Kumasi, Tamale; GHS amounts; BOG sample controls) | BE | 0.5 | A fresh staging database looks like a Ghanaian company, not Acme Corp |
| P1-11 | Currency format helper on every surface | `src/shared/utils/currency.ts` (mobile), `src/lib/currency.ts` (portal), `renderer/src/lib/currency.ts` (desktop) | FE-W + FE-M | 0.5 | `GHS 12,500.00` formats identically across web, mobile, desktop |
| P1-12 | Country picker on tenant registration | `/api/v1/tenant/register` accepts `country`, `billingCurrency`; default GHS for GH, NGN for NG, KES for KE, ZAR for ZA | BE + FE-W | 1 | Self-serve signup sets currency correctly for non-Ghana orgs too |
| P1-13 | Replace NGN ₦ symbols in UI strings | grep the web portal + backend templates for `₦` and `NGN` — replace with `GHS` | FE-W | 0.25 | Zero Naira references remain at the end |

### 4.4 Marketing site v1

Shipping a small `assetiq.io` is a two-week track in parallel with the above.

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P1-14 | One-page marketing home with hero, 6 feature blocks, BOG callout, pricing, demo-request form, footer | new repo `assetiq-marketing/` (Next.js static export, Cloudflare Pages) | FE-W + Design | 4 | Live at `assetiq.io`, passes Lighthouse 90+, under 500 kB transferred on home |
| P1-15 | `/pricing` page sharing the same `plans.ts` data as the portal | same repo | FE-W | 0.5 | Single source of truth for prices |
| P1-16 | `/trial` CTA → portal `/trial` (implemented in Phase 2) | FE-W | 0.25 | CTA wired end-to-end |

### 4.5 Phase 1 exit criteria

- A Ghanaian customer can buy a Starter license with MoMo, receive a GHS receipt, and install the standalone edition (or use the hosted version) — live end to end on staging.
- Zero Naira references in the customer portal.
- `assetiq.io` live with a Ghana-first home and pricing page.
- Demo data looks Ghanaian.

---

## 5. Phase 2 — Compliance, legal & trust (Weeks 3–4)

**Goal:** Ghana Data Protection Act (Act 843 of 2012) surface, trial keys, tenant export, legal pages. This phase is what lets you sell to banks and NGOs.

### 5.1 DPA / privacy surface

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P2-1 | Add DPO + residency fields on org | `V9__org_dpa_fields.sql` (columns: `dpo_name`, `dpo_email`, `dpo_phone`, `data_residency_region`, `processing_purposes JSONB`), update `Organisation.java`, DTO, settings UI | BE + FE-W + FE-M | 2 | Settings screen captures all 4; visible in BOG compliance report |
| P2-2 | Consent records | `ConsentRecord` entity + table, captured at registration and at every policy change | BE | 2 | `GET /api/v1/privacy/consents` returns the current user's consent history |
| P2-3 | DSAR (Data Subject Access Request) export | `PrivacyController.java` → `POST /api/v1/privacy/export` → async job → signed S3 URL → email notification | BE | 3 | A user can request and download a zip of everything personal about them within 15 min |
| P2-4 | DSAR delete / "right to erasure" | `DELETE /api/v1/privacy/me` with 7-day soft-delete, emails user a confirmation code | BE | 2 | Anonymised but preserved for audit (`deleted_at`, `deleted_reason`); org admin can also request deletion for a leaver |
| P2-5 | Retention policy engine | `RetentionPolicy` entity at category level; nightly job purges per policy | BE | 2 | Audit logs expire at the plan's retention (Starter 90 d, Growth 1 y, Business/Enterprise unlimited) |
| P2-6 | Tenant-wide export | `POST /api/v1/tenants/{id}/export` → zip of all CSVs + uploaded files from the tenant's S3 prefix | BE | 2 | Org admin can download "all of our data" in one click; used both as a sales signal and as a DPA tool |

### 5.2 Legal pages

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P2-7 | Privacy Policy, Terms of Service, Refund Policy, Cookie Policy | `assetiq-marketing/app/(legal)/*` + linked from footer everywhere | Legal + FE-W | 5 | All four live, reviewed by Ghanaian counsel, dated, versioned |
| P2-8 | Data Processing Addendum (DPA) | downloadable PDF + click-through on the portal checkout | Legal + FE-W | 2 | Every new customer accepts it at signup (timestamp + IP recorded in `ConsentRecord`) |
| P2-9 | Sub-processors page | `assetiq.io/trust/subprocessors` listing Paystack, AWS, Sentry, Resend, Cloudflare | Legal + FE-W | 0.5 | Kept in sync with the real sub-processor list |
| P2-10 | Trust centre page | `assetiq.io/trust` with BOG ICT Directive support, ISO 27001 roadmap, Ghana DPA compliance, AI policy (Groq / Anthropic / Ollama) | FE-W + Design | 1 | Public URL a procurement officer can forward to their legal team |
| P2-11 | Cookie banner on portal + marketing + app | `components/CookieBanner.tsx` per repo | FE-W | 1 | Clear "Accept / Necessary only" choice; choice persists; consent recorded server-side for logged-in users |

### 5.3 Trial & demo keys

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P2-12 | 30-day trial key issuance | `assetiq-customer-portal/src/app/trial/page.tsx` + `/api/trial/route.ts` + license server endpoint | BE + FE-W | 2 | Prospect enters email + org name, receives a 30-day signed key, rate-limited (1 / email / 30 d, 3 / IP / day) |
| P2-13 | In-app trial banner with days remaining | mobile + web `LicenseBanner.tsx` | FE-W + FE-M | 1 | Banner visible during trial, converts to "Buy now" CTA at 7 days remaining |

### 5.4 Phase 2 exit criteria

- A DPO can request a DSAR export and get it within 15 min.
- A prospect can request a 30-day trial key with no credit card and actually use the standalone edition.
- All four legal pages live, reviewed by Ghanaian counsel.
- Trust centre publicly linked from assetiq.io and the portal footer.

---

## 6. Phase 3 — Hardening, testing & observability (Weeks 3–8, parallel)

**Goal:** ship code you would stake your reputation on. This phase runs alongside Phases 2 and 4.

### 6.1 Test coverage ramp

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P3-1 | 100 % coverage on money paths | tests under `src/test/java/com/assetiq/services/impl/BillingServiceImplTest.java`, `PaystackGatewayServiceTest.java`, and `controllers/v1/BillingControllerTest.java` | BE + QA | 4 | All branches: currency mismatch, amount mismatch, webhook replay, invalid HMAC, duplicate reference, plan-code mismatch, FREEMIUM rejection |
| P3-2 | 100 % coverage on auth & tenant paths | `JwtAuthenticationFilterTest`, `TenantFilterTest`, `TenantAuthorizationServiceTest`, `JwtBlacklistTest`, `MfaControllerTest`, `SsoAuthControllerTest` | BE + QA | 4 | Cross-tenant reads blocked; expired JWT rejected; MFA challenge token single-use; SAML assertion replay blocked |
| P3-3 | 100 % coverage on webhook paths | `WebhookSignatureValidatorTest`, `WebhooksControllerTest`, Paystack webhook | BE + QA | 2 | HMAC forgery, timestamp replay, wrong event type all handled |
| P3-4 | Ramp Jacoco gate from 30 % → 40 % → 50 % over 4 weeks | `pom.xml` | BE | 0.5 | Build breaks when coverage drops below the current threshold |
| P3-5 | Add consumer-driven contract tests | Spring Cloud Contract stubs, consumed by mobile + desktop + web | BE + FE-M + FE-W | 3 | Breaking a request/response shape fails CI on the consumer |
| P3-6 | E2E happy-path suite | Playwright for web portal; Detox for mobile | QA | 5 | 10 happy paths green nightly in CI |

### 6.2 Load & capacity

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P3-7 | k6 scenarios for list, dashboard, asset create | `load/` directory in backend | DevOps | 2 | Reports p50 / p95 / p99 at 50, 200, 500 concurrent users; numbers published internally |
| P3-8 | Identify & fix the top-3 slowest endpoints | BE | BE | 3 | p95 under 500 ms for all list endpoints at 200 concurrent users |
| P3-9 | Write a capacity plan doc | `docs/capacity-plan.md` | DevOps | 1 | Documented: RPS per tier, scale-up triggers, failover plan |

### 6.3 Security

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P3-10 | OWASP ZAP baseline scan in CI | `.github/workflows/zap.yml` | DevOps | 1 | No high-severity issues on staging |
| P3-11 | Dependency scan | `dependabot.yml`, `npm audit`, `mvn dependency-check`, `trivy` in CI | DevOps | 1 | PRs for CVEs; zero critical in `main` |
| P3-12 | Threat model doc | `docs/threat-model.md` using STRIDE | BE lead | 2 | Signed off by founder |
| P3-13 | Rotate and store secrets in AWS Secrets Manager / Doppler / sops | all envs | DevOps | 2 | No secrets in `.env` files checked into a repo |
| P3-14 | External pen test (light) before paid GA | vendor of choice | DevOps + Founder | 3 (vendor time) | No critical findings; all medium findings tracked & remediated before GA |

### 6.4 Observability

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P3-15 | Structured JSON logging (logback-encoder) | `logback-spring.xml` | BE | 0.5 | Logs shippable to Loki / CloudWatch / Papertrail |
| P3-16 | Metrics to Prometheus / Grafana (Actuator already on) | `application.yml`, `prometheus.yml` | DevOps | 1 | Dashboard: RPS, error rate, p95 latency, DB pool, queue depth, per-tenant RPS |
| P3-17 | Alert rules | Grafana / BetterStack | DevOps | 1 | Paging alerts for: error-rate > 2 %, p95 > 2 s, DB pool exhaustion, license server down, Paystack webhook failures |
| P3-18 | Runbook | `docs/runbooks/*.md` | DevOps + BE | 2 | 5 runbooks: DB outage, license server outage, Paystack webhook failure, tenant data leak, on-call escalation |

### 6.5 Phase 3 exit criteria

- Backend Jacoco line coverage ≥ 50 %, money + auth + webhook paths at 100 %.
- k6 numbers documented and all list endpoints < 500 ms p95 at 200 RPS.
- External pen test run; zero criticals remaining.
- Sentry + Grafana + status page together show one coherent picture.

---

## 7. Phase 4 — Mobile to production (Weeks 5–10)

**Goal:** finish the mobile app to a state where a Ghanaian field technician can open it on the bus, scan five assets, update their location, and approve a transfer — all while offline, and have it sync when they get back to the office Wi-Fi.

This phase maps directly onto the existing `MOBILE_IMPLEMENTATION_PLAN.md` phases 2–7.

### 7.1 Week 5 — Offline foundations

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P4-1 | Outbox pattern | `src/shared/sync/outbox.ts`, `src/shared/sync/types.ts` | FE-M | 2 | Every mutating service wraps itself in `outbox.enqueue(op)`; failed calls retry with exponential backoff |
| P4-2 | Network awareness | add `@react-native-community/netinfo`; `useNetworkStatus` hook; banner when offline | FE-M | 0.5 | UI reflects connectivity accurately on iOS + Android |
| P4-3 | Background sync | when connectivity returns, drain the outbox; reconcile via ETags / `updated_at` | FE-M | 2 | A user can go offline, create 3 assets, return online, and see all 3 in the cloud within 30 s |
| P4-4 | Conflict UI | show the user when the server rejected their change (stale, forbidden, validation) | FE-M | 1 | No silent data loss |

### 7.2 Week 5–6 — Push & dashboard

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P4-5 | Push tokens endpoint | `POST /api/v1/notifications/push-tokens`, `DELETE /api/v1/notifications/push-tokens/{id}`, `push_tokens` table | BE | 1 | Tokens registered per device, revoked on logout |
| P4-6 | Expo notifications wiring | `useBootstrapSession` registers token; tapping a notification deep-links to the right screen | FE-M | 1.5 | End-to-end: backend sends "Maintenance due today" → device vibrates → taps → lands on maintenance/[id] |
| P4-7 | Backend notification dispatch | `NotificationService` learns to call Expo push API (or FCM/APNs direct) | BE | 1.5 | Stored notifications mirror push dispatches; no double-send |
| P4-8 | Dashboard charts | `src/features/dashboard/DashboardScreen.tsx` using `victory-native` (already installed) | FE-M + Design | 2 | 3 charts: assets-by-status donut, assets-by-department bar, 30-day maintenance-completions trend |

### 7.3 Weeks 6–8 — CRUD forms

From `MOBILE_IMPLEMENTATION_PLAN.md` phase 3–5.

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P4-9 | Asset create / edit form | `asset/new.tsx`, `asset/[id]/edit.tsx` + Zod schema | FE-M | 3 | Full form with category, department, location, status, condition, QR, images |
| P4-10 | Maintenance create / complete | `maintenance/new`, `maintenance/[id]/complete` | FE-M | 2 | Tech can schedule, start, complete, attach photos |
| P4-11 | Transfer request / approve / reject | `transfer/new`, `transfer/[id]` with approve / reject buttons | FE-M | 2 | Approver gets a push; taps; approves; asset location updates |
| P4-12 | Purchase order approve / reject | `purchase-order/[id]` | FE-M | 1.5 | Same pattern as transfer |
| P4-13 | Disposal + audit flows | `disposal/new`, `audit/new` | FE-M | 2 | Basic parity with web |
| P4-14 | QR render | `asset/[id]/qr.tsx` using `react-native-svg` + QR library | FE-M | 1 | Printable, scannable from 30 cm, includes asset tag + org slug |
| P4-15 | Filter / sort sheet | `FilterSheet.tsx` reused across assets, maintenance, POs | FE-M + Design | 2 | Status chips, date range, text search, sort |
| P4-16 | Bulk operations (stretch) | multi-select toolbar on asset list | FE-M | 2 | Bulk status change; defer export to v1.1 |

### 7.4 Week 9 — Polish & store listings

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P4-17 | Apple Privacy Manifest + Google Data Safety | `PrivacyInfo.xcprivacy`, Play console | FE-M + Legal | 1 | Submission passes both stores |
| P4-18 | Store screenshots, descriptions | Design + Marketing | Design + Mkt | 3 | 5 screenshots per locale per store; copy reviewed |
| P4-19 | TestFlight + Play internal track | EAS build profile | DevOps | 1 | 10 beta testers installed; feedback tracked |
| P4-20 | Beta acceptance tests | QA | QA | 3 | All core flows green on iPhone 11, iPhone 15 Pro, Android 12, Android 14 |

### 7.5 Week 10 — Submit

- **P4-21**: Submit to App Store + Play Store (release train set up for weekly cadence) — DevOps — 1d — app in review

### 7.6 Phase 4 exit criteria

- Mobile app installable from TestFlight and Play Internal; submitted to both stores.
- Offline outbox verified with a 2-hour airplane-mode test.
- Push notifications working end-to-end.
- Apple / Google privacy forms submitted.
- All Phase 2–4 items from `MOBILE_IMPLEMENTATION_PLAN.md` shipped.

---

## 8. Phase 5 — Standalone / self-hosted productionisation (Weeks 7–10)

**Goal:** a customer's IT admin in Accra can receive a zip, run one script, and have AssetIQ running inside their network with automatic TLS and a backup routine.

### 8.1 Packaging & distribution

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P5-1 | Prebuilt images to GHCR | GHCR workflow per repo | DevOps | 2 | `assetiq-backend`, `assetiq-frontend`, `assetiq-license-server`, `assetiq-customer-portal` tagged with semver |
| P5-2 | Compose switches from `build:` to `image:` | `assetiq-standalone/docker-compose.standalone.yml` | DevOps | 0.5 | `docker compose up -d` pulls and starts in < 5 min on a clean VM |
| P5-3 | Signed release zip on a download page | `portal.assetiq.io/download` + SHA-256 checksum | DevOps + FE-W | 1 | Customer can download and verify integrity |
| P5-4 | Setup wizard improvements | `scripts/setup-standalone.sh` prompts for country / currency / industry and seeds defaults | DevOps + BE | 1 | First-run wizard writes the right `.env` and tenant defaults |
| P5-5 | Let's Encrypt / Caddy alternative | `nginx/certbot/` sidecar or a `Caddyfile` drop-in | DevOps | 2 | `./scripts/setup-standalone.sh --domain assets.example.com.gh` yields a valid cert without human intervention |

### 8.2 Operations

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P5-6 | Off-site backup upload | `scripts/backup.sh` gains an `--upload s3://...` path; encrypted with customer-owned key | DevOps | 2 | Backup shipped nightly to customer's own bucket; test restore documented |
| P5-7 | Restore rehearsal in CI | nightly job that tears down staging, restores from last backup, runs smoke tests | DevOps | 2 | Green for 7 days continuous before Phase 5 closes |
| P5-8 | Upgrade path doc | `docs/standalone/upgrade.md` | DevOps + BE | 1 | "From 1.0.x to 1.1.x in 3 commands" including migration preview |
| P5-9 | Health-check dashboard for customer admins | `/admin/health` in the frontend | BE + FE-W | 2 | Customer IT admin sees: versions, migrations, uptime, license expiry, storage usage |

### 8.3 Sales enablement

| ID | Title | Files | Owner | Days | Acceptance |
|---|---|---|---|---|---|
| P5-10 | Sales evaluation kit | `sales-kit/` zip: one-pager, security whitepaper, DPA addendum, install guide, BOG-mapping spreadsheet | Mkt + Legal + BE | 3 | Sales can send it on request; pilot 1 has it in hand |
| P5-11 | On-prem support SLA template | legal + ops | Legal + DevOps | 1 | Three tiers: NBD / 4 h / 1 h |

### 8.4 Phase 5 exit criteria

- `docker compose -f docker-compose.standalone.yml up -d --build=false` brings up a fully working stack on a clean Ubuntu 22 VM in under 10 min.
- Let's Encrypt / Caddy gives a valid TLS cert out of the box.
- Backup → restore verified in CI for 7 consecutive days.
- Sales kit ready.

---

## 9. Phase 6 — Pilots & go-to-market (Weeks 9–12)

**Goal:** three pilot customers running AssetIQ in production, giving you real feedback and real logos.

### 9.1 Pilot shape

| Segment | Who | What they run | Deal size | Success signal |
|---|---|---|---|---|
| Tier-2 bank / fintech / MFI | 1 | Cloud SaaS + BOG compliance module | GHS 36,000 / yr | Produces their first BOG evidence export |
| Mid-size school or university | 1 | Cloud SaaS | GHS 14,400 / yr | Tags 2,000 assets end-to-end during stock-take |
| NGO with regional offices | 1 | Self-hosted | GHS 36,000 / yr (incl. support) | Runs for 30 days with zero support tickets |

### 9.2 Pilot playbook (per pilot)

- Week -2: signed DPA, signed MSA, signed SOW
- Week -1: kickoff + data import workshop
- Week 0: go-live; daily check-in for first week
- Weeks 1–4: weekly review; ticket queue shared in Notion / Linear
- Week 4: retrospective + reference call offer

### 9.3 Work items

| ID | Title | Owner | Days | Acceptance |
|---|---|---|---|---|
| P6-1 | Pilot onboarding runbook | PM | 2 | Repeatable; used for all 3 pilots |
| P6-2 | Data import templates (CSV + xlsx) | BE + QA | 2 | A non-technical bursar can import 2,000 assets |
| P6-3 | Bulk importer UX polish | `ImportJobsController` + mobile/web UX | BE + FE-W | 3 | Error rows are fixable inline; re-upload is safe (idempotent) |
| P6-4 | Customer success tooling | tickets in Linear, shared drive per customer, monthly exec report template | PM | 1 | Founder + PM both see the same dashboard |
| P6-5 | Content marketing: 3 case studies (once pilots consent) | Mkt | 4 | Published on `assetiq.io/customers/*` |

### 9.4 Phase 6 exit criteria

- 3 pilot customers live in production, each using at least one feature daily.
- At least 1 paying customer (non-pilot) committed.
- Support ticket SLA being met.
- 1 reference call scheduled per segment.

---

## 10. Phase 7 — Paid GA launch (Weeks 13–16)

**Goal:** publicly announce, enable self-serve signup, start marketing spend, prepare to scale.

### 10.1 Final hardening

| ID | Title | Owner | Days | Acceptance |
|---|---|---|---|---|
| P7-1 | Final external pen test result remediated | BE + DevOps | 5 | No open criticals; all mediums tracked |
| P7-2 | SLA published | Legal + DevOps | 1 | 99.5 % standard, 99.9 % Enterprise |
| P7-3 | Pricing page A/B-ready | Mkt + FE-W | 2 | GA4 + Plausible wired |
| P7-4 | Help centre | Notion / HelpScout / Intercom | PM + Mkt | 5 | 30 articles covering setup, scan, roles, billing, exports, DPA, FAQ |
| P7-5 | In-app changelog / product updates feed | FE-W | 1 | Users see what shipped this week |

### 10.2 Launch mechanics

| ID | Title | Owner | Days | Acceptance |
|---|---|---|---|---|
| P7-6 | Announcement email to waitlist | Mkt | 1 | Sent |
| P7-7 | LinkedIn launch post (founder + team) | Founder + Mkt | 0.5 | Live |
| P7-8 | Product Hunt Ghana / Africa launch | Mkt | 1 | Live on a Tuesday |
| P7-9 | Press pitches: Techcabal, Ventureburn, Benjamindades, MyJoy Online, Citi Newsroom | Mkt + Founder | 3 | 1 earned-media hit |
| P7-10 | Sector webinars (banks; schools; NGOs) | Founder + Sales | 4 | 3 webinars delivered in the first 30 days |

### 10.3 Phase 7 exit criteria — "shipped"

- assetiq.io publicly announces paid GA
- At least 2 paying customers beyond pilots
- Status page green for 30 days
- Support queue under 24-hour response

---

## 11. Phase 8 — Post-launch & expansion (Weeks 17+)

### 11.1 Decisions at week 14 (reminder)

| Decision | Options | Default |
|---|---|---|
| Desktop fate | Productise / deprecate / park | **Park for 6 months**; re-evaluate if > 3 customers ask |
| Regional expansion | Nigeria / Kenya / South Africa / francophone | **Nigeria** (shared Paystack, biggest TAM) first |
| Vertical expansion | Health / education packs / Mining pack | **Education pack** (shortest sales cycle) |

### 11.2 Candidate backlog (prioritise quarterly)

- Flutterwave gateway → M-Pesa (Kenya) + richer NGN + ZAR
- Nigeria NDPR + Kenya DPA + SA POPIA compliance surfaces
- Francophone UI (French + region-specific date/currency)
- Mining pack (HSE integration, shift handover, reliability metrics)
- Education pack (school term calendar, batch tagging for furniture/books)
- Advanced analytics: TCO, depreciation forecasting, predictive maintenance ML
- Integration marketplace: Xero, QuickBooks, SAP, Microsoft 365 for SSO, Google Workspace for SSO
- Public API + developer portal
- Whitelabel / reseller programme for resellers in Lagos, Nairobi, Johannesburg
- Desktop productisation (electron-builder, auto-update, offline SQLite, Zebra printer integration) — only if signal warrants

---

## 12. Staffing & cost envelope

### 12.1 Core team (12 weeks)

| Role | FTE | Weeks | Estimated cost (USD) at market rates |
|---|---|---|---|
| Founder / PM | 1 | 12 | — |
| Backend engineer (senior) × 2 | 2 | 12 | ~ $60,000 |
| Web frontend | 1 | 12 | ~ $24,000 |
| Mobile engineer | 1 | 12 | ~ $24,000 |
| DevOps / SRE | 1 | 12 | ~ $27,000 |
| QA | 1 | 12 | ~ $18,000 |
| Product designer (fractional) | 0.4 | 12 | ~ $10,000 |
| Ghanaian data-protection counsel (fractional, retainer) | 0.1 | 12 | ~ $6,000 |
| Marketing (fractional) | 0.4 | 12 | ~ $10,000 |
| **Total personnel** | | | **~ $179,000** |

### 12.2 Infrastructure & tooling (annualised)

| Line | USD / yr |
|---|---|
| AWS / Hetzner (hosted SaaS + staging) | 6,000 |
| Paystack (transaction fees, pass-through) | 0 |
| Sentry / Better Stack / GHCR / Cloudflare | 1,200 |
| Resend / SES email | 600 |
| Expo EAS | 1,200 |
| Code-signing certificates (Apple + Windows EV) | 800 |
| External pen test (once) | 5,000 |
| Help desk (HelpScout / Intercom) | 1,200 |
| **Total annual tools** | **16,000** |

### 12.3 Legal & launch

| Line | USD one-off |
|---|---|
| Ghanaian counsel for policies + DPA | 5,000 |
| Apple + Google developer accounts | 125 |
| Marketing launch budget (ads, content, PR) | 10,000 |
| **Total launch one-off** | **15,125** |

### 12.4 Total 12-week cash envelope

**~ $200,000 USD** (personnel) **+ one-off launch ~ $15,000** **+ quarterly infra ~ $4,000** ≈ **$220,000 USD** to a credible Ghana launch.

---

## 13. Risk register (phase-aware)

| # | Risk | Phase it bites | Mitigation |
|---|---|---|---|
| 1 | Hiring is slow; team shape under-sized | 0–2 | Start recruiting during Phase 0; use fractional senior contractors as bridging |
| 2 | Paystack test MoMo flows differ from production | 1 | Book a 30-min call with Paystack Ghana support in week 1; test on a real phone, not emulator |
| 3 | Ghanaian counsel takes > 2 weeks for legal review | 2 | Send first drafts in week 2 so there is a 4-week window |
| 4 | Offline sync introduces data-consistency bugs | 4 | Server-authoritative conflict resolution + ETags; QA flag as a P0 category |
| 5 | Pilot customer pulls out | 6 | Maintain a pipeline of 5 committed pilots so losing 2 is survivable |
| 6 | Pen test surfaces a critical late | 3 / 7 | Run an early ZAP + dependency scan in Phase 3 so nothing is a surprise in Phase 7 |
| 7 | Currency migration corrupts existing amounts | 1 | Migration is additive only (`billing_currency` nullable, backfilled); no in-place conversion of existing amounts |
| 8 | Desktop scope creeps back in mid-plan | any | PM enforces the park decision; any exception requires founder sign-off |
| 9 | Mobile app store rejection | 4 | Submit to TestFlight week 9; leave 4 weeks for re-review |
| 10 | Standalone customer can't open port 443 | 5 | Document reverse-SSH / Tailscale / Cloudflare Tunnel alternatives in the install guide |
| 11 | Post-launch, no one is on-call | 7–8 | Define a 2-person on-call rotation week before GA |
| 12 | DPA regulator asks for documentation before you are ready | 2 | Keep a DPA self-assessment in a shared doc; not required to submit unprompted but be ready |

---

## 14. Traceability matrix: market report → phase

| Market report item (top-15 blockers) | Phase | IDs |
|---|---|---|
| #1 Currency fragmentation | 1 | P1-1, P1-2, P1-3, P1-4, P1-5, P1-12, P1-13 |
| #2 Mobile money not first-class | 1 | P1-6, P1-7, P1-8, P1-9 |
| #3 Standalone missing sibling repo | 0 | P0-1 |
| #4 Flyway baseline | 0 | P0-2, P0-3 |
| #5 Backend test coverage | 3 | P3-1 – P3-6 |
| #6 Ghana DPA surface | 2 | P2-1 – P2-6 |
| #7 Mobile ~40 % complete | 4 | P4-1 – P4-21 |
| #8 Desktop has no installer | 8 | (park) |
| #9 Marketing site / pricing page | 1 | P1-14, P1-15, P1-16 |
| #10 Legal / terms / privacy / DPA | 2 | P2-7 – P2-11 |
| #11 30-day trial key | 2 | P2-12, P2-13 |
| #12 Live status page | 0 | P0-7 |
| #13 Tenant-wide data export | 2 | P2-6 |
| #14 Load test | 3 | P3-7 – P3-9 |
| #15 Localised demo data | 1 | P1-10, P1-11 |

---

## 15. Launch gates (Go / No-Go checklist)

Use this checklist literally at the Phase 7 gate meeting. Every item must be ✅ to publicly announce.

### 15.1 Product

- [ ] Web portal: GHS pricing, MoMo in checkout, legal pages linked, cookie banner, trust centre
- [ ] Backend: Flyway clean, `ddl-auto=validate` in prod, coverage ≥ 50 %, money paths 100 %
- [ ] Mobile: app in App Store & Play Store (or at least in open beta); offline verified; push verified
- [ ] Standalone: one-command install on clean VM; TLS automated; backup + restore rehearsed

### 15.2 Compliance & legal

- [ ] Terms, Privacy, Refund, DPA, Cookie policy published and counsel-reviewed
- [ ] DPO contact public; DSAR endpoint live; retention policy documented
- [ ] Sub-processors list public; trust centre live
- [ ] BOG ICT Directive mapping one-pager shipped

### 15.3 Operations

- [ ] Status page green 30 days
- [ ] Alerts paging on-call within 5 min
- [ ] Runbooks for the top 5 failure modes
- [ ] Backup upload to customer-owned storage verified (self-hosted) and to vendor-owned storage verified (cloud)

### 15.4 Commercial

- [ ] 3 pilots live; 1 paying non-pilot signed; pipeline ≥ 10 qualified leads
- [ ] Sales evaluation kit ready
- [ ] Pricing page live; self-serve signup working end-to-end
- [ ] Support channel staffed; SLA published

### 15.5 Sign-off

| Role | Name | Date |
|---|---|---|
| Founder / CEO | | |
| Engineering lead | | |
| QA lead | | |
| Legal counsel | | |
| Sales lead | | |

---

## 16. One-paragraph summary

In 12 weeks, AssetIQ can move from "broad, mature, but inconsistent" to **"the asset management platform built for Ghanaian banks, schools, and NGOs — with a self-hostable edition when your data can't leave your country."** The work splits into four parallel swim-lanes: **payments & localisation** (weeks 1–2), **compliance & legal** (weeks 3–4), **testing & hardening** (weeks 3–8 in parallel), and **mobile to production** (weeks 5–10), followed by **standalone productionisation** (weeks 7–10), **pilots** (weeks 9–12) and **paid GA** (weeks 13–16). The approximate cash envelope is **~$220,000 USD**, and the non-negotiable prerequisites are: a co-located `Enterprise-Asset-manager-Frontend/` repo, Ghanaian data-protection counsel on retainer, and a founder-level commitment to park the desktop app and redirect that capacity into mobile offline. Execute on that and AssetIQ is shippable at the BOG-compliance-anchored price points proposed in the market readiness report, with a credible regional expansion ladder behind it.

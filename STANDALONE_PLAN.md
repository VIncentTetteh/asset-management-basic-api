# Enterprise Asset Manager — Standalone (On-Premise) Edition
## Product & Technical Implementation Plan

**Version:** 2.0
**Date:** April 12, 2026
**Status:** Draft

---

## 1. Overview

The Standalone Edition allows organizations to self-host the Enterprise Asset Manager on their own infrastructure. Access is controlled by a **license key** purchased from the vendor via **Paystack** — the same payment provider already used by the cloud edition. The app enforces plan limits and expiry, transitioning to **read-only mode** when a key expires, ensuring organizations retain access to their data while being prompted to renew.

This plan is designed so that **the cloud version continues to work exactly as it does today** throughout every phase of development. All standalone features are gated behind an `APP_MODE` environment variable. Cloud customers will never see license prompts, read-only banners, or any standalone-specific UI.

---

## 2. Cloud vs. Standalone — Compatibility Promise

The guiding principle across every phase:

> **Zero changes to cloud behavior. All new code is inert unless `APP_MODE=standalone`.**

| Concern | Cloud (`APP_MODE=cloud`) | Standalone (`APP_MODE=standalone`) |
|---|---|---|
| License middleware | Disabled (skipped entirely) | Active on every request |
| Plan limits | Managed by existing Paystack subscription | Managed by license key payload |
| Billing UI | Existing Paystack billing screen | Replaced by License Management page |
| Read-only mode | Never triggered | Triggered on key expiry |
| Write action gating | Existing permission gates only | License gate added on top |
| Mobile 402 handling | Not applicable | Shows "License expired" message |
| Payment provider | Paystack (existing flow) | Paystack (Customer Portal, same provider) |
| Data storage | Vendor-hosted database | Customer's own PostgreSQL |

---

## 3. Business Model & Plan Tiers

### 3.1 Plan Structure

| Feature | Starter | Professional | Enterprise |
|---|---|---|---|
| **Price (annual)** | $1,200/yr | $4,800/yr | Custom |
| **Assets** | Up to 500 | Up to 5,000 | Unlimited |
| **Users** | Up to 10 | Up to 100 | Unlimited |
| **Departments** | Up to 5 | Up to 50 | Unlimited |
| **API Access** | ❌ | ✅ | ✅ |
| **Custom Fields** | ❌ | ✅ | ✅ |
| **Analytics & TCO** | Basic | Full | Full |
| **Audit Logs** | 90 days | 1 year | Unlimited |
| **SSO / SAML** | ❌ | ❌ | ✅ |
| **Priority Support** | ❌ | Email | Dedicated |
| **License Duration** | 1 year | 1 year | Custom |
| **Grace Period** | 7 days | 14 days | 30 days |

### 3.2 License Key Lifecycle

```
Purchase (Paystack) → Key Issued → Downloaded & Installed → Active
                                                              ↓
                                               Expiry Warning (30 days out)
                                                              ↓
                                               Grace Period begins → Read-Only
                                                              ↓
                                               Renew via Customer Portal → Full Access restored
```

---

## 4. System Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                    VENDOR INFRASTRUCTURE                          │
│                                                                  │
│  ┌───────────────────┐    ┌─────────────────────────────────┐   │
│  │  Customer Portal  │    │        License Server            │   │
│  │  (separate app)   │───▶│  - Issue / revoke keys           │   │
│  │  Paystack billing │    │  - Validate key requests         │   │
│  └───────────────────┘    │  - Store org + plan metadata     │   │
│                           └─────────────────┬───────────────┘   │
└─────────────────────────────────────────────│────────────────────┘
                                              │ HTTPS (metadata only)
┌─────────────────────────────────────────────│────────────────────┐
│               CUSTOMER'S OWN SERVER         │                   │
│                                             ▼                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                  Docker Compose Stack                     │   │
│  │                                                          │   │
│  │  ┌────────────┐  ┌──────────────────┐  ┌─────────────┐  │   │
│  │  │  Frontend  │  │   Backend API    │  │  PostgreSQL  │  │   │
│  │  │ (Next.js)  │──│  (Spring Boot)   │──│  Database   │  │   │
│  │  └────────────┘  │  APP_MODE=       │  └─────────────┘  │   │
│  │                  │  standalone      │                    │   │
│  │                  └────────┬─────────┘                   │   │
│  │                           │                             │   │
│  │                  ┌────────▼─────────┐                   │   │
│  │                  │  License Guard   │                   │   │
│  │                  │  Middleware      │                   │   │
│  │                  │  (active only in │                   │   │
│  │                  │   standalone)    │                   │   │
│  │                  └──────────────────┘                   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│   Mobile App (iOS/Android) ──────▶ Customer's Backend API       │
└──────────────────────────────────────────────────────────────────┘

                    ┌──────────────────────────────┐
                    │   EXISTING CLOUD APP          │
                    │   APP_MODE=cloud              │
                    │   (completely unchanged)      │
                    └──────────────────────────────┘
```

---

## 5. License Key System

### 5.1 Key Format

License keys use **RSA-2048 asymmetric cryptography**. The License Server signs a payload with a private key. The standalone backend verifies it using a bundled public key — offline capable. Periodic online checks catch revocations.

**Signed payload (JWT-based):**
```json
{
  "iss": "assetiq-licensing",
  "sub": "org_abc123",
  "iat": 1744000000,
  "exp": 1775536000,
  "plan": "professional",
  "limits": {
    "assets": 5000,
    "users": 100,
    "departments": 50
  },
  "features": {
    "api_access": true,
    "custom_fields": true,
    "analytics": "full",
    "sso": false,
    "audit_log_days": 365
  },
  "grace_period_days": 14,
  "fingerprint": "sha256:server-fingerprint-hash"
}
```

**Human-readable format:** `ASIQ-XXXX-XXXX-XXXX-XXXX-XXXX`

### 5.2 Hybrid Validation (Online + Offline)

```
Every API request
      ↓
APP_MODE=cloud? → Skip entirely, proceed normally
      ↓ standalone
Local RSA signature check (< 5ms, in-memory)
      ↓ pass
Last remote check < 24h? → Yes: use cached result
      ↓ No
Call License Server (POST /v1/validate)
      ├── 200 OK       → cache result, continue normally
      ├── 403 Revoked  → immediately enter read-only mode
      └── Network err  → check cache age vs. grace period
              ├── Within grace period  → continue normally
              └── Grace period exceeded → read-only mode
```

### 5.3 Read-Only Mode

When triggered by expiry or grace period lapse:

- All `GET` endpoints continue to work normally
- All `POST / PUT / PATCH / DELETE` return `HTTP 402 Payment Required`
- Data exports (PDF, CSV) remain available
- Frontend shows a persistent renewal banner with expiry info
- Admin users see a License Management page with a key re-entry field
- Mobile app shows a "License expired — read only" header badge

### 5.4 Paystack Integration for Standalone

The Customer Portal uses the **same Paystack flow** already powering the cloud app, with one key difference — instead of activating a cloud subscription after `charge.success`, it **generates and emails a license key**:

```
Customer Portal → POST /billing/checkout (Paystack init)
      ↓
Paystack payment page
      ↓
charge.success webhook → License Server generates signed JWT key
      ↓
Key stored in Customer Portal database
Key emailed to customer
Key shown in Customer Portal dashboard
      ↓
Customer copies key → enters into self-hosted app → activated
```

The existing `PaystackGatewayService` and webhook HMAC-SHA512 validation logic is reused directly in the Customer Portal backend.

---

## 6. Implementation Phases

---

### Phase 0 — Foundation: Protect the Cloud Version
**Duration: Weeks 1–2 | Risk to cloud: None**

This phase makes zero user-facing changes. It restructures the codebase so that all future standalone code can be safely added without touching cloud behavior.

**Goal:** Introduce the `APP_MODE` split and refactor shared billing code so both editions can use Paystack independently.

#### Backend
- [ ] Add `APP_MODE` environment variable (`cloud` | `standalone`) to `application.properties` and `AppConfig`
- [ ] Create `@ConditionalOnAppMode("standalone")` annotation — any bean or filter annotated with this is completely excluded from the cloud build
- [ ] Create Spring profiles: `cloud` (default) and `standalone`
- [ ] Refactor `PaystackGatewayService` into a shared module that both the main app and the future Customer Portal can import (extract to internal library or shared package)
- [ ] Create `LicenseProperties` configuration class (reads `LICENSE_KEY`, `LICENSE_SERVER_URL` from env — ignored in cloud mode)
- [ ] Write tests confirming that in `cloud` mode, no license beans are loaded

#### Frontend (Next.js)
- [ ] Add `NEXT_PUBLIC_APP_MODE` env variable (`cloud` | `standalone`)
- [ ] Create `useLicenseStatus()` hook — returns `{ mode: 'cloud' }` in cloud mode (no-op), will return full license state in standalone mode
- [ ] Create `<LicenseGate>` component — renders children normally in cloud mode, checks license state in standalone mode
- [ ] No visible UI changes in either mode at this stage

#### Mobile App
- [ ] Confirm `EXPO_PUBLIC_API_BASE_URL` already supports pointing to a custom server (it does — no code change needed)
- [ ] Add graceful handling scaffold for `HTTP 402` responses in the Axios interceptor — currently unhandled, should show a generic "Access restricted" toast in cloud mode, full license message in standalone mode

**Cloud app after Phase 0:** Identical to today. Zero visible changes.

---

### Phase 1 — License Server
**Duration: Weeks 3–6 | Risk to cloud: None (separate service)**

Build the vendor-managed License Server as a completely separate Spring Boot application. It has its own database and no dependency on the main app codebase.

**Goal:** A working service that can issue, validate, and revoke license keys.

#### License Server (New Service)
- [ ] Bootstrap new Spring Boot project (`license-server`)
- [ ] Implement RSA-2048 key pair generation and management (private key stored in secure vault, public key distributed with app builds)
- [ ] `POST /v1/keys/issue` — generates and signs a new license JWT for a given org + plan
- [ ] `POST /v1/validate` — validates a key, checks revocation list, returns current plan state
- [ ] `POST /v1/keys/revoke` — immediately invalidates a key
- [ ] `POST /v1/keys/renew` — extends expiry on renewal payment
- [ ] `GET /v1/keys/{id}` — admin: fetch key details
- [ ] Implement server fingerprint binding (on first `/v1/validate` call from a new fingerprint)
- [ ] Implement abuse detection (multiple fingerprints for same key)
- [ ] Rate limiting on validation endpoint
- [ ] Admin dashboard (internal only) for managing all issued keys
- [ ] Write unit + integration tests for all endpoints
- [ ] Deploy to vendor infrastructure (separate from main app)

**Cloud app after Phase 1:** Still completely unchanged.

---

### Phase 2 — Backend Standalone Mode
**Duration: Weeks 7–9 | Risk to cloud: Low (guarded by APP_MODE)**

Add license enforcement to the main backend, all gated behind `@ConditionalOnAppMode("standalone")`.

**Goal:** The main backend, when run with `APP_MODE=standalone`, enforces license rules. When run with `APP_MODE=cloud`, behavior is identical to today.

#### Backend
- [ ] Build `LicenseService`:
  - Local RSA signature verification using bundled public key
  - In-memory cache of license state (refreshed every 24h)
  - `@Scheduled` task calling `POST /v1/validate` on the License Server
  - Grace period tracking (stores last successful remote validation timestamp in DB)
  - Returns `LicenseState`: `{ status, plan, limits, features, daysRemaining, readOnly }`
- [ ] Build `LicenseGuardFilter` (`@ConditionalOnAppMode("standalone")`):
  - Runs on every request
  - `GET` requests: always pass through
  - `POST / PUT / PATCH / DELETE` requests: return `402` if `readOnly=true`
  - Skipped entirely in cloud mode
- [ ] Plan limit enforcement (`@ConditionalOnAppMode("standalone")`):
  - `AssetService.create()` — check `limits.assets` before insert
  - `UserService.create()` — check `limits.users`
  - `DepartmentService.create()` — check `limits.departments`
  - Return `HTTP 403` with `{ error: "PLAN_LIMIT_REACHED", limit: 500, current: 500 }` when exceeded
  - 80% threshold warning returned in response headers
- [ ] `GET /api/v1/license/status` — returns current `LicenseState` (standalone only; returns `{ mode: 'cloud' }` in cloud mode)
- [ ] `POST /api/v1/license/activate` — accepts a new key, validates and stores it, restarts the license service (standalone only)
- [ ] Startup validation: if `APP_MODE=standalone` and no valid key present, log a clear error and refuse to start
- [ ] Write unit tests for `LicenseService` covering: valid key, expired key, revoked key, network failure within grace period, network failure beyond grace period
- [ ] Write integration tests for `LicenseGuardFilter` confirming cloud mode is unaffected

**Cloud app after Phase 2:** Still completely unchanged. The new filter, service, and endpoints are compiled but never instantiated when `APP_MODE=cloud`.

---

### Phase 3 — Frontend & Mobile Standalone Mode
**Duration: Weeks 10–12 | Risk to cloud: Low (gated by APP_MODE)**

Wire up the frontend and mobile app to the license state, all conditional on standalone mode.

**Goal:** Standalone users see license state, renewal prompts, and write-action gating. Cloud users see nothing different.

#### Frontend (Next.js)
- [ ] Complete `useLicenseStatus()` hook:
  - In cloud mode: returns `{ mode: 'cloud' }`, no API call made
  - In standalone mode: polls `GET /api/v1/license/status` on load and every 5 minutes
- [ ] `<LicenseProvider>` context wrapping the app root — exposes `licenseState` to all components
- [ ] Renewal banner component — only rendered in standalone mode when `daysRemaining < 30` or `readOnly=true`
  - Shows days remaining, plan name, link to Customer Portal for renewal
  - Dismissible for 24h unless `readOnly=true` (then permanent until renewed)
- [ ] Extend all create/edit/delete buttons to check `<LicenseGate>`:
  - Cloud mode: buttons behave as today
  - Standalone + active: buttons behave as today
  - Standalone + read-only: buttons are disabled with "License expired — contact your admin" tooltip
- [ ] License Management settings page (standalone only, admin role required):
  - Current plan, expiry date, days remaining, usage vs. limits progress bars
  - Key re-entry field (calls `POST /api/v1/license/activate`)
  - Link to Customer Portal for renewal
- [ ] First-run setup wizard (standalone only, shown when no key is activated yet):
  - Step 1: Welcome + enter license key
  - Step 2: Organisation name + admin user creation
  - Step 3: Ready
- [ ] Extend `PermissionGate` to also gate on license feature flags (e.g., hide Custom Fields menu if `features.custom_fields=false`)

#### Mobile App (React Native / Expo)
- [ ] Extend Axios interceptor: on `HTTP 402`, show toast "Your organization's license has expired. Contact your admin."
- [ ] `useLicenseStatus()` hook (mirrors web, calls same `/api/v1/license/status`):
  - No-op in cloud mode
  - Polls every 5 minutes in standalone mode
- [ ] Read-only banner in app header (`AppHeaderActions`) — standalone only, shown when `readOnly=true`
- [ ] Disable all create/edit/delete actions when `readOnly=true` — buttons show disabled state, tapping shows "License expired" alert
- [ ] Scan-to-create flow: if license is read-only, "Create asset from scan" button is hidden
- [ ] Handle `HTTP 403` plan-limit responses: show "Asset limit reached — upgrade your plan" alert

**Cloud app after Phase 3:** Billing screen, plan management, and all write actions work exactly as today. No banners, no license prompts, no `useLicenseStatus()` API calls.

---

### Phase 4 — Customer Portal
**Duration: Weeks 13–16 | Risk to cloud: None (separate app)**

Build the standalone Customer Portal as a completely separate web application. It shares the Paystack integration pattern but is fully independent from the main app.

**Goal:** A working portal where organizations can purchase a plan with Paystack, receive a license key, and manage renewals.

#### Customer Portal (New Web App)
- [ ] Bootstrap new Next.js project (`customer-portal`)
- [ ] Auth system (separate from main app — portal has its own accounts):
  - Email + password registration / login
  - Email verification
  - Password reset
- [ ] Plan selection page:
  - Display Starter / Professional / Enterprise plan cards
  - Annual pricing in GHS (matching existing `APP_BILLING_DEFAULT_CURRENCY`)
  - Feature comparison table
- [ ] Paystack checkout (reusing existing `PaystackGatewayService` pattern):
  - `POST /billing/checkout` → initializes Paystack transaction with plan amount
  - Redirects to Paystack payment page
  - Callback page extracts `reference`, calls `POST /billing/checkout/verify`
  - On `charge.success` webhook → call License Server `POST /v1/keys/issue` → store key in DB → email key to customer
- [ ] License key dashboard:
  - Show active key (masked: `ASIQ-XXXX-XXXX-****-****`)
  - Show plan, expiry date, days remaining
  - "Copy key" button (reveals full key once per session)
  - "Download bundle" button (downloads Docker Compose zip for purchased version)
  - Usage stats (synced from License Server validation requests)
- [ ] Renewal flow:
  - Reuses same Paystack checkout — on successful payment, License Server extends key expiry
  - Auto-renewal toggle (uses Paystack subscription recurring billing — same as existing cloud auto-renew)
- [ ] Key transfer page (once per year):
  - Accepts new server fingerprint
  - Calls License Server `POST /v1/keys/transfer`
- [ ] Renewal email notifications (triggered by License Server scheduled job):
  - 30 days before expiry
  - 14 days before expiry
  - 7 days before expiry
  - Day of expiry (grace period starts)
  - Grace period end warning
- [ ] Admin dashboard (internal):
  - All organizations, their plan, key status, usage
  - Manual key issuance / revocation
  - Revenue overview

**Cloud app after Phase 4:** Completely unchanged. The Customer Portal is a separate URL and codebase.

---

### Phase 5 — Deployment Packaging
**Duration: Weeks 17–18 | Risk to cloud: None**

Package the standalone app for easy self-hosting.

**Goal:** An organization's IT team can go from zero to running in under 30 minutes.

#### Docker Compose Bundle
- [ ] `docker-compose.yml` for the full stack:

```yaml
version: "3.9"
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: assetiq
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data

  api:
    image: assetiq/backend:${APP_VERSION}
    depends_on: [db]
    environment:
      APP_MODE: standalone
      DATABASE_URL: jdbc:postgresql://db:5432/assetiq
      LICENSE_KEY: ${ASSETIQ_LICENSE_KEY}
      LICENSE_SERVER_URL: https://license.assetiq.io
      APP_BASE_URL: ${APP_BASE_URL}
      JWT_SECRET: ${JWT_SECRET}
    ports:
      - "8085:8085"

  frontend:
    image: assetiq/frontend:${APP_VERSION}
    depends_on: [api]
    environment:
      NEXT_PUBLIC_APP_MODE: standalone
      NEXT_PUBLIC_API_URL: ${APP_BASE_URL}/api/v1
    ports:
      - "3000:3000"

  nginx:
    image: nginx:alpine
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./ssl:/etc/nginx/ssl:ro
    ports:
      - "80:80"
      - "443:443"
    depends_on: [api, frontend]

volumes:
  pgdata:
```

- [ ] `.env.example` with all required variables documented
- [ ] `setup.sh` — first-time install script:
  ```bash
  docker compose pull
  docker compose up -d db
  # wait for DB to be ready
  docker compose up -d api frontend nginx
  # run DB migrations via API health check
  echo "Setup complete. Visit https://${APP_BASE_URL}"
  ```
- [ ] `update.sh` — version upgrade script (pulls new images, runs migrations, rolling restart)
- [ ] `nginx.conf` — SSL termination + reverse proxy to frontend and API
- [ ] Test full install on clean Ubuntu 22.04 server
- [ ] Test upgrade flow from v1 to v2
- [ ] Write PDF installation guide (hosted on Customer Portal)

**Installation steps (customer-facing):**
```bash
# 1. Download bundle from Customer Portal
unzip assetiq-standalone-v2.x.x.zip && cd assetiq-standalone

# 2. Configure
cp .env.example .env
# Fill in: DB_PASSWORD, JWT_SECRET, APP_BASE_URL, ASSETIQ_LICENSE_KEY

# 3. Install
./setup.sh

# 4. Open browser → https://your-server-address
# Complete setup wizard: enter license key → create admin user → done
```

---

### Phase 6 — QA, Security Audit & Launch
**Duration: Weeks 19–21 | Risk to cloud: Low**

**Goal:** Validate everything works end-to-end across both cloud and standalone, and that cloud customers are genuinely unaffected.

#### Cloud Regression Testing
- [ ] Full regression test suite run against the cloud app — confirm zero behavioral changes
- [ ] Confirm no license-related API calls appear in cloud network traffic
- [ ] Confirm billing screen works as before (Paystack flow unchanged)
- [ ] Confirm mobile app cloud mode is unaffected

#### Standalone End-to-End Testing
- [ ] Full flow: purchase on portal → receive key → download bundle → install → activate → use app → expire key → read-only mode → renew → full access restored
- [ ] Test plan limit enforcement: hit asset/user/department ceiling, confirm `403` response and UI prompt
- [ ] Test 80% threshold warnings
- [ ] Test grace period: disconnect from internet, let remote validation window pass, confirm app stays up within grace period, confirm read-only triggers after grace period
- [ ] Test clock manipulation: server time set forward past expiry
- [ ] Test key revocation: revoke key from admin portal, confirm `402` within 24h
- [ ] Test key re-entry: enter new key via License Management page, confirm instant activation
- [ ] Test setup wizard on fresh install

#### Security Audit
- [ ] Penetration test on License Server (key forge attempt, replay attacks)
- [ ] Test that `APP_MODE=cloud` cannot be tricked into loading license middleware
- [ ] Verify RSA public key cannot be swapped in the Docker image
- [ ] Audit `.env` file handling in Docker Compose (no secrets in image layers)

#### Beta
- [ ] Onboard 2–3 pilot organizations on the standalone edition
- [ ] Collect feedback on installation experience and UI clarity
- [ ] Fix any bugs discovered

#### Launch
- [ ] Customer Portal live with Paystack billing
- [ ] Docker bundle published on Customer Portal
- [ ] Documentation live (installation guide, upgrade guide, troubleshooting)
- [ ] Renewal email sequences active
- [ ] Monitoring and alerts on License Server

---

## 7. Codebase Impact Summary

| Repository | Changes | Cloud affected? |
|---|---|---|
| `backend` (Spring Boot) | `APP_MODE` flag, `LicenseService`, `LicenseGuardFilter`, plan limit checks, license endpoints | No — all gated by `@ConditionalOnAppMode` |
| `frontend` (Next.js) | `APP_MODE` env, `useLicenseStatus`, `LicenseGate`, renewal banner, License Management page | No — all gated by `NEXT_PUBLIC_APP_MODE` |
| `mobile` (Expo) | Axios 402 handling, `useLicenseStatus`, read-only banner | No — no-op in cloud mode |
| `license-server` | **New service** — RSA signing, validation, revocation | N/A |
| `customer-portal` | **New service** — Paystack billing, key management | N/A |
| `docker-bundle` | **New artifact** — Docker Compose packaging | N/A |

---

## 8. Security Considerations

- **RSA public key bundled in app** — used for offline JWT verification. Private key never leaves the License Server.
- **Key in transit** — HTTPS only. Never logged in application logs.
- **Key at rest** — stored in `.env` on the customer's server. Guide recommends `chmod 600 .env`.
- **Fingerprinting** — one-way hash of hardware identifiers. No raw machine data sent to License Server.
- **Validation payload** — only usage counts and app version sent to License Server. No asset names, user data, or business data ever leave the customer's server.
- **GDPR / data residency** — all operational data stays on the customer's server. Vendor retains only plan metadata and key validation timestamps.
- **Paystack security** — Customer Portal reuses existing HMAC-SHA512 webhook signature verification. No change to security posture.

---

## 9. Key Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Cloud regression from shared code changes | `APP_MODE` gates + full cloud regression suite in Phase 6 |
| Customer internet outage exceeds grace period | Grace period is 7–30 days depending on plan; documented clearly in sales materials |
| License key leaked or shared | Server fingerprint binding + abuse detection on License Server |
| Customer loses their key | Customer Portal allows full key re-display at any time |
| License Server downtime | Grace period absorbs outages; 99.9% SLA target |
| Clock manipulation to bypass expiry | Remote validation timestamp trusted over local clock |
| Old Docker image bypasses licensing | License payload includes `min_app_version` field |

---

## 10. Success Metrics

- Time to install (zero to running): **< 30 minutes**
- License validation latency (local): **< 5ms**
- License Server uptime: **99.9%**
- Cloud regression issues from standalone changes: **0**
- Unexpected customer lockouts during internet outages: **0**
- Renewal rate after first year: **> 70%**

---

## 11. Phase Timeline Summary

| Phase | Focus | Duration | Cloud Risk |
|---|---|---|---|
| **0** | APP_MODE split, shared billing refactor | Weeks 1–2 | None |
| **1** | License Server (new service) | Weeks 3–6 | None |
| **2** | Backend standalone mode | Weeks 7–9 | Low |
| **3** | Frontend & mobile standalone mode | Weeks 10–12 | Low |
| **4** | Customer Portal + Paystack | Weeks 13–16 | None |
| **5** | Docker Compose packaging | Weeks 17–18 | None |
| **6** | QA, security audit & launch | Weeks 19–21 | Low |

**Total estimated duration: 21 weeks (~5 months)**

---

*End of Plan — v2.0*

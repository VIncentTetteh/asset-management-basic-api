# AssetIQ Standalone — QA & End-to-End Test Checklist

Run this checklist before every release and after every non-trivial change.
Mark each item ✅ pass / ❌ fail / ⏭ skip (with reason).

---

## 1. Cloud Regression (run against the production cloud environment)

| # | Test | Result | Notes |
|---|------|--------|-------|
| C1 | Full regression suite passes with zero failures | | |
| C2 | No network requests to `/api/v1/license/*` appear in browser DevTools for a cloud session | | |
| C3 | Paystack billing flow (upgrade plan) completes successfully | | |
| C4 | Mobile app (cloud build) loads, logs in, and creates an asset | | |
| C5 | `APP_MODE` env var is absent (or `cloud`) in all cloud environment configs | | |
| C6 | No `LicenseGuardFilter` bean visible in cloud Spring context (`/actuator/beans`) | | |

---

## 2. Fresh Install — Docker Compose

| # | Test | Result | Notes |
|---|------|--------|-------|
| I1 | Clone repo on a clean machine with only Docker installed | | |
| I2 | `./scripts/setup-standalone.sh` completes without errors | | |
| I3 | RSA keys created at `keys/private.pem` and `keys/public.pem` | | |
| I4 | `.env` file written with non-default passwords | | |
| I5 | `docker compose up --build` brings all 5 services to healthy state | | |
| I6 | `./scripts/smoke-test.sh` passes all checks | | |
| I7 | Browser navigates to `https://localhost` without errors | | |
| I8 | Setup wizard appears on first load | | |
| I9 | Total time from clone to running: **< 30 minutes** | | |

---

## 3. License Purchase → Activation

| # | Test | Result | Notes |
|---|------|--------|-------|
| P1 | Visit customer portal pricing page — all 3 plan cards render correctly | | |
| P2 | Click "Get started" on Professional → checkout form appears pre-filled | | |
| P3 | Submit checkout with valid email/org → redirects to Paystack hosted page | | |
| P4 | Complete Paystack test payment (card `4084 0840 8408 4081`) | | |
| P5 | Redirected back to `/checkout/verify` — license key displayed immediately | | |
| P6 | License key email received within 60 seconds | | |
| P7 | Key in email matches key shown on screen | | |
| P8 | Customer dashboard shows the new key under the purchase email | | |

---

## 4. Key Activation in the App

| # | Test | Result | Notes |
|---|------|--------|-------|
| A1 | Open `https://<server>/settings/license` — page loads | | |
| A2 | Paste license key and click Activate | | |
| A3 | Success state shows plan name, expiry, limits grid | | |
| A4 | `GET /api/v1/license/status` returns `{"status":"valid","readOnly":false}` | | |
| A5 | License banner is absent from the header (key is valid, >30 days remaining) | | |
| A6 | Can create a new asset — write access confirmed | | |
| A7 | Re-enter the same key → shows "already active" or re-activates cleanly | | |

---

## 5. Plan Limit Enforcement

| # | Test | Result | Notes |
|---|------|--------|-------|
| L1 | Create assets up to the plan limit (e.g. 500 for Starter) | | |
| L2 | Attempt to create asset #501 → backend returns HTTP 403 | | |
| L3 | Mobile app shows "Plan limit reached" toast on the 403 | | |
| L4 | Web UI shows appropriate plan-limit message | | |
| L5 | At 80% of limit (400/500 assets), `X-License-Warning` header is present | | |
| L6 | User limit: invite user past ceiling → 403 response | | |
| L7 | Department limit: create department past ceiling → 403 response | | |

---

## 6. License Expiry → Read-Only Mode

| # | Test | Result | Notes |
|---|------|--------|-------|
| E1 | Manually set server clock forward past `expiresAt` **and** beyond grace period | | |
| E2 | `GET /api/v1/license/status` returns `{"readOnly":true,"status":"expired"}` | | |
| E3 | `POST /api/v1/assets` returns HTTP 402 | | |
| E4 | `GET /api/v1/assets` returns HTTP 200 (reads still work) | | |
| E5 | Red "License expired" banner visible in web UI header | | |
| E6 | Create asset button disabled / shows tooltip | | |
| E7 | Mobile: "License expired" toast shown | | |
| E8 | Mobile: "Create asset from scan" button hidden in scan screen | | |
| E9 | Setup wizard re-appears if key is removed from DB | | |

---

## 7. Grace Period (Offline Validation)

| # | Test | Result | Notes |
|---|------|--------|-------|
| G1 | Block outbound traffic from backend container (`iptables` or firewall rule) | | |
| G2 | Scheduled remote validation runs (check logs) — fails silently | | |
| G3 | App remains fully functional within grace period | | |
| G4 | `lastRemoteValidationAt` timestamp in DB does NOT update while offline | | |
| G5 | After grace period days elapse (clock forward), read-only mode activates | | |
| G6 | Restore network → next scheduled validation succeeds → full access restored | | |

---

## 8. Key Renewal

| # | Test | Result | Notes |
|---|------|--------|-------|
| R1 | In customer dashboard, click Renew on an expired key | | |
| R2 | Paystack renewal payment completes | | |
| R3 | Redirected to `/checkout/verify` with "License renewed\!" heading | | |
| R4 | Renewal email received with updated key | | |
| R5 | Paste renewed key into Settings → License → full access restored | | |
| R6 | `GET /api/v1/license/status` shows new expiry date and `readOnly: false` | | |
| R7 | Banners clear from web UI and mobile | | |

---

## 9. Security Audit

| # | Test | Result | Notes |
|---|------|--------|-------|
| S1 | Attempt to forge a license JWT with a different private key → activate fails | | |
| S2 | Replay a valid validate request → Bucket4j returns 429 after 30 req/min | | |
| S3 | Attempt to access `/v1/admin/keys` without `X-Admin-Key` → 403 | | |
| S4 | Attempt to POST to `/api/v1/billing/webhooks/paystack` with wrong HMAC → 401 | | |
| S5 | Inspect Docker image layers — no `.env` or `private.pem` baked in | | |
| S6 | `APP_MODE=cloud` container has no `LicenseGuardFilter` in Spring context | | |
| S7 | Internal services (postgres, backend, license-server) have no host port bindings | | |
| S8 | Response headers include `X-Frame-Options: DENY` and `X-Content-Type-Options: nosniff` | | |
| S9 | Portal checkout endpoint returns 429 after 5 rapid requests from same IP | | |
| S10| Swagger UI is accessible in standalone (docs needed) but `/actuator/env` is NOT | | |

---

## 10. Upgrade Test

| # | Test | Result | Notes |
|---|------|--------|-------|
| U1 | `git pull` on an existing installation | | |
| U2 | `docker compose up -d --build` completes without data loss | | |
| U3 | Flyway migrations apply cleanly (check logs) | | |
| U4 | License key still active after upgrade | | |
| U5 | All assets / users intact | | |

---

## Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| QA Lead | | | |
| Backend Engineer | | | |
| DevOps | | | |

**Release is blocked until all items in sections 1–9 are ✅ pass.**

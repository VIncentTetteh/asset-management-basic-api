# AssetIQ Standalone — Launch Checklist

Work through every section in order. Each item must be ✅ before moving to the
next section. Assign an owner and a target date for each item.

---

## Section 1 — Infrastructure

| # | Item | Owner | Done |
|---|------|-------|------|
| 1.1 | License Server deployed to production (e.g. `license.assetiq.io`) | DevOps | ☐ |
| 1.2 | License Server database backed up and monitored | DevOps | ☐ |
| 1.3 | RSA private key stored in a secrets manager (not just `.env`) | DevOps | ☐ |
| 1.4 | License Server HTTPS certificate valid and auto-renewing | DevOps | ☐ |
| 1.5 | Customer Portal deployed to production (`portal.assetiq.io`) | DevOps | ☐ |
| 1.6 | Customer Portal `RESEND_API_KEY` and `FROM` domain verified | DevOps | ☐ |
| 1.7 | Paystack webhook URL registered: `https://portal.assetiq.io/api/webhooks/paystack` | Backend | ☐ |
| 1.8 | Paystack webhook HMAC secret matches `PAYSTACK_SECRET_KEY` in portal env | Backend | ☐ |
| 1.9 | Uptime monitoring on License Server and Customer Portal | DevOps | ☐ |
| 1.10 | Automated daily backup of License Server DB verified restorable | DevOps | ☐ |

---

## Section 2 — Docker Bundle

| # | Item | Owner | Done |
|---|------|-------|------|
| 2.1 | `docker compose up --build` tested on clean Ubuntu 22 + macOS 14 | DevOps | ☐ |
| 2.2 | `./scripts/smoke-test.sh` passes on a clean install | DevOps | ☐ |
| 2.3 | All 10 QA Checklist sections signed off | QA | ☐ |
| 2.4 | Docker image sizes are reasonable (backend <500 MB, frontend <200 MB) | DevOps | ☐ |
| 2.5 | `docker scout` or `trivy` image scan — no critical CVEs | DevOps | ☐ |
| 2.6 | Bundle zip published to Customer Portal download page | DevOps | ☐ |
| 2.7 | Checksum (SHA-256) of the bundle zip published alongside it | DevOps | ☐ |

---

## Section 3 — Customer Portal

| # | Item | Owner | Done |
|---|------|-------|------|
| 3.1 | All 3 Paystack plan products created with correct prices | Business | ☐ |
| 3.2 | Test purchase (Starter) completes and key email arrives | QA | ☐ |
| 3.3 | Test purchase (Professional) completes and key email arrives | QA | ☐ |
| 3.4 | Test purchase (Enterprise) completes and key email arrives | QA | ☐ |
| 3.5 | Renewal flow tested end-to-end | QA | ☐ |
| 3.6 | Customer dashboard shows correct key status and days-remaining | QA | ☐ |
| 3.7 | Refund policy text visible on checkout page | Legal | ☐ |
| 3.8 | Privacy policy and Terms of Service linked in portal footer | Legal | ☐ |
| 3.9 | Rate limits tested (5 checkouts / IP / min) | QA | ☐ |

---

## Section 4 — Documentation

| # | Item | Owner | Done |
|---|------|-------|------|
| 4.1 | Installation guide live at `docs.assetiq.io/standalone/getting-started` | Docs | ☐ |
| 4.2 | Upgrade guide live at `docs.assetiq.io/standalone/upgrade` | Docs | ☐ |
| 4.3 | Troubleshooting guide (common errors + solutions) live | Docs | ☐ |
| 4.4 | License management guide (activate, renew, revoke) live | Docs | ☐ |
| 4.5 | Docker Compose environment variables documented | Docs | ☐ |
| 4.6 | `README.txt` in license resource dir updated with final key path | Backend | ☐ |
| 4.7 | CHANGELOG.md published with Phase 0–6 summary | Backend | ☐ |

---

## Section 5 — Communications

| # | Item | Owner | Done |
|---|------|-------|------|
| 5.1 | Announcement email drafted and reviewed | Marketing | ☐ |
| 5.2 | Pilot organisations (2–3) onboarded and feedback collected | Sales | ☐ |
| 5.3 | Pilot feedback issues triaged and critical ones fixed | Backend | ☐ |
| 5.4 | Public announcement sent | Marketing | ☐ |
| 5.5 | Support inbox monitored for day-1 issues | Support | ☐ |

---

## Section 6 — Post-Launch Monitoring (Day 1 / Week 1)

| # | Item | Owner | Done |
|---|------|-------|------|
| 6.1 | License Server error rate < 0.1% | DevOps | ☐ |
| 6.2 | No unexpected `APP_MODE` errors in cloud app logs | DevOps | ☐ |
| 6.3 | First renewal payment processed successfully | QA | ☐ |
| 6.4 | No customer lockout reports | Support | ☐ |
| 6.5 | Retrospective scheduled for Week 2 | PM | ☐ |

---

## Final Sign-off

> All items above must be ✅ before this checklist is considered complete.

| Role | Name | Date |
|------|------|------|
| Engineering Lead | | |
| QA Lead | | |
| Product Manager | | |
| DevOps Lead | | |

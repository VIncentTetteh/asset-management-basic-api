# AssetIQ — Backend API

**AssetIQ** is an enterprise asset management platform: asset lifecycle (register, checkout, transfer, audit, maintenance, disposal), procurement & finance (budgets, purchase orders, contracts, leases, depreciation), compliance (Bank of Ghana ICT directive, PCI-DSS SAQ, risk register, Ghana DPA Act 843), multi-tenant SaaS billing (Paystack), and AI insights — served to web, desktop, and mobile clients.

Part of the AssetIQ platform:

| Surface | Repo |
|---|---|
| **Backend API (this repo)** | `Enterprise-Asset-Manager` |
| Web app (Next.js) | `Enterprise-Asset-manager-Frontend` |
| Desktop app (Electron) | `Enterprise-Asset-manager-desktop-app` |
| Mobile app (Expo) | `Enterprise-Asset-Mobile` |

## Stack

- Java 21 / Spring Boot 3.3 (Maven), PostgreSQL + Flyway, Redis
- Stateless JWT auth + SAML2/OAuth2 SSO + TOTP MFA; RBAC via `Permission` enum + `@PreAuthorize`
- Real multi-tenancy: `TenantContext` / `TenantFilter` / `@EnforceTenant`
- Observability: Actuator + Prometheus, Sentry, structured logging with request correlation
- OpenAPI via Springdoc (Swagger UI admin-only, disabled in prod)

## Getting started

```bash
cp .env.example .env       # fill in values — see docs/SECRETS.md; never commit .env
docker compose up -d       # postgres + redis
./mvnw spring-boot:run     # API at http://localhost:8080/api/v1
```

Run tests (Testcontainers requires Docker):

```bash
./mvnw verify              # tests + JaCoCo coverage gate
```

## Layout

- `src/main/java/com/assetiq/` — `controllers/v1/` (47 controllers, ~353 endpoints), `services/`, `models/` (65 entities), `security/`, `multitenancy/`, `cloudsync/`, `dpa/`, `license/`
- `src/main/resources/db/migration/` — Flyway migrations (schema source of truth; `ddl-auto=validate`)
- `assetiq-customer-portal/` — Next.js license storefront (Paystack checkout, key issuance)
- `assetiq-standalone/` — self-hosted docker-compose distribution
- `ops/` — status page; `docs/` — versioning policy, API reference

## Security notes

- All secrets come from environment variables — see `docs/SECRETS.md` for the inventory and rotation procedure. Never commit `.env`.
- `StartupSecurityValidator` refuses to boot with a weak/default JWT secret.

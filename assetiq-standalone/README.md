# AssetIQ — self-hosted Docker distribution

Everything needed to run AssetIQ on your own server: a production Compose
stack, a bootstrap script that generates your secrets, and backup and health
tooling.

**The operator's manual is [`../docs/self-hosting.md`](../docs/self-hosting.md).**
It covers requirements, first run, the licence key, upgrading, backup and
restore, and troubleshooting. This file is just the map of the directory.

## Quick start

```bash
./scripts/bootstrap.sh --public-url https://assetiq.example.com --version 1.4.2
docker compose pull
docker compose up -d
./scripts/health-check.sh
```

`bootstrap.sh` generates `APP_JWT_SECRET`, `APP_DATA_ENCRYPTION_KEY` and the
database and Redis passwords in exactly the formats the application validates at
startup, writes them to `.env` at mode `0600`, and generates a self-signed TLS
certificate. It refuses to overwrite an existing `.env`, because regenerating
`APP_DATA_ENCRYPTION_KEY` makes every encrypted column permanently unreadable.

## Layout

```
.
├── docker-compose.yml          The production stack: postgres 16, redis 7,
│                               backend, web, TLS edge. Pinned image tags,
│                               healthchecks, named volumes, resource limits.
│                               Every service non-root with a read-only root
│                               filesystem.
├── docker-compose.build.yml    Overlay that swaps published images for local
│                               build contexts. Changes nothing else, so what
│                               you test from source is what ships.
├── .env.example                Every variable the stack reads, documented.
│                               No real values.
├── nginx/
│   ├── nginx.conf              TLS edge: /api and / on ONE origin, which the
│                               SameSite=Strict session cookie requires.
│   └── certs/                  Your certificate and key go here (gitignored).
└── scripts/
    ├── bootstrap.sh            One-command first-time setup. Start here.
    ├── health-check.sh         Health of every service.
    ├── backup.sh               Database + uploads backup.
    ├── smoke-compose.sh        Brings the stack up from source and asserts the
    │                           backend is healthy, Flyway migrated, and the
    │                           container is non-root on a read-only rootfs.
    ├── smoke-test.sh           Post-install functional checks.
    ├── generate-rsa-keys.sh    Keypair for the legacy online licence path.
    └── generate-self-signed-cert.sh
```

## Notes

- **Pin your version.** `ASSETIQ_VERSION` is required and `latest` is rejected.
  An AssetIQ image change is a schema migration; a moving tag runs it on
  whatever restart happens next.
- **No outbound internet is required.** The licence is verified locally against
  a public key in the image — no licence server, no call-home. Outbound access
  is needed only for SMTP, object storage or external SSO, if you enable them.
- **Nothing here is a hard stop.** An absent, expired or invalid licence key
  means free-tier quotas and a log line, never an outage and never data loss.

## Changed in this release

`docker-compose.standalone.yml`, `docker-compose.standalone.images.yml` and
`scripts/setup-standalone.sh` were replaced by `docker-compose.yml`,
`docker-compose.build.yml` and `scripts/bootstrap.sh`. The new stack adds Redis
(the old one had none, so the rate limiter silently degraded to a no-op),
read-only root filesystems, resource limits, non-root execution throughout, and
a non-interactive bootstrap. If you have an existing installation, its `.env` is
still valid; add `REDIS_PASSWORD`, `ASSETIQ_VERSION` and `APP_PUBLIC_URL`, and
see `.env.example` for the full list.

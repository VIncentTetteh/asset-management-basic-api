# AssetIQ Standalone — Deployment Package

Self-hosted edition of AssetIQ. All data stays on your server.

There are **two installation modes**. Pick one.

## Mode A — Prebuilt images (recommended for customers)

No source code required. Pulls signed, checksummed images from the AssetIQ container registry.

```bash
# 1. Run the interactive setup wizard (generates app secrets, certs, and .env)
cd assetiq-standalone
./scripts/setup-standalone.sh

# 2. Authenticate to the registry (once)
docker login ghcr.io   # use the read:packages PAT that AssetIQ sends you

# 3. Pull & start
docker compose -f docker-compose.standalone.images.yml pull
docker compose -f docker-compose.standalone.images.yml up -d

# 4. Verify
./scripts/health-check.sh

# 5. Open your browser
open https://localhost
```

## Mode B — Build from source (AssetIQ staff / advanced operators)

Requires the backend and web UI source directories to be co-located. The
license issuer is vendor-managed and is intentionally never distributed to a
customer installation.

```
parent/
├── assetiq-standalone/                   ← this directory
├── Enterprise-Asset-Manager/             ← backend
└── Enterprise-Asset-manager-Frontend/    ← web UI
```

```bash
cd assetiq-standalone
./scripts/setup-standalone.sh
docker compose -f docker-compose.standalone.yml up -d --build
open https://localhost
```

> **Heads-up:** Source builds also require the vendor public verification key at
> `Enterprise-Asset-Manager/src/main/resources/license/public.pem`. The private
> signing key must never be copied into this package. If the source bundle does
> not contain the public key, use **Mode A** or obtain the release key from
> AssetIQ operations.

## Directory structure

```
assetiq-standalone/
├── docker-compose.standalone.yml   Main compose file
├── .env.standalone.example         All environment variables documented
├── .gitignore                      Excludes secrets from git
├── nginx/
│   ├── nginx.conf                  Reverse proxy configuration
│   └── certs/                      Place server.crt + server.key here
├── scripts/
│   ├── setup-standalone.sh         First-time interactive setup wizard
│   ├── generate-self-signed-cert.sh  TLS certificate for local use
│   ├── backup.sh                   Timestamped database backup
│   └── health-check.sh             Verifies all services are healthy
```

## Services

| Service         | Internal port | Description                       |
|-----------------|--------------|-----------------------------------|
| `postgres`      | 5432         | PostgreSQL 16 application database|
| `backend`       | 8080         | Spring Boot REST API              |
| `frontend`      | 3000         | Next.js web UI                    |
| `nginx`         | 80 / 443     | Reverse proxy (public entry point)|

## License activation

After the first `docker compose up`, open `https://<your-domain>`.
A setup wizard will prompt you to enter your license key.

Buy or renew a key at **portal.assetiq.io**.
The backend verifies signatures locally with the bundled public key and checks
revocation with `https://license.assetiq.io`; only the backend has outbound
network access. Customer assets and operational records remain local.

## Backup

```bash
./scripts/backup.sh
# Writes timestamped gzip dumps to backups/
# Automatically removes dumps older than 30 days
```

Schedule with cron:
```
0 2 * * * /opt/assetiq-standalone/scripts/backup.sh >> /var/log/assetiq-backup.log 2>&1
```

## Upgrade

```bash
git pull                    # pull latest source
docker compose -f docker-compose.standalone.yml up -d --build
```

Flyway runs migrations automatically on startup.

## TLS in production

Replace the self-signed cert with a real one:
```bash
# With certbot (Let's Encrypt)
certbot certonly --standalone -d assetiq.example.com
cp /etc/letsencrypt/live/assetiq.example.com/fullchain.pem nginx/certs/server.crt
cp /etc/letsencrypt/live/assetiq.example.com/privkey.pem   nginx/certs/server.key
docker compose -f docker-compose.standalone.yml restart nginx
```

# AssetIQ Standalone — Deployment Package

Self-hosted edition of AssetIQ. All data stays on your server.

## Quick start

```bash
# 1. Clone / copy the full source tree so sibling directories exist:
#    assetiq-standalone/        ← this directory
#    Enterprise-Asset-Manager/  ← backend
#    Enterprise-Asset-manager-Frontend/  ← web UI
#    assetiq-license-server/    ← license server
#    assetiq-customer-portal/   ← (optional) customer-facing purchase portal

# 2. Run the interactive setup wizard (generates keys, certs, and .env)
cd assetiq-standalone
./scripts/setup-standalone.sh

# 3. Build and start the stack
docker compose -f docker-compose.standalone.yml up -d --build

# 4. Open your browser
open https://localhost
```

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
│   ├── generate-rsa-keys.sh        Generates RSA-2048 key pair
│   ├── generate-self-signed-cert.sh  TLS certificate for local use
│   ├── create-multiple-postgresql-databases.sh  Postgres init script
│   ├── backup.sh                   Timestamped database backup
│   └── health-check.sh             Verifies all services are healthy
└── keys/                           RSA PEM files (gitignored)
```

## Services

| Service         | Internal port | Description                       |
|-----------------|--------------|-----------------------------------|
| `postgres`      | 5432         | PostgreSQL 16 (two databases)     |
| `license-server`| 8090         | License JWT issuer + validator    |
| `backend`       | 8080         | Spring Boot REST API              |
| `frontend`      | 3000         | Next.js web UI                    |
| `nginx`         | 80 / 443     | Reverse proxy (public entry point)|

## License activation

After the first `docker compose up`, open `https://<your-domain>`.
A setup wizard will prompt you to enter your license key.

Buy or renew a key at **portal.assetiq.io**.

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

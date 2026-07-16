# AssetIQ Status Page (P0-7)

Self-hosted Uptime Kuma instance that powers `status.assetiq.io`. Lives on its
own VM so that customers can still see an "API is down" indicator when the
production stack is having a bad day.

## Deploy

```bash
# On a fresh Ubuntu 22.04 VM with Docker installed.
git clone <repo> /opt/assetiq
cd /opt/assetiq/Enterprise-Asset-Manager/ops/status-page

# Obtain a certificate (one-off; certbot handles renewals via cron).
sudo apt install -y certbot
sudo certbot certonly --standalone -d status.assetiq.io

cp .env.example .env
docker compose up -d

# Finish setup in the browser:
#   1. Open https://status.assetiq.io — you will be prompted to create
#      the admin user.
#   2. Settings → Status Pages → New Status Page (slug: `assetiq`).
#   3. Add the monitors listed in docker-compose.yml.
#   4. Settings → Notifications → add Slack and/or PagerDuty.
```

## What this page is NOT

- It is not a history-of-record for every 5xx in the product. That is Sentry's
  job (see P0-6).
- It is not an SLA dashboard. Kuma exposes a JSON feed that Phase 3 will feed
  into Grafana for internal SLA tracking — but customers see the simpler
  status page only.

## Incident workflow

1. A monitor flaps red → Kuma fires Slack + PagerDuty.
2. On-call engineer posts an incident to the status page (admin UI → Incidents).
3. Engineer links the Sentry issue in the incident body.
4. When resolved, mark the incident `resolved` and Kuma auto-notifies subscribers.

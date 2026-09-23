# Running AssetIQ on your own infrastructure

This is the operator's manual for the self-hosted distribution of AssetIQ. It
assumes you administer Linux servers and either Docker or Kubernetes, and it
assumes nothing about AssetIQ itself.

AssetIQ is an asset-management application: a Spring Boot API backed by
PostgreSQL and Redis, plus a web UI that is a directory of static files. There
are four moving parts and no hidden ones.

| Component | What it is | Where state lives |
|---|---|---|
| **backend** | Spring Boot 3 / Java 21 API, serves `/api/v1` and `/actuator` | PostgreSQL, plus an S3-compatible store for generated files — see [Object storage](#object-storage--read-this-one) |
| **web** | Next.js static export served by nginx on port 3000 | none — it is a pile of files |
| **postgres** | PostgreSQL 16, the system of record | its data volume |
| **redis** | Redis 7: rate-limiter counters, SSO handshake state | its data volume, none of it durable |

Two things are worth knowing before you start, because they shape everything
else:

- **The API and the UI must be served from one origin.** The session is an
  `HttpOnly; SameSite=Strict` cookie, and a browser will not send that cookie to
  an API on a different site. Both distributions below put `/api` and `/` on the
  same hostname. If you front AssetIQ with your own proxy, preserve that.
- **The licence never phones home and never stops the application.** See
  [Licence key](#licence-key).
- **Configure object storage before you go live.** There is no local-disk
  backend; the default keeps generated files in memory. See
  [Object storage](#object-storage--read-this-one).

---

## Contents

1. [Requirements](#requirements)
2. [Install with Docker Compose](#install-with-docker-compose)
3. [Install with Helm](#install-with-helm)
4. [First run](#first-run)
5. [Licence key](#licence-key)
6. [Upgrading](#upgrading)
7. [Backup and restore](#backup-and-restore)
8. [Troubleshooting](#troubleshooting)

---

## Requirements

### Docker Compose

| | Minimum | Comfortable |
|---|---|---|
| CPU | 2 cores | 4 cores |
| RAM | 4 GB | 8 GB |
| Disk | 20 GB | 100 GB SSD |
| Docker Engine | 24.0 | 26.0+ |
| Compose plugin | v2.20 | v2.29+ |

The backend alone is given a 2 GB memory limit and will use most of it under
load; the JVM is configured to take 75% of whatever limit it is given. A 4 GB
host runs the whole stack but leaves little headroom for a large report export.

Also needed on the host: `openssl` and `bash` (both present on any mainstream
Linux distribution).

### Kubernetes

- Kubernetes **1.30 or newer**. 1.29 works, with one caveat noted in
  `infra/helm/assetiq/README.md`: the chart's `preStop` sleep handler is beta
  from 1.30 and is silently ignored on 1.29, which makes rolling updates drop a
  few in-flight requests. Set `backend.preStopSleepSeconds=0` on 1.29 if you
  would rather not rely on a field the API server discards.
- An ingress controller.
- PostgreSQL 16 and Redis 7 reachable from the cluster. The chart does **not**
  bundle them; see [Why no bundled database](#why-no-bundled-database).
- Optional but recommended: the Prometheus Operator (for `ServiceMonitor`), the
  External Secrets Operator (for `ExternalSecret`), and a CNI that enforces
  `NetworkPolicy` — Calico, Cilium, or equivalent. On a CNI that does not
  enforce them, the chart's policies are inert objects; they apply cleanly and
  protect nothing.

### Network

The application needs **no outbound internet access**. It does not check for
updates, does not report usage, and does not validate its licence against a
server. Outbound connectivity is needed only for things you choose to turn on:
SMTP, an S3-compatible object store, or SSO against an external identity
provider.

---

## Install with Docker Compose

Everything lives in the `assetiq-standalone/` directory.

```bash
cd assetiq-standalone

# 1. Generate secrets and a TLS certificate. Run once.
./scripts/bootstrap.sh --public-url https://assetiq.example.com --version 1.4.2

# 2. Pull the pinned images.
docker compose pull

# 3. Start.
docker compose up -d

# 4. Check.
./scripts/health-check.sh
```

`bootstrap.sh` writes `.env` with mode `0600` and refuses to overwrite an
existing one, because regenerating `APP_DATA_ENCRYPTION_KEY` would permanently
destroy every encrypted column. It generates:

| Variable | Format | Why that format |
|---|---|---|
| `APP_JWT_SECRET` | 64 hex characters | The app refuses to boot on a short or low-entropy signing secret |
| `APP_DATA_ENCRYPTION_KEY` | Base64 of exactly 32 bytes | AES-256; the app decodes and length-checks it at startup |
| `POSTGRES_PASSWORD` | 48 hex characters | — |
| `REDIS_PASSWORD` | 48 hex characters | — |

`.env.example` documents every variable the stack reads, including the optional
ones `bootstrap.sh` leaves blank. It contains no real values.

### Pinning a version

`ASSETIQ_VERSION` is required and `latest` is rejected. This is deliberate: an
AssetIQ image change is a schema migration, and a moving tag means that
migration runs on whatever restart happens next, at a time nobody chose.

### TLS

`bootstrap.sh` generates a self-signed certificate so the stack is usable
immediately. Browsers will warn. Replace it with a real certificate by putting
your full chain in `nginx/certs/server.crt` and the key in
`nginx/certs/server.key`, then `docker compose restart edge`.

If you already terminate TLS at a load balancer, point it at the `edge`
container's HTTP port (`HTTP_PORT`, default 80) and ignore `HTTPS_PORT`.

### Building from source instead

If you have both repositories checked out side by side:

```bash
docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build
```

The overlay only swaps published images for local build contexts; every
hardening setting still applies, so what you test is what ships.

### What the hardening means for you

Every container runs as a non-root user with a **read-only root filesystem**.
The only writable paths are the named volumes and the `tmpfs` mounts declared
per service. If you add a sidecar or a plugin that needs to write somewhere
else, add an explicit `tmpfs` entry for that path rather than removing
`read_only` — the setting is what stops a compromised process persisting
anything.

The compose file also splits the stack across four networks. `assetiq-data` and
`assetiq-internal` are `internal: true`, meaning containers on them have no
route off the host at all. Only `edge` is attached to published ports. For a
fully air-gapped installation you can delete the `assetiq-egress` network and
the backend's reference to it; the licence never needs it.

---

## Install with Helm

The chart is at `infra/helm/assetiq/`, and its README covers the values in
detail. The short version:

```bash
# 1. Create the Secret the chart requires. It templates no secret values itself
#    and will refuse to render without this.
kubectl create secret generic assetiq-backend \
  --namespace assetiq \
  --from-literal=spring-datasource-password='...' \
  --from-literal=app-jwt-secret="$(openssl rand -hex 32)" \
  --from-literal=app-data-encryption-key="$(openssl rand -base64 32)" \
  --from-literal=redis-password='...'

# 2. Install.
helm upgrade --install assetiq infra/helm/assetiq \
  --namespace assetiq --create-namespace \
  --values infra/helm/assetiq/values-production.yaml \
  --set backend.existingSecret=assetiq-backend \
  --set image.registry=public.ecr.aws/assetiq \
  --set image.tag=1.4.2 \
  --set ingress.host=assetiq.example.com \
  --set backend.database.host=postgres.internal \
  --set backend.redis.host=redis.internal
```

If you run External Secrets, set `externalSecret.enabled=true` and point it at
your store instead of creating the Secret by hand; the chart ships an example
with remote key *names* only.

`values-production.yaml` is the one to use for anything real: two replicas per
tier, HPA on, PodDisruptionBudgets, NetworkPolicies, a ServiceMonitor, TLS, and
zone spreading. The default `values.yaml` is single-replica and meant for
evaluation.

### Why no bundled database

The chart has no PostgreSQL or Redis subchart. A bundled database is a
stateful, unbacked-up datastore whose lifetime is tied to `helm uninstall`, and
the first time that matters is the worst possible time. Point the chart at a
managed service (RDS, Cloud SQL, ElastiCache) or an operator-managed cluster
you back up deliberately.

### Migrations under Helm

There is no migration Job. Flyway runs on backend startup and takes a database
advisory lock, so concurrent replicas are safe — the first one migrates, the
others wait. The consequence worth knowing is that **the startup probe budget is
the migration budget**. The chart allows 5 minutes by default
(`backend.startupProbe`); a migration slower than that turns into a crash loop.
If you are upgrading across a release with a large data migration, raise
`backend.startupProbe.failureThreshold` first.

---

## First run

1. Open your public URL. You will get the setup screen.
2. Create the first organisation and its administrator account.
3. Sign in.

Before you onboard real users, deal with these two defaults. Both are off
because they need your input, and both are the kind of thing that is discovered
at the worst moment if left.

### Email

**Password reset and user invitation emails require SMTP.** With
`APP_EMAIL_ENABLED=false` (the default) you can still create users, but nobody
can self-serve a forgotten password — an administrator must reset it. Set
`SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` and `APP_EMAIL_ENABLED=true`.

### Object storage — read this one

**AssetIQ has no local-disk storage backend.** With `APP_STORAGE_S3_ENABLED=false`
(the default), generated reports and imports are held in the backend's JVM heap.
That means:

- every generated file is lost when the container restarts;
- a second backend replica returns 404 for a file the first one produced;
- the map is never evicted, so it is an unbounded path to an out-of-memory kill.

This is acceptable while you evaluate. It is not acceptable in production, and
nothing in the application will warn you about it at runtime.

The fix does not require leaving your own network. The compose stack bundles
MinIO, which speaks S3, behind an optional profile:

```bash
docker compose --profile minio up -d

# Create the bucket, once.
docker compose exec minio mc alias set local http://127.0.0.1:9000 \
  "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
docker compose exec minio mc mb --ignore-existing local/assetiq
```

`bootstrap.sh` has already written MinIO credentials and the matching
`APP_STORAGE_S3_*` values into `.env`. Flip `APP_STORAGE_S3_ENABLED=true` and
restart the backend:

```bash
docker compose up -d --force-recreate backend
```

If you would rather use AWS S3, or any other S3-compatible store, set
`APP_STORAGE_S3_BUCKET`, `APP_STORAGE_S3_REGION` and (for a non-AWS store)
`APP_STORAGE_S3_ENDPOINT` with `APP_STORAGE_S3_PATH_STYLE=true`. On AWS with an
instance profile or IRSA, leave `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
blank and let the SDK resolve the role.

On Kubernetes, `values-production.yaml` sets `storage.s3.enabled: true` with an
empty bucket on purpose, so a deployment that forgot to configure storage fails
at the application rather than silently writing into a pod-local heap.

---

## Licence key

The self-hosted build determines its plan from a **signed offline licence key**.

- It is a self-contained signed document carrying your organisation name, plan
  tier, seat count and an expiry date.
- The backend verifies its signature **locally**, against a public key baked
  into the image. There is no licence server, no activation call, no periodic
  check-in and no telemetry. An air-gapped installation behaves identically to a
  connected one, because neither one sends anything.
- It is not a secret in the usual sense — it grants only your own entitlement —
  but treat it as confidential.

Install it by setting `APP_LICENSE_OFFLINE_KEY` in `.env` (or in the Kubernetes
Secret) and restarting the backend. `bootstrap.sh --licence <key>` sets it at
install time.

### What happens without a valid key

Nothing breaks. This is the important part, so it is spelled out:

| Situation | Result |
|---|---|
| No key configured | Free-tier quotas. A log line says so. |
| Key expired | Free-tier quotas. The log names the expiry date. |
| Key altered in transit | Free-tier quotas. The log says the signature did not verify. |
| Key issued for a different build | Free-tier quotas, same message. |
| Image has no licence public key at all | Free-tier quotas. The application still starts. |

In every case the application **keeps running, keeps serving, and keeps all of
your data**. Nothing is deleted, no feature is switched off destructively, and
there is no hard stop. The only consequence is that free-tier limits on assets,
users and departments apply until a current key is supplied. Supplying one
restores your plan on the next restart.

A key that lapses while the application is running is honoured as expired
without needing a restart — expiry is re-evaluated on each check.

### Seat counts

If your key carries a seat count, the effective seat limit is the **lower** of
your plan's allowance and your key's count. A BUSINESS key sold for 25 seats
gives you 25 seats, not the catalogue's 250.

### Where the key comes from

Your vendor issues it. If you are the vendor, see
`scripts/licence/issue-offline-licence.sh`, which both generates the keypair and
signs keys. The private key is the root of trust for every licence you will ever
issue; keep it in a password manager or a KMS, never in a repository or a build
image. Only the public half is baked into the release image, and the repository
deliberately contains neither.

---

## Upgrading

### Read this first

**Migrations run automatically on backend startup, and they are expand-only.**

Expand-only means a migration adds tables, columns and indexes but never drops
or narrows anything that the previous release still reads. The practical
consequences:

- The new version can start against the old schema's data without a maintenance
  window for the schema change itself.
- Rolling back the *application* to the previous image is safe, because the old
  code still works against the expanded schema.
- Rolling back the *database* to a pre-migration snapshot is **not** safe if the
  new version has already written data. Restore the database only as part of a
  full point-in-time restore, not as an "undo migration" step.

Hibernate is configured to `validate`, never to mutate. If the schema does not
match what the code expects, the backend refuses to start and says so, rather
than quietly altering your tables.

### Docker Compose

```bash
cd assetiq-standalone

# 1. Always back up first. See the next section.
./scripts/backup.sh

# 2. Change the pinned version.
sed -i 's/^ASSETIQ_VERSION=.*/ASSETIQ_VERSION=1.5.0/' .env

# 3. Pull and restart. Compose recreates only what changed.
docker compose pull
docker compose up -d

# 4. Watch the migration.
docker compose logs -f backend
```

Expect the backend to be unavailable for as long as the migration takes. The
healthcheck allows 180 seconds before it starts failing the container; a
migration longer than that will restart the container mid-flight, so for a large
upgrade raise `start_period` in `docker-compose.yml` first.

### Kubernetes

```bash
helm upgrade assetiq infra/helm/assetiq \
  --namespace assetiq \
  --values infra/helm/assetiq/values-production.yaml \
  --set backend.existingSecret=assetiq-backend \
  --set image.tag=1.5.0 \
  --reuse-values
```

The first new pod migrates; the rest wait on Flyway's lock. See the note above
about the startup probe budget.

### Rolling back

Set the previous tag and redeploy. Because migrations are expand-only, the older
image runs against the newer schema. Do not restore the database as part of a
rollback unless you have decided to lose everything written since the snapshot.

---

## Backup and restore

Two things must be backed up, and **both are required to recover**:

1. **The PostgreSQL database.**
2. **`APP_DATA_ENCRYPTION_KEY`.**

A database backup restored without that key leaves every encrypted column —
MFA secrets, SSO and webhook secrets, payment gateway tokens — permanently
unreadable. There is no recovery path, no escrow, and no support process that
can reconstruct it. Store it the way you would store a password database, in a
different place from the database dump.

If you use the bundled MinIO, also back up its `minio_data` volume — or,
better, use `mc mirror` to copy the bucket somewhere off this host. With
`APP_STORAGE_S3_ENABLED=false` there is nothing to back up, because generated
files only ever existed in the backend's memory.

### Backing up

```bash
cd assetiq-standalone
./scripts/backup.sh
```

Or by hand:

```bash
# Database
docker compose exec -T postgres \
  pg_dump -U assetiq -d assetiq --format=custom --compress=9 \
  > assetiq-$(date -u +%Y%m%dT%H%M%SZ).dump

# Object storage, if you run the bundled MinIO
docker run --rm \
  -v assetiq_minio_data:/data:ro \
  -v "$PWD":/backup \
  alpine tar czf /backup/assetiq-minio-$(date -u +%Y%m%dT%H%M%SZ).tar.gz -C /data .
```

Verify the dump is readable *before* you rely on it — a backup you have never
restored is a hypothesis:

```bash
pg_restore --list assetiq-20260923T120000Z.dump | head
```

### Restoring

```bash
cd assetiq-standalone

# 1. Stop the application, leave the database running.
docker compose stop backend web edge

# 2. Restore into a clean database. --clean drops existing objects first.
docker compose exec -T postgres \
  pg_restore -U assetiq -d assetiq --clean --if-exists --no-owner \
  < assetiq-20260923T120000Z.dump

# 3. Restore object storage, if you run the bundled MinIO.
docker compose --profile minio stop minio
docker run --rm -v assetiq_minio_data:/data -v "$PWD":/backup \
  alpine sh -c 'rm -rf /data/* && tar xzf /backup/assetiq-minio-*.tar.gz -C /data'

# 4. Confirm .env holds the SAME APP_DATA_ENCRYPTION_KEY the dump was taken
#    under. If it does not, stop here — starting up will not fail, but every
#    encrypted field will be garbage.

# 5. Start.
docker compose up -d
```

On Kubernetes the same applies, using your database's own restore procedure and
the same warning about the encryption key.

### Restore drill

Restore into a throwaway copy at least once a quarter. Point a second compose
project at the dump, start it, log in, and open a record with an encrypted field
(a user with MFA enabled is a good test). That exercise is the only way to find
out you have been backing up the database but not the key.

---

## Troubleshooting

### The backend will not start

Read the logs first: `docker compose logs backend` or
`kubectl logs -n assetiq deploy/assetiq-backend`. The application validates its
configuration at startup and refuses to boot on anything unsafe, so most
failures name themselves.

| Message contains | Cause | Fix |
|---|---|---|
| `JWT secret validation failed` | `APP_JWT_SECRET` is too short or too low-entropy | `openssl rand -hex 32` |
| `APP_DATA_ENCRYPTION_KEY must be Base64 for exactly 32 random bytes` | Wrong format — often hex instead of Base64 | `openssl rand -base64 32` |
| `ddl-auto='update' is NOT allowed` | Someone set Hibernate to mutate the schema | Set `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`; Flyway owns the schema |
| `app.storage.s3.enabled=false in cloud mode` | The install is not declaring itself self-hosted | Set `APP_LICENSE_OFFLINE_ENABLED=true` (the compose stack does), or configure object storage |
| `Validate failed ... applied migration ... has a different checksum` | The database was migrated by a different build | See below |
| Connection refused to `postgres:5432` | Backend started before the database was ready | The compose file already gates on health; if you changed it, restore `depends_on: condition: service_healthy` |

### Flyway checksum mismatch

This means the database has a migration recorded whose contents differ from the
one in the running image. Almost always it is one of:

- Running an older image against a database a newer one already migrated. Fix by
  going back to the newer image; do not "repair" it.
- A build from a modified source tree. Fix by running a released image.

Do not run `flyway repair` against production data without understanding which
of the two it is. Take a backup first, either way.

### The UI loads but every request fails with 401 or a CORS error

The API and the UI are not on the same origin. The session cookie is
`SameSite=Strict`, so the browser is refusing to send it. Check that
`APP_PUBLIC_URL` matches the hostname users actually type, including the scheme,
and that your proxy serves `/api` from the same hostname as `/`.

### The browser warns that the certificate is not trusted

`bootstrap.sh` generated a self-signed certificate. Replace
`nginx/certs/server.crt` and `nginx/certs/server.key` with a real pair and
`docker compose restart edge`.

### A container exits immediately with a read-only filesystem error

Something is trying to write outside the declared `tmpfs` and volume mounts. Add
an explicit `tmpfs` entry for that exact path. Do not remove `read_only: true` —
that trades a one-line fix for the property that makes the container
tamper-resistant.

### Everything works but limits are lower than the plan I bought

The licence key is absent, expired, or did not verify. Search the backend log
for `[LICENCE]`; the line says which and what to do. The application is
deliberately running normally on free-tier quotas rather than stopping. See
[Licence key](#licence-key).

### Health endpoints

| Endpoint | Meaning |
|---|---|
| `/actuator/health/liveness` | The process is alive. Does **not** check the database, deliberately: a database outage must not make an orchestrator restart every replica and turn a recoverable failure into a crash loop. |
| `/actuator/health/readiness` | Ready to serve: includes the database and Redis. This is what a load balancer should use. |
| `/actuator/prometheus` | Metrics. |

`/actuator/health` and its sub-paths are unauthenticated. If you expose
AssetIQ publicly, deny `/actuator` at your proxy and scrape metrics from inside
your network — the Helm chart's `ServiceMonitor` does exactly that.

### Getting help

Include, from a failing installation: the output of `docker compose ps`, the
last 200 lines of `docker compose logs backend`, your `ASSETIQ_VERSION`, and the
value of `latest migration` from
`SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1`.

Never include `.env`, your `APP_DATA_ENCRYPTION_KEY`, or your `APP_JWT_SECRET`.

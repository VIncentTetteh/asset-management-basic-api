# AssetIQ Helm chart

Deploys the AssetIQ Enterprise Asset Manager: a Spring Boot 3.3 / Java 21 API
and a statically exported Next.js UI served by nginx, behind a single Ingress
host.

| | |
|---|---|
| Chart version | `0.1.0` |
| Kubernetes | `>= 1.30` (see [Kubernetes version](#kubernetes-version)) |
| Helm | `>= 3.14` (validated on Helm v4.0.4) |
| Dependencies | **none** — PostgreSQL 16 and Redis 7 are external |

---

## What it creates

| Object | Guard |
|---|---|
| `Deployment` backend / web | `backend.enabled` / `web.enabled` |
| `Service` backend:8080 / web:3000 (`ClusterIP`, port name `http`) | as above |
| `ConfigMap` backend (non-secret env only) | `backend.enabled` |
| `ServiceAccount` backend / web | `*.serviceAccount.create` |
| `Ingress` (one host, `/api` + `/actuator` → backend, `/` → web) | `ingress.enabled` |
| `PodDisruptionBudget` backend / web | `*.pdb.enabled` |
| `HorizontalPodAutoscaler` backend / web (`autoscaling/v2`) | `*.autoscaling.enabled` |
| `NetworkPolicy` × 5 (default-deny + explicit allows) | `networkPolicy.enabled` |
| `ServiceMonitor` backend only | `metrics.serviceMonitor.enabled` |
| `ExternalSecret` (example, remote key names only) | `externalSecret.enabled` |

The chart **never creates a Secret** and ships no secret values.

---

## Quick start

```bash
CHART=infra/helm/assetiq

# 1. Create the Secret the backend needs (names, not values, are in the chart).
kubectl -n assetiq create secret generic assetiq-backend \
  --from-literal=spring-datasource-password="$(...)" \
  --from-literal=app-jwt-secret="$(openssl rand -base64 48)" \
  --from-literal=app-data-encryption-key="$(openssl rand -base64 32)"

# 2. Install.
helm upgrade --install assetiq $CHART \
  -n assetiq --create-namespace \
  -f $CHART/values-production.yaml \
  --set image.registry=123456789012.dkr.ecr.eu-central-1.amazonaws.com \
  --set image.tag="$(git describe --tags --always)" \
  --set backend.existingSecret=assetiq-backend \
  --set ingress.host=assetiq.example.com \
  --set backend.app.corsAllowedOrigins=https://assetiq.example.com
```

Omitting `backend.existingSecret` fails at render time, by design:

```
Error: execution error at (assetiq/templates/backend-deployment.yaml:3:19):
backend.existingSecret is required. Create a Secret (or enable externalSecret)
holding at least spring-datasource-password, app-jwt-secret and
app-data-encryption-key, then set backend.existingSecret to its name. This
chart ships no secret values.
```

`image.tag` is likewise hard-gated: an empty tag or `latest` aborts the render.

---

## Secrets

`backend.secretEnv` maps an env var name to a **key inside an existing Secret**.
Defaults:

| Env var | Secret key | Required |
|---|---|---|
| `SPRING_DATASOURCE_PASSWORD` | `spring-datasource-password` | yes |
| `APP_JWT_SECRET` | `app-jwt-secret` | yes |
| `APP_DATA_ENCRYPTION_KEY` | `app-data-encryption-key` | yes |
| `REDIS_PASSWORD` | `redis-password` | optional |
| `PAYSTACK_SECRET_KEY` | `paystack-secret-key` | optional |
| `APP_LICENSE_OFFLINE_KEY` | `app-license-offline-key` | only when `backend.license.offlineEnabled` |

`APP_LICENSE_OFFLINE_KEY` is omitted from the pod spec entirely unless
`backend.license.offlineEnabled=true`, so an unused integration cannot block
startup.

### External Secrets Operator

`templates/externalsecret-example.yaml` (guarded by `externalSecret.enabled`)
shows how ESO materialises that Secret from AWS Secrets Manager or Vault. It
contains **remote key names only**. `target.name` is
`backend.existingSecret`, so both must agree.

It renders `apiVersion: external-secrets.io/v1` (GA, ESO >= 0.14). On an older
operator set `externalSecret.apiVersion: external-secrets.io/v1beta1`.

> The chart does **not** hash the Secret into `checksum/config`: it never reads
> Secret contents. Rotating a secret needs a `kubectl rollout restart` (or ESO's
> `reloader`-style annotation on your side).

---

## Database migrations — there is no migration Job

Flyway runs inside the backend on startup and takes a **PostgreSQL advisory
lock** before applying anything. Concurrent replicas are therefore safe: the
first to acquire the lock migrates, the rest block and then continue. Adding a
`pre-install`/`pre-upgrade` migration Job or init container would be redundant
and would break `helm rollback` (the Job would have to be idempotent and
version-aware to be correct).

Consequences a reviewer should be aware of:

- The **startup probe**, not the liveness probe, carries the migration budget
  (`backend.probes.startup`: 5s × 60 = 5 minutes by default). A migration
  slower than that will crash-loop the rollout — raise `failureThreshold`
  before shipping a large migration.
- `SPRING_JPA_HIBERNATE_DDL_AUTO` is pinned to `validate`. Setting it to
  `update` lets Hibernate mutate a production schema behind Flyway's back; the
  NOTES output warns when it is changed.
- `maxUnavailable: 0` plus a backward-compatible migration (expand/contract)
  is what makes a zero-downtime rollout possible. A destructive migration
  still requires a maintenance window.

---

## Probes

| Probe | Path | Touches DB? |
|---|---|---|
| startup | `/actuator/health/readiness` | yes |
| liveness | `/actuator/health/liveness` | **no** |
| readiness | `/actuator/health/readiness` | yes (db + redis) |

The liveness split is deliberate: a Postgres or Redis outage must take pods out
of the Service (readiness) without restarting them (liveness). A liveness probe
on `/actuator/health` would turn a database blip into a cluster-wide
crash-loop.

---

## Security posture

- Pod and container `securityContext` on both workloads: `runAsNonRoot`,
  uid/gid/fsGroup `10001`, `seccompProfile: RuntimeDefault`,
  `allowPrivilegeEscalation: false`, `capabilities: drop [ALL]`,
  `readOnlyRootFilesystem: true`.
- Writable paths are `emptyDir` only:
  backend `/tmp`, `/app/uploads`; web `/var/cache/nginx`, `/var/run`, `/tmp`.
- `automountServiceAccountToken: false` on both SAs and both pod specs —
  neither workload talks to the Kubernetes API.
- Both `requests` and `limits` on every container.
- nginx listens on **3000**, an unprivileged port, so no `NET_BIND_SERVICE`.
- `imagePullPolicy: IfNotPresent`; `latest` is rejected by the `assetiq.image`
  helper.

### NetworkPolicy

`networkPolicy.enabled=true` renders:

1. **default-deny** ingress *and* egress for every pod in the release
   (`assetiq.releaseSelectorLabels`);
2. DNS egress (UDP **and** TCP 53) for every pod;
3. ingress-controller → `web:3000`;
4. ingress-controller (+ Prometheus namespace) → `backend:8080`;
5. backend egress → Postgres `5432`, Redis `6379`, optional `443`
   (`networkPolicy.allowExternalHttps`) and optional SMTP.

The web workload gets **no egress other than DNS** — the static export talks to
the API through the browser, same-origin, never pod-to-pod.

When `allowExternalHttps` is on and `externalHttpsCidrs` is empty the rule is
`0.0.0.0/0` with RFC1918 and `169.254.169.254/32` in `except`, which keeps the
pod off the cloud metadata endpoint. Narrow it to real destinations (or a NAT /
egress-proxy CIDR) in any regulated environment.

> **These objects are inert on a CNI that does not enforce NetworkPolicy.**
> The API server accepts them silently. Verify enforcement with Calico/Cilium
> before treating this as a control.

---

## Ingress and the single-host rule

The browser session is an `HttpOnly; SameSite=Strict` cookie, so the UI and the
API **must** be same-origin. The chart enforces one host with three ordered
`Prefix` paths:

```
/api      -> backend Service
/actuator -> backend Service
/          -> web Service
```

`/actuator` is exposed through the Ingress so Prometheus can scrape it without
a second route — **restrict it at the edge**. Spring's `prod` profile exposes
only the health and prometheus endpoints, but an ingress-level allow-list or
auth annotation on `/actuator` is the safer belt-and-braces. Consider:

```yaml
ingress:
  annotations:
    nginx.ingress.kubernetes.io/server-snippet: |
      location /actuator { deny all; }
```

…and scrape via the `ServiceMonitor` (in-cluster) instead.

`ingress.enabled` with an empty `ingress.host`, or `ingress.tls.enabled` with
an empty `ingress.tls.secretName`, fails the render.

---

## Observability

`metrics.serviceMonitor.enabled=true` creates a `ServiceMonitor` for the
**backend only** (the web pod is a static nginx with nothing to scrape),
scraping the Service port named `http` at `/actuator/prometheus`.

Set `metrics.serviceMonitor.labels` to whatever your Prometheus Operator's
`serviceMonitorSelector` matches (e.g. `release: kube-prometheus-stack`) or the
object will be created and quietly ignored.

---

## Production overlay

`values-production.yaml` turns on everything the defaults leave off:

| | dev default | production |
|---|---|---|
| backend / web replicas | 1 / 1 | 2 / 2 |
| HPA | off | on (backend 2–8, web 2–6) |
| PDB | off | `minAvailable: 1` |
| NetworkPolicy | off | on |
| ServiceMonitor | off | on |
| Ingress TLS | off | on (cert-manager) |
| topologySpreadConstraints | none | zone `DoNotSchedule` + hostname `ScheduleAnyway` |
| backend resources | 250m/768Mi → 1/1.5Gi | 500m/1.5Gi → 2/2Gi |
| S3 storage | off | on |

Still environment-specific and **wrong by default** in that file:
`image.registry`, `image.tag`, `backend.existingSecret`, `ingress.host`,
`ingress.tls.secretName`, `backend.database.url`/`.host`,
`backend.redis.host`, `backend.app.corsAllowedOrigins`,
`backend.storage.s3.bucket`, and the `networkPolicy.postgres/redis.cidrs`
placeholders (`10.0.16.0/20`).

Note that map-valued overrides in that file use `null`, not `{}` — Helm
deep-merges maps, so `{}` would leave the default selector in place and widen
the NetworkPolicy.

---

## Uploads and replica count

With `backend.storage.s3.enabled=false`, `/app/uploads` is a per-pod
`emptyDir`: files are invisible to the other replicas and lost on restart. Any
deployment with more than one backend replica **must** enable S3 (and give the
ServiceAccount an IRSA role scoped to that bucket). `NOTES.txt` warns when this
combination is detected.

---

## Kubernetes version

Both pod specs use the `lifecycle.preStop.sleep` handler, which is beta and
enabled by default from **Kubernetes 1.30** (alpha in 1.29). On an older
cluster either upgrade or set `backend.preStopSleepSeconds=0` and
`web.preStopSleepSeconds=0` — the handler is omitted entirely at `0`, at the
cost of a small window where the ingress controller still routes to a
terminating pod.

---

## Verification

```bash
CHART=infra/helm/assetiq
helm lint $CHART --set backend.existingSecret=assetiq-backend
helm lint $CHART -f $CHART/values-production.yaml --set backend.existingSecret=assetiq-backend
helm template assetiq $CHART --set backend.existingSecret=assetiq-backend | kubeconform -strict -summary
helm template assetiq $CHART -f $CHART/values-production.yaml --set backend.existingSecret=assetiq-backend \
  | kubeconform -strict -summary -schema-location default \
      -schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json'
```

The second `kubeconform` invocation needs the CRD catalogue because
`ServiceMonitor` and `ExternalSecret` are custom resources.

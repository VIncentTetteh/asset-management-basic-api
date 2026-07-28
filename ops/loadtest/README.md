# AssetIQ load testing

Read-heavy endpoint load test for the go-live capacity check (market-readiness
plan #14). Drives the asset list, dashboard summary, and asset stats paths.

## Run

```bash
# install k6: https://k6.io/docs/get-started/installation/  (brew install k6)
k6 run \
  -e BASE_URL=https://staging-api.assetiq.io \
  -e EMAIL=loadtest@yourorg.com \
  -e PASSWORD='...' \
  ops/loadtest/read-endpoints.js
```

The default profile ramps 0 → 50 → 200 → 500 VUs. Thresholds fail the run if
error rate exceeds 1% or p95 exceeds 500ms (800ms for the heavier stats path).

## Rate-limit caveat (read before trusting throughput numbers)

Authenticated traffic is rate-limited (default **100/min per principal**, see
`RateLimitingInterceptor` / `RedisRateLimiter`). Against a single load-test
user with the default limit you measure the limiter, not the server. Before a
real capacity run either:

- raise the limit for the load-test user/IP in the target environment, or
- fan the login across many seeded users (extend `setup()`).

## Local baseline (2026-07-25, ApacheBench, dev docker Postgres, 48 assets)

Latency at concurrency 10 (n=90 per endpoint, 0 failures). This is a **dev
baseline for regression comparison**, not the capacity plan — the numbers above
require staging with the rate limiter tuned.

| Endpoint | p50 | p95 | p99 | req/s @ c=10 |
|---|---|---|---|---|
| `GET /api/v1/assets?size=20` | 181ms | 327ms | 336ms | 48 |
| `GET /api/v1/dashboard/summary` | 54ms | 102ms | 163ms | 133 |
| `GET /api/v1/assets/stats` | 327ms | 576ms | 830ms | 25 |

### Finding

`/api/v1/assets/stats` is the slowest read (p99 830ms with only 48 assets),
markedly heavier than the dashboard summary. It does per-request aggregation
that will degrade as asset counts grow — profile and cache/precompute it before
onboarding a large tenant.

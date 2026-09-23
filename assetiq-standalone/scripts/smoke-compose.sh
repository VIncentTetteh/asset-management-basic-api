#!/usr/bin/env bash
#
# smoke-compose.sh — prove the self-hosted stack actually comes up.
#
# Brings Postgres, Redis and the backend up from source, waits for the backend
# to report healthy, and asserts that Flyway migrated against the composed
# Postgres rather than the app silently starting on an empty schema. Tears the
# stack down on exit, pass or fail.
#
# Run from anywhere:   ./assetiq-standalone/scripts/smoke-compose.sh
# Or via the Makefile: make selfhosted-smoke
#
# Uses its own compose project name and a throwaway .env, so it cannot touch a
# real installation's volumes or secrets.

set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

PROJECT="assetiq-smoke-$$"
ENV_FILE="$(mktemp -t assetiq-smoke-env.XXXXXX)"
COMPOSE=(docker compose -p "$PROJECT" --env-file "$ENV_FILE"
         -f docker-compose.yml -f docker-compose.build.yml)

cleanup() {
  local status=$?
  echo ""
  echo "==> tearing down"
  "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  rm -f "$ENV_FILE"
  exit "$status"
}
trap cleanup EXIT INT TERM

info() { echo "==> $*"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

# ── Throwaway configuration ──────────────────────────────────────────────────
# Same formats the real bootstrap generates and the app validates, but these
# secrets live and die with this script.
umask 077
cat > "$ENV_FILE" <<EOF
ASSETIQ_VERSION=0.0.0-smoke
APP_PUBLIC_URL=https://localhost
APP_CORS_ALLOW_LOCALHOST=true
POSTGRES_DB=assetiq
POSTGRES_USER=assetiq
POSTGRES_PASSWORD=$(openssl rand -hex 24)
REDIS_PASSWORD=$(openssl rand -hex 24)
APP_JWT_SECRET=$(openssl rand -hex 32)
APP_DATA_ENCRYPTION_KEY=$(openssl rand -base64 32 | tr -d '\n')
APP_LICENSE_OFFLINE_ENABLED=true
APP_LICENSE_OFFLINE_KEY=
EOF

info "building and starting postgres, redis, backend (project ${PROJECT})"
"${COMPOSE[@]}" up -d --build postgres redis backend

# ── Wait for health ──────────────────────────────────────────────────────────
info "waiting for the backend to report healthy (up to 5 minutes)"
deadline=$(( $(date +%s) + 300 ))
while :; do
  # `docker compose ps --format json` emits NDJSON on some versions and a JSON
  # array on others, and omits stopped containers without --all — which is
  # exactly the case this loop has to detect.
  state="$("${COMPOSE[@]}" ps --all --format json backend 2>/dev/null \
            | python3 -c '
import sys, json
raw = sys.stdin.read().strip()
if not raw:
    print("unknown"); raise SystemExit
records = []
try:
    parsed = json.loads(raw)
    records = parsed if isinstance(parsed, list) else [parsed]
except json.JSONDecodeError:
    for line in raw.splitlines():
        line = line.strip()
        if line:
            records.append(json.loads(line))
print((records[0].get("Health") or records[0].get("State") or "unknown")
      if records else "unknown")
' 2>/dev/null || echo unknown)"

  case "$state" in
    healthy) info "backend is healthy"; break ;;
    exited|dead)
      "${COMPOSE[@]}" logs --tail 80 backend
      fail "backend container exited" ;;
    restarting)
      # `restart: unless-stopped` turns a startup crash into a restart loop,
      # which otherwise only surfaces as a five-minute timeout with no
      # explanation. Spring prints this line once per failed refresh, so
      # seeing it at all means the app is not going to come up on its own.
      if "${COMPOSE[@]}" logs backend 2>/dev/null | grep -q "Application run failed"; then
        "${COMPOSE[@]}" logs --tail 40 backend | grep -E "Application run failed|Caused by|BeanCreationException" | head -5
        fail "backend is crash-looping on startup (see the lines above)"
      fi
      ;;
  esac

  [ "$(date +%s)" -lt "$deadline" ] || {
    "${COMPOSE[@]}" logs --tail 120 backend
    fail "backend did not become healthy within 5 minutes (last state: $state)"
  }
  sleep 5
done

# ── Assert Flyway actually migrated ──────────────────────────────────────────
# A healthy container is not proof of a migrated schema: ddl-auto=validate would
# fail loudly, but a misconfigured datasource pointing somewhere else would not.
info "checking Flyway's schema history in the composed Postgres"
applied="$("${COMPOSE[@]}" exec -T postgres \
  psql -U assetiq -d assetiq -tAc \
  "SELECT count(*) FROM flyway_schema_history WHERE success" 2>/dev/null | tr -d '[:space:]')"

case "$applied" in
  ''|*[!0-9]*) fail "could not read flyway_schema_history (got: '${applied:-<empty>}')" ;;
esac
if [ "$applied" -eq 0 ]; then fail "flyway_schema_history is empty — no migration ran"; fi
info "flyway applied ${applied} migrations successfully"

latest="$("${COMPOSE[@]}" exec -T postgres \
  psql -U assetiq -d assetiq -tAc \
  "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL
   ORDER BY installed_rank DESC LIMIT 1" | tr -d '[:space:]')"
info "latest migration: V${latest}"

# ── Assert the app is really serving ─────────────────────────────────────────
info "checking the liveness endpoint through the container"
if ! "${COMPOSE[@]}" exec -T backend \
      wget -qO- http://127.0.0.1:8080/actuator/health/liveness | grep -q '"status":"UP"'; then
  fail "liveness endpoint did not report UP"
fi
info "liveness reports UP"

# ── Assert the hardening actually held ───────────────────────────────────────
info "checking the container is non-root with a read-only root filesystem"
uid="$("${COMPOSE[@]}" exec -T backend id -u | tr -d '[:space:]')"
if [ "$uid" = "0" ]; then fail "backend is running as root"; fi
info "backend runs as uid ${uid}"

# Explicit if, not `cmd && fail`: the expected outcome here is a NON-zero exit,
# and an AND-list whose left side fails takes the whole script down under set -e.
if "${COMPOSE[@]}" exec -T backend sh -c 'touch /readonly-probe' 2>/dev/null; then
  fail "root filesystem is writable — read_only did not take effect"
fi
info "root filesystem is read-only"

if ! "${COMPOSE[@]}" exec -T backend sh -c 'touch /tmp/probe && rm /tmp/probe'; then
  fail "/tmp is not writable — the JVM needs it"
fi
info "/tmp is writable"

echo ""
echo "SMOKE TEST PASSED"

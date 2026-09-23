#!/usr/bin/env bash
#
# smoke-compose.sh — prove the self-hosted stack actually comes up.
#
# Brings Postgres, Redis and the backend up from source, waits for the backend
# to report healthy, and asserts that Flyway migrated against the composed
# Postgres rather than the app silently starting on an empty schema. Then
# rehearses backup.sh and restore.sh end to end, because an unrehearsed restore
# is a guess. Tears the stack down on exit, pass or fail.
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

BACKUP_DIR="$(mktemp -d -t assetiq-smoke-backups.XXXXXX)"

cleanup() {
  local status=$?
  echo ""
  echo "==> tearing down"
  "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  rm -f "$ENV_FILE"
  rm -rf "$BACKUP_DIR"
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

# ── Assert durable file storage is actually in use ───────────────────────────
# The backend refuses to start without a durable backend, so reaching this point
# means one is configured. Prove it is the filesystem one and that it is
# writable by the app's uid, rather than trusting the env var.
STORAGE_DIR=/app/uploads
info "checking the file storage directory is writable by the backend"
if ! "${COMPOSE[@]}" exec -T backend sh -c \
      "touch $STORAGE_DIR/.smoke-probe && rm $STORAGE_DIR/.smoke-probe"; then
  fail "$STORAGE_DIR is not writable — uploads would fail at runtime"
fi
info "$STORAGE_DIR is writable"

# ── Rehearse backup and restore ──────────────────────────────────────────────
# The failure being designed out: a database-only restore. document_attachments
# rows hold a storage key, not bytes, so a restore that brings back the rows and
# not the files produces a system that lists every document and 404s on all of
# them. This proves one command captures both and one command puts both back.
info "rehearsing backup.sh (database + file storage)"

MARKER_KEY="attachments/smoke/$(date +%s)/evidence.txt"
MARKER_BODY="restore-marker-$$"
"${COMPOSE[@]}" exec -T backend sh -c \
  "mkdir -p \"\$(dirname $STORAGE_DIR/$MARKER_KEY)\" && printf '%s' '$MARKER_BODY' > $STORAGE_DIR/$MARKER_KEY" \
  || fail "could not write the marker file into $STORAGE_DIR"

# A row that must survive the database half of the round trip.
MARKER_TABLE="smoke_restore_marker_$$"
"${COMPOSE[@]}" exec -T postgres psql -U assetiq -d assetiq -q -v ON_ERROR_STOP=1 \
  -c "CREATE TABLE \"$MARKER_TABLE\" (v text); INSERT INTO \"$MARKER_TABLE\" VALUES ('$MARKER_BODY');" \
  >/dev/null || fail "could not create the database marker table"

export ASSETIQ_COMPOSE_CMD="docker compose -p $PROJECT --env-file $ENV_FILE"
export ASSETIQ_COMPOSE_FILES="-f docker-compose.yml -f docker-compose.build.yml"
export ASSETIQ_BACKUP_DIR="$BACKUP_DIR"
export POSTGRES_USER=assetiq
POSTGRES_PASSWORD="$(grep '^POSTGRES_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
export POSTGRES_PASSWORD
export APP_STORAGE_FILESYSTEM_BASE_DIR="$STORAGE_DIR"
export ASSETIQ_RESTORE_ASSUME_YES=1

# backup.sh writes into its own repo-relative backups/ dir; point it at the
# throwaway one so a smoke run never drops files into a real installation.
( cd "$(pwd)" && ASSETIQ_BACKUP_DIR="$BACKUP_DIR" ./scripts/backup.sh ) \
  > "$BACKUP_DIR/backup.log" 2>&1 || { cat "$BACKUP_DIR/backup.log"; fail "backup.sh failed"; }

TS="$(grep -o 'Restore with: ./scripts/restore.sh [0-9_]*' "$BACKUP_DIR/backup.log" \
      | awk '{print $NF}')"
[ -n "$TS" ] || { cat "$BACKUP_DIR/backup.log"; fail "could not determine the backup timestamp"; }
info "backup set $TS written"

for f in "assetiq_$TS.sql.gz" "license_server_$TS.sql.gz" "storage_$TS.tar.gz" "manifest_$TS.txt"; do
  [ -s "$BACKUP_DIR/$f" ] || fail "backup set is missing or empty: $f"
done
info "backup set contains both databases and the storage archive"

# Destroy both halves, so a restore that only does one of them fails here.
info "destroying the marker file and the marker table"
"${COMPOSE[@]}" exec -T backend sh -c "rm -f $STORAGE_DIR/$MARKER_KEY" \
  || fail "could not remove the marker file"
"${COMPOSE[@]}" exec -T postgres psql -U assetiq -d assetiq -q \
  -c "DROP TABLE \"$MARKER_TABLE\";" >/dev/null || fail "could not drop the marker table"

info "running restore.sh $TS"
./scripts/restore.sh "$TS" > "$BACKUP_DIR/restore.log" 2>&1 \
  || { cat "$BACKUP_DIR/restore.log"; fail "restore.sh failed"; }

# The file must be back, byte for byte.
restored="$("${COMPOSE[@]}" exec -T backend sh -c "cat $STORAGE_DIR/$MARKER_KEY" 2>/dev/null \
            | tr -d '\r\n')"
[ "$restored" = "$MARKER_BODY" ] \
  || fail "the storage file was not restored (expected '$MARKER_BODY', got '${restored:-<nothing>}')"
info "file storage restored"

# And so must the row.
restored_row="$("${COMPOSE[@]}" exec -T postgres psql -U assetiq -d assetiq -tAc \
                "SELECT v FROM \"$MARKER_TABLE\"" 2>/dev/null | tr -d '[:space:]')"
[ "$restored_row" = "$MARKER_BODY" ] \
  || fail "the database row was not restored (got '${restored_row:-<nothing>}')"
info "database restored"

# A partial set must be refused rather than half-applied.
rm -f "$BACKUP_DIR/storage_$TS.tar.gz"
if ./scripts/restore.sh "$TS" >/dev/null 2>&1; then
  fail "restore.sh accepted a set with no storage archive — a database-only restore"
fi
info "restore.sh refuses an incomplete backup set"

echo ""
echo "SMOKE TEST PASSED"

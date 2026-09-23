#!/usr/bin/env bash
# backup.sh
#
# Captures a complete, restorable AssetIQ backup: both PostgreSQL databases AND
# the file storage volume, in one timestamped set.
#
# The files are not optional extras. document_attachments rows hold a storage
# key, not the bytes; restoring the database alone gives you a system that lists
# every contract, licence certificate, receipt and compliance document and 404s
# on all of them. So the two are taken together, under one timestamp, and
# restore.sh puts both back.
#
# Schedule with cron:  0 2 * * * /path/to/assetiq-standalone/scripts/backup.sh
#
# Output, all sharing one timestamp:
#   backups/assetiq_YYYYMMDD_HHMMSS.sql.gz
#   backups/license_server_YYYYMMDD_HHMMSS.sql.gz
#   backups/storage_YYYYMMDD_HHMMSS.tar.gz
#   backups/manifest_YYYYMMDD_HHMMSS.txt
#
# Restore with:  ./scripts/restore.sh YYYYMMDD_HHMMSS

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_DIR="$ROOT_DIR/backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RETENTION_DAYS="${ASSETIQ_BACKUP_RETENTION_DAYS:-30}"

mkdir -p "$BACKUP_DIR"

# Load env vars so we know the postgres password
if [ -f "$ROOT_DIR/.env" ]; then
  set -o allexport; source "$ROOT_DIR/.env"; set +o allexport
fi

PG_USER="${POSTGRES_USER:-assetiq}"
PG_PASS="${POSTGRES_PASSWORD:?POSTGRES_PASSWORD not set in .env}"
STORAGE_DIR="${APP_STORAGE_FILESYSTEM_BASE_DIR:-/app/uploads}"
# Both are overridable so the smoke test can drive these scripts against its
# own throwaway compose project instead of a real installation. Unquoted on
# purpose: they carry multiple words.
COMPOSE_CMD="${ASSETIQ_COMPOSE_CMD:-docker compose}"
if [ -z "${ASSETIQ_COMPOSE_CMD:-}" ] && command -v docker-compose &>/dev/null; then
  COMPOSE_CMD="docker-compose"
fi
COMPOSE_FILES="${ASSETIQ_COMPOSE_FILES:--f $ROOT_DIR/docker-compose.yml}"

# shellcheck disable=SC2086
compose() { $COMPOSE_CMD $COMPOSE_FILES "$@"; }

dump_db() {
  local db="$1"
  local file="$BACKUP_DIR/${db}_${TIMESTAMP}.sql.gz"
  echo "→ Backing up database: $db"
  PGPASSWORD="$PG_PASS" compose exec -T postgres \
    pg_dump -U "$PG_USER" "$db" | gzip > "$file"
  echo "  Written: $file"
}

dump_storage() {
  local file="$BACKUP_DIR/storage_${TIMESTAMP}.tar.gz"
  echo "→ Backing up file storage: $STORAGE_DIR"

  if [ "${APP_STORAGE_S3_ENABLED:-false}" = "true" ]; then
    echo "  APP_STORAGE_S3_ENABLED=true — files live in object storage, not on this"
    echo "  volume. Back up the bucket with your object-store tooling instead."
    echo "  (Writing an empty archive so the set stays consistent.)"
  fi

  # tar from inside the backend container: the volume is only mounted there, and
  # this avoids needing to know the volume's name or the host's docker root.
  # The exit status of tar is what matters, so pipefail is doing real work here.
  if ! compose exec -T backend tar czf - -C "$STORAGE_DIR" . > "$file"; then
    rm -f "$file"
    echo "  ERROR: could not read $STORAGE_DIR from the backend container." >&2
    echo "  The backend must be running for a storage backup." >&2
    return 1
  fi
  echo "  Written: $file ($(du -h "$file" | cut -f1))"
}

write_manifest() {
  local file="$BACKUP_DIR/manifest_${TIMESTAMP}.txt"
  {
    echo "assetiq_backup_version=1"
    echo "timestamp=$TIMESTAMP"
    echo "created_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "assetiq_version=${ASSETIQ_VERSION:-unknown}"
    echo "storage_base_dir=$STORAGE_DIR"
    echo "storage_s3_enabled=${APP_STORAGE_S3_ENABLED:-false}"
    echo "databases=assetiq license_server"
  } > "$file"
  echo "→ Manifest: $file"
}

dump_db assetiq
dump_db license_server
dump_storage
write_manifest

# Prune whole sets, not individual files: half a set is not a restorable backup.
find "$BACKUP_DIR" \( -name "*.sql.gz" -o -name "storage_*.tar.gz" -o -name "manifest_*.txt" \) \
     -mtime "+${RETENTION_DAYS}" -delete 2>/dev/null || true

echo ""
echo "✅  Backup complete: $TIMESTAMP"
echo "    Restore with: ./scripts/restore.sh $TIMESTAMP"

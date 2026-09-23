#!/usr/bin/env bash
# restore.sh
#
# Restores a backup set produced by backup.sh: both databases AND the file
# storage volume, from one timestamp.
#
# Restoring the database on its own is the failure this script exists to
# prevent. document_attachments rows hold a storage key, not bytes; a
# database-only restore produces a system that lists every contract, licence
# certificate, receipt and compliance document and 404s on all of them — and it
# looks fine until someone clicks a document.
#
# Usage:
#   ./scripts/restore.sh YYYYMMDD_HHMMSS          # restore that set
#   ./scripts/restore.sh --list                   # show available sets
#   ASSETIQ_RESTORE_ASSUME_YES=1 ./scripts/restore.sh <ts>   # no prompt (CI)
#
# THIS IS DESTRUCTIVE. It drops and recreates both databases and replaces the
# entire contents of the storage directory.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_DIR="${ASSETIQ_BACKUP_DIR:-$ROOT_DIR/backups}"

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

list_sets() {
  echo "Available backup sets in $BACKUP_DIR:"
  find "$BACKUP_DIR" -maxdepth 1 -name 'manifest_*.txt' 2>/dev/null \
    | sed 's#.*/manifest_##; s#\.txt$##' | sort -r \
    | while read -r ts; do
        printf '  %s  (%s)\n' "$ts" \
          "$(grep -h '^created_at_utc=' "$BACKUP_DIR/manifest_${ts}.txt" | cut -d= -f2)"
      done
}

if [ "${1:-}" = "--list" ] || [ "${1:-}" = "-l" ]; then
  list_sets
  exit 0
fi

TIMESTAMP="${1:-}"
if [ -z "$TIMESTAMP" ]; then
  echo "Usage: $0 YYYYMMDD_HHMMSS   (or --list)" >&2
  echo "" >&2
  list_sets >&2
  exit 2
fi

DB_ASSETIQ="$BACKUP_DIR/assetiq_${TIMESTAMP}.sql.gz"
DB_LICENSE="$BACKUP_DIR/license_server_${TIMESTAMP}.sql.gz"
STORAGE_ARCHIVE="$BACKUP_DIR/storage_${TIMESTAMP}.tar.gz"

# ── Refuse a partial set ─────────────────────────────────────────────────────
# A set missing its storage archive is not a restorable backup of a system that
# stores files. Saying so here is better than finding out from a user.
missing=()
[ -f "$DB_ASSETIQ" ]      || missing+=("$DB_ASSETIQ")
[ -f "$DB_LICENSE" ]      || missing+=("$DB_LICENSE")
[ -f "$STORAGE_ARCHIVE" ] || missing+=("$STORAGE_ARCHIVE")
if [ ${#missing[@]} -gt 0 ]; then
  echo "ERROR: backup set $TIMESTAMP is incomplete. Missing:" >&2
  printf '  %s\n' "${missing[@]}" >&2
  echo "" >&2
  echo "Restoring part of a set produces a database whose document rows point at" >&2
  echo "files that are not there. Refusing." >&2
  echo "" >&2
  list_sets >&2
  exit 1
fi

# ── Confirm ──────────────────────────────────────────────────────────────────
if [ "${ASSETIQ_RESTORE_ASSUME_YES:-0}" != "1" ]; then
  echo "About to restore backup set $TIMESTAMP."
  echo ""
  echo "  This DROPS and recreates the 'assetiq' and 'license_server' databases"
  echo "  and REPLACES everything under $STORAGE_DIR."
  echo ""
  read -r -p "Type 'restore' to continue: " confirm
  [ "$confirm" = "restore" ] || { echo "Aborted."; exit 1; }
fi

echo "==> stopping the backend so nothing writes during the restore"
compose stop backend >/dev/null 2>&1 || true

restore_db() {
  local db="$1" archive="$2"
  echo "→ Restoring database: $db"
  PGPASSWORD="$PG_PASS" compose exec -T postgres \
    psql -U "$PG_USER" -d postgres -v ON_ERROR_STOP=1 \
    -c "DROP DATABASE IF EXISTS \"$db\" WITH (FORCE);" \
    -c "CREATE DATABASE \"$db\" OWNER \"$PG_USER\";" >/dev/null
  gunzip -c "$archive" | PGPASSWORD="$PG_PASS" compose exec -T postgres \
    psql -U "$PG_USER" -d "$db" -v ON_ERROR_STOP=1 -q >/dev/null
  echo "  Restored: $db"
}

restore_storage() {
  echo "→ Restoring file storage into $STORAGE_DIR"
  # The backend is stopped, so use a throwaway container on the same volume
  # rather than exec-ing into it. `run --rm --no-deps` starts nothing else.
  # find -mindepth 1 -delete rather than rm -rf "$DIR"/*: the latter misses
  # dotfiles and would remove the mount point itself if the glob failed.
  compose run --rm --no-deps --entrypoint sh -T backend -c \
    "find '$STORAGE_DIR' -mindepth 1 -delete" >/dev/null
  gunzip -c "$STORAGE_ARCHIVE" | compose run --rm --no-deps --entrypoint sh -T backend -c \
    "tar xzf - -C '$STORAGE_DIR'"
  local count
  count="$(compose run --rm --no-deps --entrypoint sh -T backend -c \
    "find '$STORAGE_DIR' -type f | wc -l" | tr -d '[:space:]')"
  echo "  Restored: $count file(s)"
}

restore_db assetiq        "$DB_ASSETIQ"
restore_db license_server "$DB_LICENSE"
restore_storage

echo "==> starting the backend"
compose up -d backend >/dev/null

echo ""
echo "✅  Restore complete from set $TIMESTAMP."
echo "    Verify before declaring success: open a document attachment in the UI."
echo "    A database-only restore looks healthy until someone does exactly that."

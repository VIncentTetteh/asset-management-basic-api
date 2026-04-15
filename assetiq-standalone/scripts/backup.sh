#!/usr/bin/env bash
# backup.sh
#
# Creates a timestamped PostgreSQL dump of both AssetIQ databases.
# Schedule with cron:  0 2 * * * /path/to/assetiq-standalone/scripts/backup.sh
#
# Output: backups/assetiq_YYYYMMDD_HHMMSS.sql.gz
#                  backups/license_server_YYYYMMDD_HHMMSS.sql.gz

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_DIR="$ROOT_DIR/backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

mkdir -p "$BACKUP_DIR"

# Load env vars so we know the postgres password
if [ -f "$ROOT_DIR/.env" ]; then
  set -o allexport; source "$ROOT_DIR/.env"; set +o allexport
fi

PG_USER="${POSTGRES_USER:-assetiq}"
PG_PASS="${POSTGRES_PASSWORD:?POSTGRES_PASSWORD not set in .env}"
COMPOSE_CMD="docker compose"
command -v docker-compose &>/dev/null && COMPOSE_CMD="docker-compose"

dump_db() {
  local db="$1"
  local file="$BACKUP_DIR/${db}_${TIMESTAMP}.sql.gz"
  echo "→ Backing up database: $db"
  PGPASSWORD="$PG_PASS" $COMPOSE_CMD \
    -f "$ROOT_DIR/docker-compose.standalone.yml" \
    exec -T postgres \
    pg_dump -U "$PG_USER" "$db" | gzip > "$file"
  echo "  Written: $file"
}

dump_db assetiq
dump_db license_server

# Remove backups older than 30 days
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +30 -delete 2>/dev/null || true

echo "✅  Backup complete."

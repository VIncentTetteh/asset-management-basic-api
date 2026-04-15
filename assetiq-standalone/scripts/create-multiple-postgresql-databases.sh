#!/usr/bin/env bash
# create-multiple-postgresql-databases.sh
#
# Docker entrypoint init script that creates one PostgreSQL database per name
# listed in the POSTGRES_MULTIPLE_DATABASES env var (comma-separated).
#
# Usage in docker-compose.yml:
#   environment:
#     POSTGRES_MULTIPLE_DATABASES: assetiq,license_server
#   volumes:
#     - ./scripts/create-multiple-postgresql-databases.sh:/docker-entrypoint-initdb.d/01-create-databases.sh:ro
#
# Credit: adapted from the well-known mrts/docker-postgres-multiple-databases snippet.

set -euo pipefail

function create_db() {
  local db="$1"
  echo "  Creating database: $db"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE "$db";
    GRANT ALL PRIVILEGES ON DATABASE "$db" TO "$POSTGRES_USER";
EOSQL
}

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
  echo "→ Creating additional databases: $POSTGRES_MULTIPLE_DATABASES"
  IFS=',' read -ra DBS <<< "$POSTGRES_MULTIPLE_DATABASES"
  for db in "${DBS[@]}"; do
    # Skip the default DB that Postgres already created
    if [ "$db" != "$POSTGRES_DB" ] 2>/dev/null; then
      create_db "$db"
    fi
  done
  echo "✅  Done."
fi

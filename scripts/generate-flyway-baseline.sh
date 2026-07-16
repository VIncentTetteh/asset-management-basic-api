#!/usr/bin/env bash
#
# generate-flyway-baseline.sh
#
# Creates src/main/resources/db/migration/V7__baseline.sql from the CURRENT
# shape of the live schema. Required once, then every future schema change
# goes through a normal Vn__*.sql migration.
#
# Why: the repo today only has V1..V6 migrations but 40+ JPA entities —
# the production schema is implicitly managed by Hibernate ddl-auto. Before
# flipping ddl-auto to 'validate' in every environment (see P0-3), we need
# Flyway to own a single checkpoint that matches what Hibernate already built.
#
# Usage:
#
#   # 1. Boot a clean Postgres with the *current* backend in ddl-auto=update
#   #    so Hibernate builds the full schema from scratch:
#   docker compose up -d postgres
#   HIBERNATE_DDL_AUTO=update ./mvnw spring-boot:run &  # let it boot, then Ctrl-C
#
#   # 2. Export the schema:
#   ./scripts/generate-flyway-baseline.sh
#
#   # 3. Review and commit V7__baseline.sql
#
#   # 4. Flip ddl-auto to validate everywhere (already the default in prod)
#
# Notes:
#   - Strips Flyway's own history table so Flyway can re-own it.
#   - Strips row data (it's a schema baseline, not a data snapshot).
#   - Produces a file that is safe to apply to a fresh database
#     (all CREATE statements are idempotent where possible).

set -euo pipefail

HOST="${PGHOST:-localhost}"
PORT="${PGPORT:-5432}"
USER="${PGUSER:-postgres}"
DB="${PGDATABASE:-assetiq}"

OUT_DIR="$(dirname "$0")/../src/main/resources/db/migration"
OUT_FILE="$OUT_DIR/V7__baseline.sql"

mkdir -p "$OUT_DIR"

echo "-- AUTO-GENERATED Flyway baseline"                                           > "$OUT_FILE"
echo "-- Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"                               >> "$OUT_FILE"
echo "-- Source: pg_dump -s on $HOST:$PORT/$DB"                                   >> "$OUT_FILE"
echo "--"                                                                          >> "$OUT_FILE"
echo "-- Baselines the current Hibernate-managed schema. Do NOT edit by hand."    >> "$OUT_FILE"
echo "-- Future schema changes must be added as V8__*.sql and beyond."            >> "$OUT_FILE"
echo ""                                                                            >> "$OUT_FILE"

# Dump schema only, no ownership, no privileges, no Flyway history table.
pg_dump \
  --host="$HOST" \
  --port="$PORT" \
  --username="$USER" \
  --dbname="$DB" \
  --schema-only \
  --no-owner \
  --no-privileges \
  --no-comments \
  --exclude-table='flyway_schema_history' \
  --exclude-table='flyway_schema_history_lock' \
| sed -E \
    -e '/^SET /d' \
    -e '/^SELECT pg_catalog.set_config/d' \
    -e 's/^CREATE TABLE /CREATE TABLE IF NOT EXISTS /' \
    -e 's/^CREATE SEQUENCE /CREATE SEQUENCE IF NOT EXISTS /' \
    -e 's/^CREATE INDEX /CREATE INDEX IF NOT EXISTS /' \
    -e 's/^CREATE UNIQUE INDEX /CREATE UNIQUE INDEX IF NOT EXISTS /' \
  >> "$OUT_FILE"

echo ""                                                                            >> "$OUT_FILE"
echo "-- End of V7__baseline.sql"                                                 >> "$OUT_FILE"

BYTES=$(wc -c < "$OUT_FILE" | tr -d ' ')
LINES=$(wc -l < "$OUT_FILE" | tr -d ' ')
echo "✓ Wrote $OUT_FILE  ($LINES lines, $BYTES bytes)"
echo ""
echo "Next steps:"
echo "  1. Review the diff:  git diff $OUT_FILE"
echo "  2. Drop & recreate the dev DB and confirm Flyway applies V1..V7 cleanly."
echo "  3. Flip HIBERNATE_DDL_AUTO to 'validate' in every non-dev environment."

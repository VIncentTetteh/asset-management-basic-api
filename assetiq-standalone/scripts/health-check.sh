#!/usr/bin/env bash
# health-check.sh
#
# Prints the health status of every AssetIQ service.
# Run any time to verify the stack is healthy.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_CMD="docker compose"
command -v docker-compose &>/dev/null && COMPOSE_CMD="docker-compose"
COMPOSE_FILE="${ASSETIQ_COMPOSE_FILE:-$ROOT_DIR/docker-compose.yml}"

echo ""
echo "AssetIQ Standalone — Health Check"
echo "──────────────────────────────────"

check_container() {
  local name="$1"
  local service="$2"
  local probe="$3"
  if $COMPOSE_CMD -f "$COMPOSE_FILE" exec -T "$service" sh -c "$probe" >/dev/null 2>&1; then
    echo "  ✅  $name"
  else
    echo "  ❌  $name"
  fi
}

check_container "Backend API" backend "wget -qO- http://127.0.0.1:8080/actuator/health/readiness"
check_container "Web UI" web "wget -qO- http://127.0.0.1:3000/"
check_container "TLS edge" edge "wget --no-check-certificate -qO- https://127.0.0.1:8443/healthz"

echo ""
echo "Container statuses:"
$COMPOSE_CMD -f "$COMPOSE_FILE" ps --format "  {{.Name}}  {{.Status}}"
echo ""

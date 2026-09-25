#!/usr/bin/env bash
# health-check.sh
#
# Prints the health status of every AssetIQ service.
# Run any time to verify the stack is healthy.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_CMD="docker compose"
command -v docker-compose &>/dev/null && COMPOSE_CMD="docker-compose"
COMPOSE_FILE="${ASSETIQ_COMPOSE_FILE:-$ROOT_DIR/docker-compose.standalone.images.yml}"

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

check_container "Backend API" backend "curl -fsS http://localhost:8080/actuator/health"
check_container "Frontend" frontend "wget -qO- http://localhost:3000/"
check_container "Reverse proxy" nginx "wget --no-check-certificate -qO- https://localhost/"

echo ""
echo "Container statuses:"
$COMPOSE_CMD -f "$COMPOSE_FILE" ps --format "  {{.Name}}  {{.Status}}"
echo ""

#!/usr/bin/env bash
# health-check.sh
#
# Prints the health status of every AssetIQ service.
# Run any time to verify the stack is healthy.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_CMD="docker compose"
command -v docker-compose &>/dev/null && COMPOSE_CMD="docker-compose"

echo ""
echo "AssetIQ Standalone — Health Check"
echo "──────────────────────────────────"

check() {
  local name="$1"
  local url="$2"
  local status
  status=$(curl -sf -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
  if [ "$status" = "200" ]; then
    echo "  ✅  $name  ($url)"
  else
    echo "  ❌  $name  ($url)  HTTP $status"
  fi
}

check "Backend API"     "http://localhost:8080/actuator/health"
check "License Server"  "http://localhost:8090/actuator/health"
check "Frontend"        "http://localhost:3000/"

echo ""
echo "Container statuses:"
$COMPOSE_CMD -f "$ROOT_DIR/docker-compose.standalone.yml" ps --format "  {{.Name}}  {{.Status}}"
echo ""

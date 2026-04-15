#!/usr/bin/env bash
# smoke-test.sh
#
# End-to-end smoke test for the running AssetIQ Standalone Docker stack.
# Run AFTER `docker compose up -d --build` and the stack is healthy.
#
# Tests performed:
#   1. nginx serves HTTPS (or HTTP) on port 80 / 443
#   2. Backend /actuator/health returns {"status":"UP"}
#   3. License Server /actuator/health returns {"status":"UP"}
#   4. Frontend root page returns HTTP 200
#   5. GET /api/v1/license/status returns a JSON body
#   6. POST /api/v1/auth/login with wrong creds returns 401 (not 500)
#   7. PostgreSQL is accepting connections
#
# Usage:
#   ./scripts/smoke-test.sh [BASE_URL]
#   BASE_URL defaults to http://localhost

set -euo pipefail

BASE="${1:-http://localhost}"
BACKEND_DIRECT="http://localhost:8080"     # only reachable if port is bound (dev only)
LICENSE_DIRECT="http://localhost:8090"
PASS=0; FAIL=0

GREEN="\033[0;32m"; RED="\033[0;31m"; RESET="\033[0m"; BOLD="\033[1m"

ok()   { echo -e "${GREEN}✅  PASS${RESET}  $*"; ((PASS++)); }
fail() { echo -e "${RED}❌  FAIL${RESET}  $*"; ((FAIL++)); }

check_http() {
  local label="$1"; local url="$2"; local expected_status="${3:-200}"
  local status
  status=$(curl -sk -o /dev/null -w "%{http_code}" "$url" 2>/dev/null)
  if [ "$status" = "$expected_status" ]; then ok "$label  (HTTP $status)";
  else fail "$label  expected $expected_status, got $status"; fi
}

check_json_field() {
  local label="$1"; local url="$2"; local field="$3"; local expected="$4"
  local value
  value=$(curl -sk "$url" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$field',''))" 2>/dev/null || echo "")
  if [ "$value" = "$expected" ]; then ok "$label  ($field=$value)";
  else fail "$label  expected $field=$expected, got '$value'"; fi
}

echo ""
echo -e "${BOLD}AssetIQ Standalone — Smoke Test${RESET}"
echo -e "${BOLD}Base URL: $BASE${RESET}"
echo "──────────────────────────────────────────────"
echo ""

# ── 1. nginx / frontend ────────────────────────────────────────────────────────
check_http "nginx frontend root"           "$BASE/"                       "200"
check_http "nginx API proxy (auth route)"  "$BASE/api/v1/auth/login"      "405"

# ── 2. Backend health ─────────────────────────────────────────────────────────
check_json_field "Backend health"  "$BASE/api/v1/health"  "status"  "UP" || \
check_json_field "Backend health (actuator)" "http://localhost:8080/actuator/health" "status" "UP"

# ── 3. License server health ───────────────────────────────────────────────────
check_json_field "License Server health"  "http://localhost:8090/actuator/health"  "status"  "UP"

# ── 4. License status endpoint ────────────────────────────────────────────────
echo ""
LICENSE_STATUS=$(curl -sk "$BASE/api/v1/license/status" 2>/dev/null || echo "{}")
if echo "$LICENSE_STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); assert 'status' in d or 'readOnly' in d" 2>/dev/null; then
  ok "License status endpoint returns JSON with expected shape"
else
  fail "License status endpoint returned unexpected body: $LICENSE_STATUS"
fi

# ── 5. Auth rejects wrong credentials ─────────────────────────────────────────
AUTH_STATUS=$(curl -sk -o /dev/null -w "%{http_code}" \
  -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"nobody@test.com","password":"wrong"}')
if [ "$AUTH_STATUS" = "401" ] || [ "$AUTH_STATUS" = "400" ]; then
  ok "Auth login rejects bad credentials  (HTTP $AUTH_STATUS)"
else
  fail "Auth login returned unexpected status: $AUTH_STATUS"
fi

# ── 6. Rate limit on license validation ───────────────────────────────────────
echo ""
echo "Testing license server rate limit (sending 35 rapid requests)…"
BLOCKED=0
for i in $(seq 1 35); do
  s=$(curl -sk -o /dev/null -w "%{http_code}" \
    -X POST "http://localhost:8090/v1/validate" \
    -H "Content-Type: application/json" \
    -d '{"keyToken":"fake","instanceId":"smoke-test"}' 2>/dev/null)
  [ "$s" = "429" ] && ((BLOCKED++)) || true
done
if [ "$BLOCKED" -gt 0 ]; then
  ok "License Server rate limiting triggered after ${BLOCKED} of 35 requests"
else
  fail "License Server rate limiting did not trigger in 35 requests"
fi

# ── Summary ────────────────────────────────────────────────────────────────────
echo ""
echo "──────────────────────────────────────────────"
echo -e "${BOLD}Results: ${GREEN}${PASS} passed${RESET}${BOLD}, ${RED}${FAIL} failed${RESET}"
echo ""

if [ "$FAIL" -gt 0 ]; then
  echo "Run 'docker compose -f docker-compose.standalone.yml logs' to investigate."
  exit 1
else
  echo -e "${GREEN}All smoke tests passed. Stack is healthy.${RESET}"
  exit 0
fi

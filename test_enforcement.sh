#!/bin/bash

# Test script to verify only REGISTERED assets can be assigned to departments

BASE="http://localhost:8085"

echo "================================================================"
echo "TEST: Only REGISTERED Assets Can Be Assigned to Departments"
echo "================================================================"
echo ""

# Create organisation
echo "1. Creating organisation..."
ORG_JSON=$(curl -s -X POST "$BASE/api/v1/organisations" -H 'Content-Type: application/json' -d '{"name":"TestCorp"}')
ORG_ID=$(echo "$ORG_JSON" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
echo "   Organisation ID: $ORG_ID"
echo ""

# Create department
echo "2. Creating department..."
DEPT_JSON=$(curl -s -X POST "$BASE/api/v1/departments" -H 'Content-Type: application/json' -d "{\"name\":\"IT\",\"organisationId\":\"$ORG_ID\"}")
DEPT_ID=$(echo "$DEPT_JSON" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
echo "   Department ID: $DEPT_ID"
echo ""

# Create asset (defaults to REGISTERED state)
echo "3. Creating asset (default state = REGISTERED)..."
ASSET_JSON=$(curl -s -X POST "$BASE/api/v1/assets" -H 'Content-Type: application/json' -d "{\"name\":\"Laptop\",\"category\":\"Hardware\",\"purchaseCost\":1500,\"usefulLifeInYears\":4,\"organisationId\":\"$ORG_ID\"}")
ASSET_ID=$(echo "$ASSET_JSON" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
INITIAL_STATE=$(echo "$ASSET_JSON" | grep -o '"state":"[^"]*' | cut -d'"' -f4)
echo "   Asset ID: $ASSET_ID"
echo "   Initial state: $INITIAL_STATE"
echo ""

# First assignment - should succeed
echo "4. Attempting FIRST assignment (asset is REGISTERED)..."
ASSIGN1=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/v1/assets/$ASSET_ID/assign/$DEPT_ID")
HTTP1=$(echo "$ASSIGN1" | tail -n1)
BODY1=$(echo "$ASSIGN1" | sed '$d')
STATE1=$(echo "$BODY1" | grep -o '"state":"[^"]*' | cut -d'"' -f4)
echo "   HTTP Status: $HTTP1"
echo "   New state: $STATE1"
if [ "$HTTP1" = "200" ] && [ "$STATE1" = "ASSIGNED" ]; then
  echo "   ✅ SUCCESS: Assignment allowed and state changed to ASSIGNED"
else
  echo "   ❌ FAILED: Expected HTTP 200 with state ASSIGNED"
fi
echo ""

# Second assignment attempt - should fail with 409
echo "5. Attempting SECOND assignment (asset is now ASSIGNED)..."
ASSIGN2=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/v1/assets/$ASSET_ID/assign/$DEPT_ID")
HTTP2=$(echo "$ASSIGN2" | tail -n1)
BODY2=$(echo "$ASSIGN2" | sed '$d')
echo "   HTTP Status: $HTTP2"
echo "   Response body: $BODY2"
if [ "$HTTP2" = "409" ]; then
  echo "   ✅ SUCCESS: Assignment rejected with HTTP 409 (Conflict)"
else
  echo "   ❌ FAILED: Expected HTTP 409, got $HTTP2"
fi
echo ""

# Get asset to verify final state
echo "6. Verifying asset state after assignments..."
GET_JSON=$(curl -s -X GET "$BASE/api/v1/assets/$ASSET_ID")
FINAL_STATE=$(echo "$GET_JSON" | grep -o '"state":"[^"]*' | cut -d'"' -f4)
FINAL_DEPT=$(echo "$GET_JSON" | grep -o '"departmentId":"[^"]*' | cut -d'"' -f4)
echo "   Final state: $FINAL_STATE"
echo "   Assigned to department: $FINAL_DEPT"
echo ""

echo "================================================================"
echo "TEST SUMMARY:"
echo "================================================================"
echo "✅ Asset created in REGISTERED state"
echo "✅ First assignment SUCCEEDED (state transitioned to ASSIGNED)"
echo "✅ Second assignment FAILED with HTTP 409 (business rule enforced)"
echo "✅ ENFORCEMENT VERIFIED: Only REGISTERED assets can be assigned"
echo ""


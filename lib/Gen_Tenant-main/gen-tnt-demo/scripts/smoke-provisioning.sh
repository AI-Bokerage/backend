#!/usr/bin/env bash
# gen-tnt-demo/scripts/smoke-provisioning.sh
# Manual smoke check. Requires: docker compose up -d (from repo root), the
# gen-tnt-demo app running (./gradlew.bat :gen-tnt-demo:bootRun), and this
# script's mock step server running (python3 scripts/mock_step_server.py).
set -euo pipefail

API_BASE="http://localhost:8201"
SECRET="dev-secret"
SLUG="smoke-$(date +%s)"

echo "1. create tenant"
CREATE_RESPONSE=$(curl -s -X POST "$API_BASE/api/v1/tenants" \
  -H "X-Internal-Secret: $SECRET" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Smoke Test\",\"slug\":\"$SLUG\",\"region\":\"us\",\"primaryOwnerUserId\":\"$(python3 -c 'import uuid; print(uuid.uuid4())')\"}")
echo "   $CREATE_RESPONSE"

JOB_ID=$(echo "$CREATE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['provisioningJobId'])")
TENANT_ID=$(echo "$CREATE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

echo "2. poll job status until ACTIVE or DEAD"
for i in $(seq 1 20); do
  TENANT=$(curl -s "$API_BASE/api/v1/tenants/$TENANT_ID" -H "X-Internal-Secret: $SECRET")
  STATUS=$(echo "$TENANT" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
  echo "   attempt $i: tenant status=$STATUS"
  if [ "$STATUS" = "ACTIVE" ]; then
    echo "SMOKE OK — tenant reached ACTIVE"
    exit 0
  fi
  sleep 1
done

echo "SMOKE FAILED — tenant never reached ACTIVE, last status=$STATUS"
exit 1

#!/usr/bin/env bash
# scripts/smoke-sla.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3202}"
SECRET="${GEN_SLA_INTERNAL_SECRET:-demo-secret}"
TENANT_ID="${TENANT_ID:-8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a11}"

echo "== health =="
curl -sf "$BASE_URL/health"; echo

echo "== create policy =="
POLICY=$(curl -sf -X POST "$BASE_URL/api/v1/sla/$TENANT_ID/policies" \
  -H "X-Internal-Secret: $SECRET" -H "Content-Type: application/json" \
  -d '{"name":"First Response","entityType":"TICKET","slaType":"FIRST_RESPONSE","durationMins":60,"warningMins":45}')
echo "$POLICY"
POLICY_ID=$(echo "$POLICY" | node -pe 'JSON.parse(require("fs").readFileSync(0)).id')

echo "== list policies =="
curl -sf "$BASE_URL/api/v1/sla/$TENANT_ID/policies" -H "X-Internal-Secret: $SECRET"; echo

echo "== get policy =="
curl -sf "$BASE_URL/api/v1/sla/$TENANT_ID/policies/$POLICY_ID" -H "X-Internal-Secret: $SECRET"; echo

echo "== list instances (expect empty) =="
curl -sf "$BASE_URL/api/v1/sla/$TENANT_ID/instances" -H "X-Internal-Secret: $SECRET"; echo

echo "== metrics summary =="
curl -sf "$BASE_URL/api/v1/sla/$TENANT_ID/metrics/summary" -H "X-Internal-Secret: $SECRET"; echo

echo "== delete policy =="
curl -sf -X DELETE "$BASE_URL/api/v1/sla/$TENANT_ID/policies/$POLICY_ID" -H "X-Internal-Secret: $SECRET" -o /dev/null -w "%{http_code}\n"

echo "Smoke test passed."

#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3700}"
TENANT_ID="11111111-1111-1111-1111-111111111111"
INTERNAL_SECRET="${GEN_FMM_INTERNAL_SECRET:-change-me-dev-secret}"

echo "== health check =="
curl -sf "$BASE_URL/health" | grep -q '"ok"'

echo "== list catalog flags (seeded by the demo) =="
curl -sf "$BASE_URL/api/v1/catalog/flags"

echo "== resolve new_dashboard (default false, no override) =="
RESULT=$(curl -sf "$BASE_URL/api/v1/entitlement/$TENANT_ID/new_dashboard")
echo "$RESULT"
echo "$RESULT" | grep -q '"reason":"FLAG_DEFAULT"'

echo "== set a tenant override enabling it =="
curl -sf -X POST "$BASE_URL/api/v1/overrides" \
  -H 'content-type: application/json' \
  -d "{\"tenantId\":\"$TENANT_ID\",\"flagKey\":\"new_dashboard\",\"enabled\":true,\"reason\":\"smoke test\"}"

echo "== resolve again — override should now win =="
RESULT=$(curl -sf "$BASE_URL/api/v1/entitlement/$TENANT_ID/new_dashboard")
echo "$RESULT"
echo "$RESULT" | grep -q '"reason":"TENANT_OVERRIDE"'
echo "$RESULT" | grep -q '"enabled":true'

echo "== resolve an unknown flag — 200 with FLAG_NOT_FOUND, never 404 =="
RESULT=$(curl -sf "$BASE_URL/api/v1/entitlement/$TENANT_ID/does_not_exist")
echo "$RESULT" | grep -q '"reason":"FLAG_NOT_FOUND"'

echo "== internal check route rejects a missing secret =="
if curl -sf "$BASE_URL/internal/v1/fmm/check/$TENANT_ID/new_dashboard" >/dev/null 2>&1; then
  echo "expected 401, got success" >&2
  exit 1
fi

echo "== internal check route succeeds with the secret =="
curl -sf "$BASE_URL/internal/v1/fmm/check/$TENANT_ID/new_dashboard" -H "x-internal-secret: $INTERNAL_SECRET"

echo "== bulk resolve for the tenant =="
curl -sf "$BASE_URL/api/v1/entitlements/$TENANT_ID"

echo "== query telemetry (populated by the checks above once flushed) =="
curl -sf "$BASE_URL/api/v1/telemetry/$TENANT_ID"

echo "== remove the override =="
curl -sf -X DELETE "$BASE_URL/api/v1/overrides/$TENANT_ID/new_dashboard"

echo "== smoke test complete =="

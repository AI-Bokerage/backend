#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3900}"
SECRET="${GEN_SUP_INTERNAL_SECRET:-demo-secret}"

echo "GET /health"
curl -sf "$BASE_URL/health" | tee /dev/stderr | grep -q '"status":"ok"'

echo "GET /api/v1/dashboard/kpis without secret (expect 401)"
status=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/dashboard/kpis")
[ "$status" = "401" ] || { echo "expected 401, got $status"; exit 1; }

echo "GET /api/v1/dashboard/kpis with secret (expect 200)"
curl -sf -H "X-Internal-Secret: $SECRET" "$BASE_URL/api/v1/dashboard/kpis" | tee /dev/stderr | grep -q '"activeTenants"'

echo "Smoke test passed."

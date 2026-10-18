#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3400}"
SECRET="${GEN_TBR_INTERNAL_SECRET:?set GEN_TBR_INTERNAL_SECRET}"
TENANT_ID="${TENANT_ID:-11111111-1111-1111-1111-111111111111}"

echo "1/5 creating brand..."
curl -sf -X PUT "$BASE_URL/api/v1/branding/$TENANT_ID" \
  -H "X-Internal-Secret: $SECRET" -H "Content-Type: application/json" \
  -d '{"displayName":"Acme Co","primaryColor":"#1f6feb"}' > /dev/null

echo "2/5 claiming domain..."
DOMAIN_JSON=$(curl -sf -X POST "$BASE_URL/api/v1/domains" \
  -H "X-Internal-Secret: $SECRET" -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"domain\":\"acme-smoke.example.com\"}")
DOMAIN_ID=$(echo "$DOMAIN_JSON" | node -e "process.stdin.on('data',d=>console.log(JSON.parse(d).id))")

echo "3/5 verifying domain (expected to fail without real DNS — smoke stops here for real deploys)..."
curl -s -X POST "$BASE_URL/api/v1/domains/$DOMAIN_ID/verify" -H "X-Internal-Secret: $SECRET" || true

echo "4/5 fetching manifest by tenantId..."
curl -sf "$BASE_URL/api/v1/branding/$TENANT_ID/manifest" | node -e "process.stdin.on('data',d=>console.log(JSON.parse(d).displayName))"

echo "5/5 done."

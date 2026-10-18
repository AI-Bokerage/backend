#!/usr/bin/env bash
# scripts/smoke-search.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3800}"
SECRET="${GEN_SEARCH_INTERNAL_SECRET:-demo-secret}"
TENANT_ID="${TENANT_ID:-8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a11}"
OWNER_ID="${OWNER_ID:-8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a12}"

echo "== health =="
curl -sf "$BASE_URL/health"; echo

echo "== search (expect empty) =="
curl -sf -G "$BASE_URL/api/v1/search/$TENANT_ID" --data-urlencode "q=acme" -H "X-Internal-Secret: $SECRET"; echo

echo "== reindex (empty demo source) =="
curl -sf -X POST "$BASE_URL/api/v1/search/$TENANT_ID/reindex" -H "X-Internal-Secret: $SECRET" -H "Content-Type: application/json" -d '{}'; echo

echo "== erasure (expect removed: 0) =="
curl -sf -X POST "$BASE_URL/api/v1/search/$TENANT_ID/erasure" \
  -H "X-Internal-Secret: $SECRET" -H "Content-Type: application/json" \
  -d "{\"ownerId\":\"$OWNER_ID\"}"; echo

echo "Smoke test passed."

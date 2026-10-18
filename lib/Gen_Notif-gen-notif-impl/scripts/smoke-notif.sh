#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3500}"
TENANT_ID="11111111-1111-1111-1111-111111111111"
USER_ID="22222222-2222-2222-2222-222222222222"
INTERNAL_SECRET="${GEN_NOTIF_INTERNAL_SECRET:-change-me-dev-secret}"

echo "== health check =="
curl -sf "$BASE_URL/health" | grep -q '"ok"'

echo "== set an email preference (immediate, not digest) =="
curl -sf -X PATCH "$BASE_URL/api/v1/preferences" \
  -H 'content-type: application/json' \
  -d "{\"tenantId\":\"$TENANT_ID\",\"userId\":\"$USER_ID\",\"eventType\":\"doc.uploaded\",\"channel\":\"email\",\"enabled\":true,\"digestMode\":false}"

echo "== register a webhook endpoint =="
WEBHOOK_RES=$(curl -sf -X POST "$BASE_URL/api/v1/webhook-endpoints" \
  -H 'content-type: application/json' \
  -d "{\"tenantId\":\"$TENANT_ID\",\"userId\":\"$USER_ID\",\"url\":\"https://example.com/hook\"}")
echo "$WEBHOOK_RES"

echo "== trigger a notification =="
curl -sf -X POST "$BASE_URL/internal/notify" \
  -H 'content-type: application/json' \
  -H "x-internal-secret: $INTERNAL_SECRET" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"recipients\":[{\"userId\":\"$USER_ID\",\"email\":\"test@example.com\"}],\"eventType\":\"doc.uploaded\",\"data\":{\"fileName\":\"invoice.pdf\",\"uploadedBy\":\"Alice\"}}"

echo "== fetch unread notifications =="
curl -sf "$BASE_URL/api/v1/notifications/unread?tenantId=$TENANT_ID&userId=$USER_ID"

echo "== run a digest sweep (no-op unless a digestMode preference queued something) =="
curl -sf -X POST "$BASE_URL/internal/digest/sweep" -H "x-internal-secret: $INTERNAL_SECRET"

echo "== smoke test complete =="

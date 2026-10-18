#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8106}"
TENANT_ID="$(uuidgen)"
USER_ID="$(uuidgen)"

echo "== Bootstrap first role for a fresh tenant =="
curl -sf -X POST "$BASE_URL/internal/bootstrap" \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"userId\":\"$USER_ID\",\"roleName\":\"owner\",\"permissionCodes\":[\"adm:roles:manage\",\"adm:offboarding:manage\",\"adm:impersonation:request\",\"adm:invitations:manage\"]}"
echo

echo "== Create a second role =="
ROLE_ID=$(curl -sf -X POST "$BASE_URL/api/v1/roles" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d '{"name":"viewer","description":"Read-only role"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "Created role: $ROLE_ID"

echo "== Assign the new role to a second user =="
OTHER_USER_ID="$(uuidgen)"
curl -sf -X POST "$BASE_URL/api/v1/assignments" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d "{\"userId\":\"$OTHER_USER_ID\",\"roleId\":\"$ROLE_ID\"}"
echo

echo "== List assignments for the second user =="
curl -sf "$BASE_URL/api/v1/assignments?userId=$OTHER_USER_ID" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID"
echo

echo "== Initiate offboarding for the second user =="
JOB_ID=$(curl -sf -X POST "$BASE_URL/api/v1/offboarding" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d "{\"userId\":\"$OTHER_USER_ID\",\"reason\":\"left the company\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "Created offboarding job: $JOB_ID"

echo "== Check offboarding job status =="
curl -sf "$BASE_URL/api/v1/offboarding/$JOB_ID" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID"
echo

echo "== Grant adm:impersonation:manage to the viewer role =="
curl -sf -X PUT "$BASE_URL/api/v1/roles/$ROLE_ID/permissions" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d '{"permissionCodes":["adm:impersonation:manage"]}'
echo

echo "== Request to impersonate the second user =="
SESSION_ID=$(curl -sf -X POST "$BASE_URL/api/v1/impersonation-requests" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d "{\"targetUserId\":\"$OTHER_USER_ID\",\"reason\":\"support ticket #42\",\"ttlMinutes\":30}" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "Created impersonation session: $SESSION_ID"

echo "== Approve as the second user (now holding adm:impersonation:manage) =="
curl -sf -X POST "$BASE_URL/api/v1/impersonation-requests/$SESSION_ID/approve" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo

echo "== End the session as the original requester =="
curl -sf -X POST "$BASE_URL/api/v1/impersonation-requests/$SESSION_ID/end" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID"
echo

echo "== Create an invitation for a new hire, granting the viewer role =="
INVITE_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/v1/invitations" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d "{\"email\":\"newhire@example.com\",\"roleIds\":[\"$ROLE_ID\"]}")
echo "$INVITE_RESPONSE"
INVITE_TOKEN=$(echo "$INVITE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo "== Accept the invitation as a brand-new user (no auth headers needed — the token is the auth) =="
NEW_USER_ID="$(uuidgen)"
curl -sf -X POST "$BASE_URL/api/v1/invitations/$INVITE_TOKEN/accept" \
  -H "Content-Type: application/json" \
  -d "{\"userId\":\"$NEW_USER_ID\"}"
echo

echo "== List invitations (the accepted one should no longer appear) =="
curl -sf "$BASE_URL/api/v1/invitations" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID"
echo

echo "== File a support ticket as the owner (no special permission needed) =="
TICKET_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/v1/support-tickets" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d '{"type":"TECHNICAL","subject":"Cannot access dashboard","description":"Getting a 500 error.","priority":"HIGH"}')
echo "$TICKET_RESPONSE"
TICKET_ID=$(echo "$TICKET_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

echo "== List my own tickets =="
curl -sf "$BASE_URL/api/v1/support-tickets/mine" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID"
echo

echo "== Grant adm:tickets:manage to the viewer role =="
curl -sf -X PUT "$BASE_URL/api/v1/roles/$ROLE_ID/permissions" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d '{"permissionCodes":["adm:tickets:manage"]}'
echo

echo "== List all tenant tickets as the second user (now holding adm:tickets:manage) =="
curl -sf "$BASE_URL/api/v1/support-tickets" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo

echo "== Start, resolve, and close the ticket as the second user =="
curl -sf -X POST "$BASE_URL/api/v1/support-tickets/$TICKET_ID/start" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo
curl -sf -X POST "$BASE_URL/api/v1/support-tickets/$TICKET_ID/resolve" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo
curl -sf -X POST "$BASE_URL/api/v1/support-tickets/$TICKET_ID/close" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo

echo "== Grant adm:exports:manage to the viewer role =="
curl -sf -X PUT "$BASE_URL/api/v1/roles/$ROLE_ID/permissions" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d '{"permissionCodes":["adm:exports:manage"]}'
echo

echo "== Request a data export as the second user (now holding adm:exports:manage) =="
EXPORT_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/v1/exports" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID")
echo "$EXPORT_RESPONSE"
EXPORT_ID=$(echo "$EXPORT_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

echo "== List exports =="
curl -sf "$BASE_URL/api/v1/exports" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo

echo "== Get export status =="
curl -sf "$BASE_URL/api/v1/exports/$EXPORT_ID" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo

echo "== Download the export snapshot =="
curl -sf "$BASE_URL/api/v1/exports/$EXPORT_ID/download" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo

echo "== Revoke the export =="
curl -sf -X POST "$BASE_URL/api/v1/exports/$EXPORT_ID/revoke" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $OTHER_USER_ID"
echo

echo "Smoke test complete."

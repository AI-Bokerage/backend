#!/usr/bin/env bash
# scripts/smoke-signup.sh
# Full-path smoke test: signup -> verify-email -> poll until ACTIVE.
# Requires: gen-reg-demo (npm run dev --workspace=@gen-ms/gen-reg-demo) running
# locally, plus a live Gen_TNT and Gen_Auth stack (and Gen_TNT's own mock step
# server) already up.
set -euo pipefail

BASE_URL="${GEN_REG_BASE_URL:-http://localhost:3200}"
EMAIL="smoketest-$(date +%s)@example.com"
SLUG="smoketest-$(date +%s)"

echo "Starting signup for $EMAIL / $SLUG"
SIGNUP_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"smoketest123\",\"companyName\":\"Smoke Test Co\",\"desiredSubdomain\":\"$SLUG\"}")

SESSION_ID=$( (echo "$SIGNUP_RESPONSE" | grep -o '"sessionId":"[^"]*"' | cut -d'"' -f4) || true)
if [ -z "$SESSION_ID" ]; then
  echo "SMOKE FAILED: no sessionId in signup response: $SIGNUP_RESPONSE"
  exit 1
fi
echo "Session created: $SESSION_ID"

# Phase 1 uses a console-stub email sender — the verification link is logged
# by the server process, not actually emailed. Pull the token from the
# server's own log line for this smoke run instead of a real inbox.
echo "Check the Gen_REG server log for the verification link for $EMAIL, then export VERIFY_TOKEN and re-run this script's second half."
echo "(A fully automated version would swap ConsoleEmailSender for a test double the script can read from directly.)"

if [ -z "${VERIFY_TOKEN:-}" ]; then
  echo "SMOKE INCOMPLETE: set VERIFY_TOKEN and re-run to continue past email verification."
  exit 1
fi

curl -s "$BASE_URL/api/v1/signup/verify-email?token=$VERIFY_TOKEN" > /dev/null
echo "Email verified, selecting a plan and starting checkout..."

SELECT_PLAN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/signup/select-plan" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"planCode\":\"STARTER\"}")

NEXT_STEP=$( (echo "$SELECT_PLAN_RESPONSE" | grep -o '"nextStep":"[^"]*"' | cut -d'"' -f4) || true)
if [ "$NEXT_STEP" != "CHECKOUT" ]; then
  echo "SMOKE FAILED: select-plan did not return nextStep=CHECKOUT: $SELECT_PLAN_RESPONSE"
  exit 1
fi

CHECKOUT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/signup/checkout" \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"successUrl\":\"https://example.com/success\",\"cancelUrl\":\"https://example.com/cancel\"}")

CHECKOUT_URL=$( (echo "$CHECKOUT_RESPONSE" | grep -o '"sessionUrl":"[^"]*"' | cut -d'"' -f4) || true)
if [ -z "$CHECKOUT_URL" ]; then
  echo "SMOKE FAILED: no sessionUrl in checkout response: $CHECKOUT_RESPONSE"
  exit 1
fi

echo "Checkout URL: $CHECKOUT_URL"
echo "Open the checkout URL above and complete payment manually to continue the flow."
echo "(If your Stripe/Razorpay test-mode account has a webhook simulator, use it to fire the"
echo " checkout-completed event instead — that's what drives PAYMENT_PENDING -> PAYMENT_SUCCEEDED"
echo " and unblocks provisioning.) Polling for ACTIVE now -- complete payment within the poll window."

for i in $(seq 1 30); do
  STATE=$( (curl -s "$BASE_URL/api/v1/signup/$SESSION_ID" | grep -o '"state":"[^"]*"' | cut -d'"' -f4) || true)
  echo "  poll $i: state=$STATE"
  if [ "$STATE" = "ACTIVE" ]; then
    echo "SMOKE OK"
    exit 0
  fi
  if [ "$STATE" = "PROVISION_FAILED" ]; then
    echo "SMOKE FAILED: session reached PROVISION_FAILED"
    exit 1
  fi
  sleep 2
done

echo "SMOKE FAILED: timed out waiting for ACTIVE"
exit 1

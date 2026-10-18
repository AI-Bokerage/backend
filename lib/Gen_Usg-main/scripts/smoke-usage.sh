#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3600}"
TENANT_ID="11111111-1111-1111-1111-111111111111"
INTERNAL_SECRET="${GEN_USG_INTERNAL_SECRET:-change-me-dev-secret}"

echo "== health check =="
curl -sf "$BASE_URL/health" | grep -q '"ok"'

echo "== increment seats twice with the same eventId (proves dedup) =="
curl -sf -X POST "$BASE_URL/internal/usage/increment" \
  -H 'content-type: application/json' \
  -H "x-internal-secret: $INTERNAL_SECRET" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"metric\":\"seats\",\"delta\":1,\"eventId\":\"smoke-evt-1\"}"
REPLAY=$(curl -sf -X POST "$BASE_URL/internal/usage/increment" \
  -H 'content-type: application/json' \
  -H "x-internal-secret: $INTERNAL_SECRET" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"metric\":\"seats\",\"delta\":1,\"eventId\":\"smoke-evt-1\"}")
echo "$REPLAY" | grep -q '"replayed":true'

echo "== check seats (ALLOW, well under the demo's limit of 5) =="
curl -sf -X POST "$BASE_URL/internal/usage/check" \
  -H 'content-type: application/json' \
  -H "x-internal-secret: $INTERNAL_SECRET" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"metric\":\"seats\"}" | grep -q '"outcome":"ALLOW"'

echo "== increment api_calls past its limit of 3, then check (GRACE, api_calls is grace-eligible) =="
for i in 1 2 3 4; do
  curl -sf -X POST "$BASE_URL/internal/usage/increment" \
    -H 'content-type: application/json' \
    -H "x-internal-secret: $INTERNAL_SECRET" \
    -d "{\"tenantId\":\"$TENANT_ID\",\"metric\":\"api_calls\",\"delta\":1,\"eventId\":\"smoke-api-$i\"}" > /dev/null
done
curl -sf -X POST "$BASE_URL/internal/usage/check" \
  -H 'content-type: application/json' \
  -H "x-internal-secret: $INTERNAL_SECRET" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"metric\":\"api_calls\"}" | grep -q '"outcome":"GRACE"'

echo "== increment api_calls PAST its limit a second time, then check again (repeat-GRACE path — this is the bump() call on an already-OPEN window, previously broken by the RLS bypass) =="
for i in 5 6; do
  curl -sf -X POST "$BASE_URL/internal/usage/increment" \
    -H 'content-type: application/json' \
    -H "x-internal-secret: $INTERNAL_SECRET" \
    -d "{\"tenantId\":\"$TENANT_ID\",\"metric\":\"api_calls\",\"delta\":1,\"eventId\":\"smoke-api-$i\"}" > /dev/null
done
curl -sf -X POST "$BASE_URL/internal/usage/check" \
  -H 'content-type: application/json' \
  -H "x-internal-secret: $INTERNAL_SECRET" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"metric\":\"api_calls\"}" | grep -q '"outcome":"GRACE"'

echo "== fetch the usage summary =="
curl -sf "$BASE_URL/api/v1/usage/summary?tenantId=$TENANT_ID"

echo "== run a reconciliation sweep, a rollup, and a grace-window sweep (host-triggered, no cron in the library) =="
echo "(these are function-only in v1, no HTTP surface — exercised here via a small Node script against the same createGenUsg config the demo uses)"
DATABASE_URL="${DATABASE_URL:-postgresql://genusg_app:genusg_app@localhost:5439/genusg}" \
REDIS_URL="${REDIS_URL:-redis://localhost:6383}" \
GEN_USG_INTERNAL_SECRET="$INTERNAL_SECRET" \
SMOKE_TENANT_ID="$TENANT_ID" \
node --input-type=module <<'EOF'
import { createGenUsg, registerMeter } from "@gen-ms/gen-usg-starter";

// Same meter registration + limitProvider shape as packages/gen-usg-demo/src/index.ts —
// this is a separate Node process from the demo server, so the in-memory
// meter registry has to be seeded again here.
registerMeter("seats", { unit: "seat" });
registerMeter("api_calls", { unit: "call", graceEligible: true });
registerMeter("storage_bytes", { unit: "byte", mode: "resource" });

const limitProvider = {
  async getLimits() {
    return { seats: 5, api_calls: 3, storage_bytes: -1 };
  },
};

const genUsg = createGenUsg({
  limitProvider,
  internalSecret: process.env.GEN_USG_INTERNAL_SECRET,
});

const tenantId = process.env.SMOKE_TENANT_ID;

const rollup = await genUsg.runDailyRollup([tenantId]);
console.log("runDailyRollup ->", JSON.stringify(rollup));
if (rollup.failures > 0) throw new Error(`runDailyRollup reported ${rollup.failures} failure(s)`);

const sweep = await genUsg.runReconciliationSweep([tenantId]);
console.log("runReconciliationSweep ->", JSON.stringify(sweep));

const closed = await genUsg.closeExpiredGraceWindows([tenantId]);
console.log("closeExpiredGraceWindows ->", JSON.stringify(closed));

process.exit(0);
EOF

echo "== smoke test complete =="

# Gen_USG Integration Guide

## Embedding in-process

```ts
import { createGenUsg, registerMeter, type ILimitProvider } from "@gen-ms/gen-usg-starter";

registerMeter("seats", { unit: "seat" });
registerMeter("api_calls", { unit: "call", graceEligible: true });

const myLimitProvider: ILimitProvider = {
  async getLimits(tenantId) {
    // resolve from your own billing/plan lookup
    return { seats: 25, api_calls: 100_000 };
  },
};

const genUsg = createGenUsg({ limitProvider: myLimitProvider });

hostApp.use("/usg", genUsg.app);

// call these directly from your own domain code, no HTTP round-trip required:
await genUsg.increment({ tenantId, metric: "seats", delta: 1, eventId: "evt-123" });
const verdict = await genUsg.check(tenantId, "seats");
```

## Running standalone (HTTP)

```bash
docker compose up -d
npx prisma migrate deploy --schema packages/gen-usg-starter/prisma/schema.prisma
npm run build --workspaces --if-present
node packages/gen-usg-demo/dist/index.js
```

## Environment variables

Pulled directly from `packages/gen-usg-starter/src/create-gen-usg.ts`'s `resolveXxx` functions and `config/env.ts`:

| Var | Required when | Notes |
|---|---|---|
| `DATABASE_URL` | any Prisma-backed repo (`meterRepo`/`graceOverageRepo`/`reconciliationRepo`/`idempotencyRepo`) not overridden | Postgres connection string. Connect as the unprivileged app role (e.g. `genusg_app`), not the migration superuser |
| `REDIS_URL` | `counterStore` not overridden | Backs `RedisCounterStore` — every counter, dedup key, and limit cache entry lives here |
| `GEN_USG_INTERNAL_SECRET` | `internalSecret` config not set | Gates `/internal/usage/increment` and `/internal/usage/check` via `X-Internal-Secret` |
| `ALLOWED_ORIGINS` | never | Comma-separated CORS origins; defaults to `*`, which allows all origins |

Note: `limitProvider` has **no** env var — it has no default adapter at all (see callout below).

Note: the `20260728091000_create_app_role` migration's `GRANT` list is a fixed snapshot of the tables that existed when it was written, not automatic — any future table added to `schema.prisma` needs its own explicit `GRANT` to `genusg_app` in a new migration.

## API surface

Internal (require `X-Internal-Secret: <GEN_USG_INTERNAL_SECRET>`):

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/internal/usage/increment` | `{ tenantId, metric, delta, eventId?, resourceId?, idempotencyKey?, occurredAt? }` | Increments a counter (or, for `mode:"resource"` meters, adds/removes a member in a resource set). Dedups on `eventId`; replays return `{ replayed: true }`. Caveat: dedup keys are set *before* the counter write completes — if the counter write itself fails after the dedup keys are set, a retry with the same `eventId` is treated as an already-processed replay and the counter is never actually incremented for that event. |
| POST | `/internal/usage/check` | `{ tenantId, metric, delta? }` | Returns the current ALLOW/SOFT_WARN_80/SOFT_WARN_95/GRACE/BLOCK verdict — always `200`, the verdict lives in the response body, never the status code |

Public (`modules.*` gated, no auth of its own — front with the host's own authn/authz):

| Method | Path | Query | Notes |
|---|---|---|---|
| GET | `/api/v1/usage/summary` | `tenantId` | Per-metric current/limit/pct, recent rollup trend, and any open grace-overage windows |

Always available:

| Method | Path | Notes |
|---|---|---|
| GET | `/health` | No auth. Returns `{ "status": "ok" }` |

**Gen_USG ships no working `ILimitProvider` — you must supply one, or `createGenUsg()` throws `GenUsgConfigError` at boot whenever `modules.check` or `modules.summary` is enabled.**

**Any `ILimitProvider` failure (throw or timeout) makes `check()` fail OPEN to unlimited (`Infinity`), not BLOCK. This is a deliberate availability-over-strictness default — wrap your `ILimitProvider` yourself if you need strict fail-closed behavior.**

**Gen_USG has no internal scheduler and no tenant registry. You must call `runDailyRollup(tenantIds)`, `runMonthlyRollup(tenantIds)`, `runReconciliationSweep(tenantIds)`, and `closeExpiredGraceWindows(tenantIds)` yourself on your own cron/interval, supplying your own tenant list each time.**

**There is no message broker anywhere in Gen_USG. Call `increment()`/`check()` directly, in-process, whenever your own domain event fires — there is no RabbitMQ consumer, no DLQ, no retry queue.**

## Worked example: a `mode:"resource"` meter

`mode:"resource"` meters track a *set* of resources (e.g. uploaded files) rather than a running total, so re-incrementing the same `resourceId` doesn't double-count and removing a resource is a first-class operation — not just "subtract the delta".

```ts
import { createGenUsg, registerMeter, type ILimitProvider } from "@gen-ms/gen-usg-starter";

registerMeter("storage_bytes", { unit: "byte", mode: "resource" });

const limitProvider: ILimitProvider = {
  async getLimits() {
    return { storage_bytes: -1 }; // -1 == unlimited
  },
};

const genUsg = createGenUsg({ limitProvider });

// upload: add a 4 MiB file as resource "file-42"
await genUsg.increment({
  tenantId,
  metric: "storage_bytes",
  delta: 4_194_304,
  resourceId: "file-42",
  eventId: "upload-file-42",
});

// check(): current == 4194304 (the sum of every resource's score)
const afterUpload = await genUsg.check(tenantId, "storage_bytes");
// afterUpload.current === 4194304

// delete: a negative delta on the same resourceId removes it from the set
// entirely (zrem), it does not subtract the delta from the running total
await genUsg.increment({
  tenantId,
  metric: "storage_bytes",
  delta: -4_194_304,
  resourceId: "file-42",
  eventId: "delete-file-42",
});

// check(): current == 0 again, back to nothing tracked for this tenant
const afterDelete = await genUsg.check(tenantId, "storage_bytes");
// afterDelete.current === 0
```

## Swapping adapters

The six ports resolved by `create-gen-usg.ts`'s `resolveXxx` functions — override any of them in `createGenUsg({...})` to swap the default adapter:

| Config key | Port | Default adapter | Swap for |
|---|---|---|---|
| `counterStore` | `ICounterStore` | `RedisCounterStore` (`REDIS_URL`) | Any store with atomic incr/zset semantics |
| `meterRepo` | `IMeterRepo` | `PrismaMeterRepo` (`DATABASE_URL`) | Rollup snapshot persistence |
| `graceOverageRepo` | `IGraceOverageRepo` | `PrismaGraceOverageRepo` (`DATABASE_URL`) | Grace-window persistence |
| `reconciliationRepo` | `IReconciliationRepo` | `PrismaReconciliationRepo` (`DATABASE_URL`) | Reconciliation-sweep audit log |
| `idempotencyRepo` | `IIdempotencyRepo` | `PrismaIdempotencyRepo` (`DATABASE_URL`) | Durable increment idempotency ledger (Redis dedup keys are the fast path; this is the durable backstop) |
| `limitProvider` | `ILimitProvider` | **none — you must supply one** | Your own billing/plan-lookup service |

## Known limitations (v1)

- No working `ILimitProvider` out of the box — `createGenUsg()` throws `GenUsgConfigError` at boot if `modules.check`/`modules.summary` is enabled and none is supplied.
- No internal scheduler or tenant registry — rollups, reconciliation, and grace-window expiry are entirely host-driven, one tenant list at a time.
- No message broker, no outbox, no retry queue — `increment()`/`check()` are plain in-process async calls.
- `ILimitProvider` failures fail OPEN (unlimited), not closed — see the callout above.

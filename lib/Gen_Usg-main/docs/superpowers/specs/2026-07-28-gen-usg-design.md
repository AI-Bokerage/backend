# Gen_USG — Usage Metering — Design

## Goal

Build Gen_USG: the seventh Gen_MS library, owning usage metering — per-tenant
counters, per-metric limit checks (ALLOW/WARN/GRACE/BLOCK), grace-window
overage handling, periodic rollup snapshots, and drift reconciliation between
the live counter store and Postgres. TypeScript/Express/Prisma, matching
Gen_REG/Gen_TBR/Gen_NOTIF conventions.

## Why

Per `CPMS-Platform/PMP_Service_Reuse_Decision_Matrix.md`'s BILLING row,
`usg-svc` is **ADAPT** grade: real, tested Node logic (triple-dedup
increment, ALLOW/WARN/GRACE/BLOCK check, a 7-day grace-window state machine,
rollup snapshotting, drift reconciliation) already domain-agnostic at the
algorithm level — the decision matrix's own genericization tradeoffs note
calls usage metering's dedup/counter/grace-window logic "already
domain-agnostic," with extraction mainly a matter of parameterizing metric
names. This spec is written from a full source read (`increment/v1/service.ts`,
`check/v1/service.ts`, `grace-overage/v1/service.ts`,
`workers/rollup.worker.ts` + `cron.helper.ts`, `reconciliation/v1/service.ts`,
`infra/external/ppm.client.ts`), not a blank boundary — like Gen_NOTIF, not
like Gen_TBR.

Two real gaps found in the source, addressed here rather than carried
forward silently:
1. `usage_idempotency_ledger` has zero tenant scoping — acceptable by design
   (it's a pure event-id dedup backstop, not a tenant-queryable resource),
   documented explicitly rather than left as an unexplained anomaly.
2. `GraceOverageService.getOrOpenGraceWindow`'s find-then-create is not
   atomic — only the DB's `@@unique([tenantId, metric, status])` constraint
   prevents a duplicate `OPEN` window, and a concurrent-open unique-violation
   is never caught. Gen_USG catches it and retries as a re-read.

## Scope

**In scope (v1):**

- **Direct `increment()`/`check()` API, no message broker.** The source
  drives increments from 5 RabbitMQ consumer queues (`adm.user.*`,
  `pmt.project.*`, `dms.document.*`, `bsm.subscription.*`, `pmt.kt.*`) with a
  full DLQ-replay admin surface behind them. Gen_USG has zero broker
  infrastructure — matching every sibling's precedent (Gen_NOTIF's `notify()`,
  Gen_TBR's on-demand DNS verify). A host calls
  `genUsg.increment({tenantId, metric, delta, eventId, resourceId?})`
  in-process whenever its own domain event fires. The DLQ admin module drops
  entirely with it — there is no consumer to dead-letter from.
- **Host-registered meters.** `registerMeter(code, {unit, graceEligible,
  mode})` — a standalone, module-level-registry function called at config
  time, mirroring `registerTemplate`. Gen_USG ships zero hardcoded business
  metrics (the source's fixed `MeterCode` enum — `ACTIVE_USERS`,
  `ACTIVE_PROJECTS`, `STORAGE_BYTES`, `ACTIVE_KT_MILESTONES` — does not ship).
- **Per-meter accounting mode.** `mode: "counter"` (default, plain `INCRBY`,
  clamped at 0) or `mode: "resource"` (the source's `STORAGE_BYTES` special
  case, generalized: a Redis sorted-set keyed by `resourceId`, re-summed on
  every write, so out-of-order/duplicate add-then-delete events stay exact).
  Any meter can opt into resource-mode, not just a hardcoded one.
- **`check(tenantId, metric, delta?)`** → `ALLOW` / `SOFT_WARN_80` /
  `SOFT_WARN_95` / `GRACE` / `BLOCK`. Always resolves — the verdict is the
  return value, never a thrown error, matching the source's "200 always,
  verdict in body" HTTP behavior.
- **`ILimitProvider` port, host-supplied, required.** `getLimits(tenantId):
  Promise<Record<string, number>>` — one call resolves every metric's limit
  for a tenant (mirrors the source's "seed all entitlements in one pipeline"
  efficiency; `-1` means unlimited). No working default adapter ships — the
  source's PPM-svc HTTP client is CPMS-specific (and its
  `getLimitForMetric` method is dead code calling a non-existent endpoint,
  confirmed unreferenced anywhere in the current call graph). A host wires
  their own billing/plan lookup.
- **Fail-open by default.** Any `ILimitProvider` failure (throw, timeout,
  lock contention with nothing cached yet) resolves `check()` to `ALLOW`
  (unlimited) rather than blocking — the source's own tested behavior,
  an explicit availability-over-strictness choice, documented loudly in
  `integration-guide.md` rather than silently inherited.
- **Grace-window state machine**, 7-day default (configurable): existing
  `OPEN` window bumps `overageCount`; none exists → opens fresh, catching the
  unique-violation race described above. `closeExpiredGraceWindows()` is
  host-triggered (no cron) and **returns the closed list**
  (`{tenantId, metric, overageCount}[]`) instead of publishing a broker
  event, so a host can act on it directly from the return value.
- **Rollup + reconciliation, host-triggered, host-tenant-listed.**
  `runDailyRollup(tenantIds)`, `runMonthlyRollup(tenantIds)`,
  `runReconciliationSweep(tenantIds)` — no `node-cron`, no internal
  scheduler, no separate worker process (the source runs `server.ts` and
  `worker.ts` as two deployables; Gen_USG collapses to one). The host
  supplies the tenant list explicitly — Gen_USG owns no tenant registry and
  the source's Redis-`SCAN`-based tenant discovery is coupled to owning
  Redis directly, which Gen_USG only touches through `ICounterStore`.
  Per-tenant failures are caught and counted, never abort the batch.
  Reconciliation's "authoritative" value is the latest `UsageSnapshot`, not
  true ground truth — same caveat as the source, documented explicitly.
- **`GET /api/v1/usage/summary`** — trusts a caller-supplied `tenantId` query
  param, no JWT/JWKS at all. The source requires a JWKS-verified Bearer
  token on `/usg/me` (ignoring any client-supplied tenant header); every
  other Gen_MS sibling instead trusts the caller and leaves auth to the
  host, so Gen_USG matches its siblings, not the source. Renamed from
  `/usg/me` to `/usage/summary` since it's tenant-scoped, not per-user.

**Out of scope (v1 — each with its own rationale):**

- **RabbitMQ, consumers, DLQ, the separate worker process.** Collapsed into
  direct `increment()`/`check()` calls and host-triggered sweep functions.
  A future `IEventSource` bridge port (host wires their own broker into
  `increment()`) is a Phase 2 question, not built now.
- **PPM/BSM-specific HTTP clients.** Replaced by the generic `ILimitProvider`
  port. `HttpPpmClient.getLimitForMetric` (the source's dead,
  `@deprecated`-annotated method calling a non-existent PPM endpoint) is not
  ported in any form.
- **Fixed `MeterCode` enum.** Replaced by `registerMeter`.
- **Tenant-existence validation against Gen_TNT.** Same precedent as
  Gen_TBR/Gen_NOTIF: the caller's `tenantId` is trusted outright (validated
  only as a UUID shape); authorization is the host's job.
- **Prometheus/OpenTelemetry, `/metrics`, `/health/ready` shutdown-draining
  nuance.** Basic `GET /health` only, matching every sibling. A host wanting
  full observability wires its own.
- **Summary-endpoint response caching.** The source caches `/usg/me` in
  Redis with an invalidation hook on every increment; Gen_USG queries live.
  Unrequested caching layer — add later if load demands it.

## Architecture

### Cross-library integration

Least coupled alongside Gen_TBR/Gen_NOTIF: no port to any sibling service.
`ILimitProvider` happens to interoperate with a future Gen_BILLING/Gen_FMM
if a host points it there, but Gen_USG has no code-level knowledge of either.

### The factory pattern

```ts
export interface GenUsgConfig {
  counterStore?: ICounterStore;         // override-or-default -> RedisCounterStore (requires REDIS_URL)
  meterRepo?: IMeterRepo;               // override-or-default -> PrismaMeterRepo (requires DATABASE_URL)
  graceOverageRepo?: IGraceOverageRepo;
  reconciliationRepo?: IReconciliationRepo;
  idempotencyRepo?: IIdempotencyRepo;
  limitProvider?: ILimitProvider;        // REQUIRED override — no working default
  modules?: GenUsgModulesConfig;
  internalSecret?: string;
}

export interface GenUsgModulesConfig {
  check?: boolean;       // default true — POST /internal/usage/check
  increment?: boolean;   // default true — POST /internal/usage/increment
  summary?: boolean;     // default true — GET /api/v1/usage/summary
}

export interface GenUsgInstance {
  app: Express;
  increment(input: IncrementInput): Promise<IncrementResult>;
  check(tenantId: string, metric: string, delta?: number): Promise<CheckResult>;
  runDailyRollup(tenantIds: string[]): Promise<RollupResult>;
  runMonthlyRollup(tenantIds: string[]): Promise<RollupResult>;
  runReconciliationSweep(tenantIds: string[]): Promise<ReconciliationSweepResult>;
  closeExpiredGraceWindows(): Promise<ClosedGraceWindow[]>;
}

// standalone, module-level registry — mirrors registerTemplate
export function registerMeter(code: string, def: MeterDefinition): void;
export interface MeterDefinition {
  unit: string;
  graceEligible?: boolean;         // default false
  mode?: "counter" | "resource";   // default "counter"
}
```

Same `resolveXxx(override)` idiom as every sibling: an override is used
as-is; otherwise the default adapter is built lazily from env vars via
`requireEnv`, throwing `GenUsgConfigError` synchronously at
`createGenUsg()` call time. `limitProvider` has no default — if unset and
a registered meter is actually `check()`-ed, the resolver throws
`GenUsgConfigError` at boot (fail loud at construction, never mid-request).

### Ports

- **`ICounterStore`** — `incrBy(key, delta)`, `get(key)`, `mget(keys)`,
  `setNX(key, value, ttlSec)`, `del(key)`, `zadd(key, score, member)`,
  `zrem(key, member)`, `zsumScores(key)` (wraps `zrangebyscore` + reduce),
  `setBatch(entries: {key, value, ttlSec}[])` (pipeline, for seeding a
  tenant's limits in one round trip). Default `RedisCounterStore` via
  `ioredis`.
- **`ILimitProvider`** — `getLimits(tenantId): Promise<Record<string, number>>`.
  No default adapter.
- **`IMeterRepo`** — `insertSnapshot(tenantId, snapshotAt, period, metrics)`,
  `findTrend(tenantId, sinceDays)`, `latestSnapshot(tenantId)`. Default
  `PrismaMeterRepo`.
- **`IGraceOverageRepo`** — `findOpen(tenantId, metric)`,
  `open(tenantId, metric, expiresAt)` (catches the unique-violation race,
  retries as a re-read), `bump(id)`, `listExpiredOpen()`, `close(id)`.
- **`IReconciliationRepo`** — `insertLog(entry)`.
- **`IIdempotencyRepo`** — `exists(eventId)`, `insert(eventId)` — global, no
  `tenantId`, no RLS (same rationale as Gen_NOTIF's `EmailSuppression`).

### Domain model (Postgres/Prisma)

**`UsageSnapshot`** (table `usage_snapshot`) — `id`, `tenantId`, `snapshotAt`,
`period` (`"daily"|"monthly"`), `metrics` (`Json`, `{[meterCode]: number}`),
`createdAt`. Indexes `(tenantId, snapshotAt desc)`,
`(tenantId, period, snapshotAt desc)`. RLS: `ENABLE`+`FORCE ROW LEVEL
SECURITY`, policy with both `USING` and `WITH CHECK`, from the first
migration.

**`UsageGraceOverage`** (table `usage_grace_overage`) — `id`, `tenantId`,
`metric`, `graceStartedAt`, `graceExpiresAt`, `overageCount` (default 0),
`status` (enum `OPEN`/`CLOSED`), `billedAt?`, `createdAt`, `updatedAt`.
`@@unique([tenantId, metric, status])`. RLS scoped by `tenantId`.

**`UsageReconciliationLog`** (table `usage_reconciliation_log`) — `id`,
`tenantId`, `metric`, `counterValue` (`BigInt` — port-agnostic rename of the
source's `valkeyValue`), `dbValue` (`BigInt`), `driftPct` (`Decimal 6,2`),
`corrected` (`Boolean`), `runAt`. Append-only. RLS scoped by `tenantId`.

**`UsageIdempotencyLedger`** (table `usage_idempotency_ledger`) —
`eventId` (`String @id`), `processedAt`. Global, no `tenantId`, **no RLS by
design** — a pure dedup backstop, not a tenant-queryable resource.

**Explicitly not modeled**: no `UsageDlqMessage` (dropped with the
consumers), no plan/entitlement tables (`ILimitProvider`'s job, outside
Gen_USG).

**Redis key namespace**, all prefixed `genusg:` to avoid colliding with a
host's own Redis usage: `genusg:{tenantId}:{metric}` (counter),
`genusg:resource:{tenantId}:{metric}` (sorted set, `mode: "resource"`),
`genusg:dedup:{eventId}`, `genusg:dedup:inc:{idempotencyKey}`,
`genusg:limit:{tenantId}:{metric}`, `genusg:lock:limit:{tenantId}`.

### The `increment()` pipeline

```
increment({ tenantId, metric, delta, eventId?, resourceId?, idempotencyKey?, occurredAt? })
```

1. Unregistered `metric` → `MeterNotRegisteredError`.
2. `delta === 0` → `IncrementDeltaInvalidError`.
3. `occurredAt` older than `backdateDays` (config, default 30) →
   `UsageEventOutOfWindowError`.
4. Dedup layer 1 — `counterStore.setNX` on `genusg:dedup:{eventId}` — not
   acquired: return `{newValue: 0, replayed: true}` immediately.
5. Dedup layer 2 — `setNX` on `genusg:dedup:inc:{idempotencyKey ?? eventId}` —
   same short-circuit.
6. Dedup layer 3 — `idempotencyRepo.exists(eventId)`, the Postgres fallback
   beyond the Redis TTL — same short-circuit.
7. Apply: `mode: "resource"` meters require `resourceId` (else
   `ResourceIdRequiredError`) — `zadd`/`zrem` then re-sum via `zsumScores`.
   `mode: "counter"` meters — `incrBy`, clamp to 0 (and log a warning) if the
   result goes negative.
8. Fire-and-forget `idempotencyRepo.insert(eventId)` — a write failure only
   logs a warning, never fails the call.

### The `check()` pipeline

```
check(tenantId, metric, delta = 1)
```

Never throws for a normal business verdict (only `MeterNotRegisteredError`
on a bad metric code):

1. One `mget([counterKey, limitKey])` round trip.
2. Limit cache miss → stampede lock (`setNX` on
   `genusg:lock:limit:{tenantId}`): loser spin-waits 100ms then re-reads the
   cache (fail-open to `Infinity` if still empty); winner calls
   `limitProvider.getLimits(tenantId)` — **any throw/timeout fails open to
   `Infinity`, by design** — and seeds every returned metric's limit via one
   `setBatch` pipeline call, releasing the lock in `finally`.
3. `next = current + delta`; limit `-1` or `Infinity` → `ALLOW`, pct 0.
4. `next > limit` → grace-eligible meter: `getOrOpenGraceWindow` → `GRACE
   {expiresAt, daysLeft}`; else `BLOCK {code: "USAGE_QUOTA_EXCEEDED"}`.
5. Soft thresholds checked 95% before 80% (so 95 wins over 80):
   `SOFT_WARN_95` / `SOFT_WARN_80` / `ALLOW`.

### API surface (v1)

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/internal/usage/increment` | `X-Internal-Secret` | body = `IncrementInput` |
| POST | `/internal/usage/check` | `X-Internal-Secret` | body `{tenantId, metric, delta?}` — always 200, verdict in body |
| GET | `/api/v1/usage/summary` | none (trusted `tenantId` query param) | `{meters:[{metric,unit,current,limit,pct}], trend:[...], graceOverages:[...]}` |
| GET | `/health` | none | `{status:"ok"}` |

`runDailyRollup`/`runMonthlyRollup`/`runReconciliationSweep`/
`closeExpiredGraceWindows` are function-only (no HTTP route) — host wires
its own cron/interval, same precedent as Gen_NOTIF's `runDigestSweep()`.

### Error taxonomy (`common/errors.ts`)

`AppError` base (statusCode + code) +:
- `GenUsgConfigError` (500, thrown at boot) — boot-time misconfiguration.
- `MeterNotRegisteredError` (400) — unknown metric code.
- `IncrementDeltaInvalidError` (400) — zero delta.
- `UsageEventOutOfWindowError` (422) — backdate beyond window.
- `ResourceIdRequiredError` (400) — `mode: "resource"` meter called without
  `resourceId`.

Every error → `{ error: code, message: humanText }` via `errorHandler`.

## Testing Strategy

- **Unit** (Vitest, mocked ports): `increment` (3 dedup layers, backdate
  rejection, resource-mode zadd/zrem/zsumScores, counter-mode negative
  clamp), `check` (ALLOW/WARN_80/WARN_95/GRACE/BLOCK/unlimited/
  stampede-lock-dedupes-concurrent-provider-calls/fail-open-on-provider-
  throw), `grace-overage` (first-open, bump-existing, expire-sweep,
  unique-violation race retry), `rollup`/`reconciliation` (per-tenant
  failure isolation, zero-baseline never corrects).
- **Repo integration** (Testcontainers Postgres): snapshot insert/trend
  query, grace-overage `@@unique` constraint, reconciliation log insert,
  idempotency ledger insert/exists, RLS actually blocks cross-tenant reads.
- **Counter-store integration** (Testcontainers Redis): real `incrBy`/
  `zadd`/`zrem`/`zsumScores`/`setNX`/`mget`/`setBatch` against a live Redis.
- **HTTP** (supertest): each endpoint's happy path, `X-Internal-Secret`
  gate, module-toggle 404s, `createGenUsg({})` config-error boot test
  (missing `limitProvider` when a meter is check-able).
- **Smoke script** `scripts/smoke-usage.sh`: increment twice with the same
  `eventId` (proves dedup) → check (ALLOW) → increment past a stub limit →
  check (BLOCK or GRACE depending on meter config) →
  `runReconciliationSweep` → `runDailyRollup` → fetch
  `/api/v1/usage/summary`.

## Delivery Approach

**Docker/ports** — next free in the family's allocation (AUTH 5433, TNT
5435, REG 5436, TBR 5437, NOTIF 5438): Postgres **5439** (db `genusg`),
Redis **6383** (AUTH 6380, TNT 6381, REG 6382). Demo HTTP **3600** (REG 3200,
TBR 3400, NOTIF 3500).

**Package layout** — `Gen_MS/Gen_USG/`, its own repo, workspace root +
`packages/gen-usg-starter` (`@gen-ms/gen-usg-starter`) +
`packages/gen-usg-demo`. Factory `createGenUsg`, namespace `GenUsg`, config
`GenUsgConfig`/instance `GenUsgInstance`. Same `.ts`-extension/NodeNext/
Zod-env/pino/`AppError` conventions as every sibling.

**Documentation deliverables:**
1. This spec.
2. `docs/superpowers/plans/2026-07-28-gen-usg-implementation.md` —
   task-by-task plan, one spec/one plan/many small tasks, matching
   Gen_ADM/Gen_REG/Gen_NOTIF's style.
3. `docs/integration-guide.md` — env var table, full API table, the
   `ILimitProvider`/`ICounterStore` swap guides, loud callouts: **no message
   broker, no internal scheduler, fail-open limit checks by default, no
   tenant registry (host supplies tenant lists for sweeps)**.
4. `README.md` — Gen_MS family table (now 7 rows), local run instructions,
   port-offset table, "Not in scope" section.

## Assumptions (revisit if challenged)

1. **No message broker, no consumers, no DLQ.** Confirmed with the user —
   direct `increment()`/`check()` calls only. A future `IEventSource` bridge
   port is Phase 2, not built now.
2. **Metrics are entirely host-defined** via `registerMeter` — no hardcoded
   business metric ships. Confirmed with the user.
3. **`ILimitProvider` is a required host-supplied port**, no working
   default — confirmed with the user, since Gen_USG has no sibling
   billing/plan service built yet.
4. **Redis/Valkey is a required dependency**, behind `ICounterStore` —
   confirmed with the user; the hot path (dedup, counters, limit cache,
   locks) cannot reasonably run in-process across multiple host instances.
5. **No built-in JWT/JWKS** — the summary endpoint trusts a caller-supplied
   `tenantId`, matching every sibling except the source. Confirmed with the
   user.
6. **Resource-keyed accounting generalized to any meter** via
   `mode: "resource"`, not hardcoded to a storage-bytes metric. Confirmed
   with the user.
7. **Fail-open by default on limit-provider failure** — confirmed with the
   user as matching the source's tested behavior; documented loudly rather
   than silently inherited.
8. **Rollup/reconciliation take an explicit `tenantIds` list from the
   host** rather than self-discovering tenants via a Redis `SCAN` — confirmed
   with the user, since `ICounterStore` may not expose a SCAN-equivalent for
   every backend.

## Anti-patterns to Avoid (explicitly)

- Importing any sibling Gen_MS package as an npm/code dependency.
- RabbitMQ, a DLQ table, or a separate worker process in v1.
- A fixed `MeterCode`-style enum shipped by the library.
- Calling Gen_TNT to validate a tenant.
- Built-in JWT/JWKS verification on the summary endpoint.
- A working default `ILimitProvider` that guesses at a billing integration.
- Caching the summary endpoint's response.
- Default ports colliding with any sibling's Postgres/Redis/demo-HTTP port.
- Raw `console.log` in library code; raw `Error` thrown across a request
  boundary.
- Omitting `.ts` on relative imports.

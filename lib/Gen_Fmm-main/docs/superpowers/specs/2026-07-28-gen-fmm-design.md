# Gen_FMM Design Spec

**Date:** 2026-07-28
**Status:** Approved

## Goal

Build `Gen_FMM` — a reconfigurable, embeddable feature-gate/entitlement engine for the Gen_MS family, adapting CPMS-Platform's `fmm-svc`. Completes the BILLING module's feature-gating half (Gen_USG already covers usage-metering).

## Why

`fmm-svc` implements a solid 4-tier entitlement resolution algorithm (override → plan-entitlement → default → rollout-bucket) with a proven cache-stampede-safe L1+L2 chain. It is hard-wired to CPMS-specific infra (RabbitMQ subscription events, X-Internal-Key RBAC, Valkey pub/sub) that a generic embeddable library must not carry. It also has two live bugs worth fixing rather than porting: a decorative (non-enforcing) RLS policy, and a repo-layer bug that hardcodes `expiresAt: null`, silently disabling override expiry.

## Scope

**In scope:**
- 4-tier entitlement resolution (`resolve()`, pure fn, ported verbatim)
- `check()`/`bulk()` direct in-process API + internal HTTP routes
- Catalog CRUD (modules/plan_modules/feature_flags — global tables)
- Tenant override CRUD (with real expiry enforcement)
- L1 (in-process) + L2 (Redis) cache chain with stampede lock, host-supplied cross-pod invalidation hook
- Telemetry (usage event logging, host-triggered flush, query route)
- RLS on the two tenant-scoped tables, enforced via `withTenant()` + dedicated app role (family-standard pattern)
- `requireFeature(featureKey)` portable Express middleware for host route-gating

**Out of scope:**
- Any RabbitMQ/message-broker integration (subscription events, override-changed events)
- Valkey/Redis pub/sub for multi-pod coherence — replaced by a host-supplied callback hook
- JWT verification / RBAC (SUPER_ADMIN, tenant.read/write permission checks) — host's responsibility entirely
- A `plans` table or any plan-membership storage — `planCode` is always caller-supplied
- PostHog / analytics vestigial wiring (unused in source anyway)

## Architecture

`createGenFmm(config)` factory, hexagonal ports-and-adapters, mirrors Gen_USG/Gen_NOTIF conventions exactly (Node 20, TS ESM/NodeNext, `.ts` import extensions, Express 4, Prisma 5, Postgres 15, Vitest 2 + supertest + Testcontainers).

```ts
export function createGenFmm(config: GenFmmConfig): GenFmmInstance {
  const catalogRepo = config.catalogRepo ?? new PrismaCatalogRepo();
  const overrideRepo = config.overrideRepo ?? new PrismaOverrideRepo();
  const telemetryRepo = config.telemetryRepo ?? new PrismaTelemetryRepo();
  const cacheStore = config.cacheStore ?? new RedisCacheStore(resolveRedisUrl());
  const onFlagChanged = config.onFlagChanged ?? (() => {});
  const internalSecret = config.internalSecret ?? requireEnvIfInternalRoutesEnabled(config);

  // ... wire services, controllers, routes ...

  return {
    app,                                    // Express app, mount at any prefix
    check: entitlementService.check,        // (tenantId, flagKey, planCode?) => Promise<EntitlementCheckResult>
    bulk: entitlementService.bulk,          // (tenantId, planCode?) => Promise<BulkEntitlementResult>
    flushTelemetryBuffer: telemetryService.flush, // () => Promise<number> (flushed count)
    requireFeature: (featureKey) => requireFeatureMiddleware(featureKey, entitlementService),
  };
}
```

### Ports

| Port | Purpose | Default adapter |
|---|---|---|
| `ICatalogRepo` | modules/plan_modules/feature_flags CRUD, no tenant scoping | `PrismaCatalogRepo` |
| `ITenantOverrideRepo` | tenant_feature_flags CRUD, RLS'd | `PrismaOverrideRepo` (wraps every call in `withTenant`) |
| `ITelemetryRepo` | feature_usage_events write/query, RLS'd | `PrismaTelemetryRepo` (wraps every call in `withTenant`) |
| `ICacheStore` | get/set/del for L2 | `RedisCacheStore` |

L1 is an in-process `BoundedTtlCache` — a perf detail, not a swappable port (matches source; no host would ever need to override an in-process LRU).

### Domain model

```ts
interface FeatureModule {
  code: string; name: string; description: string | null;
  category: string | null; isActive: boolean; displayOrder: number; iconKey: string | null;
}
interface PlanModule { planCode: string; moduleCode: string; entitlement: Record<string, unknown>; }
interface FeatureFlag {
  key: string; moduleCode: string | null; defaultEnabled: boolean;
  isGradualRollout: boolean; rolloutPercentage: number;
}
interface TenantFeatureFlag {
  tenantId: string; flagKey: string; enabled: boolean;
  config: Record<string, unknown>; reason: string | null;
  expiresAt: Date | null; createdBy: string | null;
}
interface FeatureUsageEvent {
  id: string; tenantId: string; flagKey: string; enabled: boolean;
  reason: string; planCode: string | null; occurredAt: Date;
}
interface EntitlementCheckResult {
  tenantId: string; flagKey: string; enabled: boolean;
  reason: "TENANT_OVERRIDE" | "PLAN_ENTITLEMENT" | "FLAG_DEFAULT" | "ROLLOUT" | "FLAG_NOT_FOUND";
  cacheHit: "l1" | "l2" | "miss"; latencyMs: number;
}
```

### check()/bulk() pipeline (pseudocode)

```
check(tenantId, flagKey, planCode?):
  if L1.get(key) -> return (cacheHit: "l1")
  value = getOrSet(L2key, fetcher, ttl, lockTtl)   // stampede-safe: SET NX EX lock,
                                                     // poll-then-refetch on lock-miss, jittered TTL
    fetcher():
      [flagWithOverride, planModules] = parallel(
        overrideRepo.findFlagWithTenantOverride(tenantId, flagKey),
        planCode ? catalogRepo.findPlanModules(planCode) : []
      )
      return resolve(flag, override, planModules, tenantId, flagKey)
  L1.set(key, value)   // always refresh L1 regardless of which tier served the read
  recordUsage(tenantId, flagKey, value)  // fire-and-forget -> telemetry ring buffer
  return value

resolve(flag, override, planModuleCodes, tenantId, flagKey):
  if !flag: return { enabled: false, reason: "FLAG_NOT_FOUND" }
  if override && (!override.expiresAt || override.expiresAt >= now()):
    return { enabled: override.enabled, reason: "TENANT_OVERRIDE" }
  if flag.moduleCode && planModuleCodes.includes(flag.moduleCode):
    return { enabled: true, reason: "PLAN_ENTITLEMENT" }
  if !flag.isGradualRollout:
    return { enabled: flag.defaultEnabled, reason: "FLAG_DEFAULT" }
  bucket = computeRolloutBucket(tenantId, flagKey)   // sha256(`${tenantId}:${flagKey}`), first 8 hex chars, %100
  return { enabled: bucket < flag.rolloutPercentage, reason: "ROLLOUT" }
```

`bulk()` is the same shape, L2-only cache (no L1 — bulk maps are large/per-tenant), loads all flags + all overrides + plan modules in parallel to avoid N+1.

### Cache invalidation

Writes (override upsert/delete, flag/plan-module update) call, in order: (1) local `invalidateTenantFlag`/`invalidateFlag`/`invalidateModuleFlags` (direct in-process L1+L2 purge), (2) `onFlagChanged?.(tenantId, flagKey)` — host-supplied hook, no-op default, for hosts running multiple pods to broadcast on their own bus.

### RLS

`tenant_feature_flags` and `feature_usage_events` get `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` + policy on `current_setting('app.tenant_id', true)::uuid` (wrapped in `NULLIF` per the Gen_USG-discovered pooled-connection fix), plus a dedicated non-superuser `genfmm_app` role migration. Every Prisma call touching either table goes through `withTenant(tenantId, fn)`. `modules`/`plan_modules`/`feature_flags` stay global — no RLS, no tenant_id column — confirmed correct by source inspection and the CPMS decision matrix.

## Error Taxonomy

| Error | HTTP | Code |
|---|---|---|
| `ConflictError` | 409 | `MODULE_ALREADY_EXISTS` / `FLAG_ALREADY_EXISTS` |
| `NotFoundError` | 404 | `MODULE_NOT_FOUND` / `FLAG_NOT_FOUND` / `OVERRIDE_NOT_FOUND` |
| `BusinessRuleError` | 422 | `OVERRIDE_EXPIRY_IN_PAST` |
| `InvalidTenantIdError` | 400 | `INVALID_TENANT_ID` |
| `GenFmmConfigError` | — (thrown at factory-call time, synchronous) | boot-time misconfiguration |

`check()`/`bulk()` never throw `NotFoundError` for an unknown flag key — they return `{ enabled: false, reason: "FLAG_NOT_FOUND" }` with HTTP 200, ported verbatim from source (deliberate: callers never special-case 404 for flag checks).

## Testing Strategy

- **Unit:** `resolve()` — every branch (not-found, override wins, override-expired-falls-through, plan-entitlement, default true/false, rollout 0%/100%/mid); `computeRolloutBucket` (range, determinism, spread); `BoundedTtlCache`; cache-key builders.
- **Integration (Testcontainers Postgres+Redis):** RLS enforcement under `genfmm_app` non-superuser role — repeat-write succeeds, cross-tenant isolation holds, round-trip correctness (same 3-case pattern Gen_USG's final review established); `getOrSet` stampede-lock behavior under concurrent misses; catalog CRUD conflict paths (P2002 race); override upsert with future/past `expiresAt`, proving expiry is actually enforced on the read path.

## Delivery Approach

npm workspace: `packages/gen-fmm-starter` (library) + `packages/gen-fmm-demo` (runnable demo). Ports: Postgres 5441, Redis 6385 (next free in family sequence — Gen_USG used 5439/6383). Subagent-Driven Development, following the exact task granularity of Gen_NOTIF/Gen_USG's plans (scaffold → ports → Prisma schema/migrations → default adapters → services → controllers/routes → factory → demo app → docs → smoke test), ending with a final whole-branch review on the most capable model.

## Assumptions

1. `planCode` is always host-supplied — Gen_FMM never looks up subscription state itself.
2. No RBAC/JWT — entirely the host's responsibility; internal-secret gates only the two internal routes.
3. RLS must be live end-to-end (`withTenant` on every tenant-scoped repo call) — the final review will explicitly check for this bug class, per Gen_USG's precedent finding.
4. Override `expiresAt` must be respected on the read path — the source bug does not get repeated here.
5. `onFlagChanged` broadcast hook is opt-in, no-op default — no message bus dependency introduced.
6. Telemetry flush is host-triggered (`flushTelemetryBuffer()`), no internal timer — matches family's "host owns scheduling" convention (Gen_USG rollups, Gen_NOTIF digest sweep).
7. Catalog tables (`modules`/`plan_modules`/`feature_flags`) stay global, no `tenant_id` — deliberate, confirmed by source + decision matrix, not a silent copy.
8. `computeRolloutBucket` ports verbatim — a tested pure function, not touched.

## Anti-Patterns to Avoid

- Do not port RabbitMQ consumers, Valkey pub/sub, JWT/RBAC middleware, or PostHog wiring.
- Do not ship a decorative RLS policy without a working `withTenant()` mechanism.
- Do not repeat the `expiresAt: null` hardcode bug.
- Do not add a `plans` table or any subscription-lookup logic — `planCode` is always a parameter.
- Do not add an internal cron/scheduler for telemetry flush or cache TTL sweeps.
- Do not add tenant_id to the catalog tables "for consistency" with the family's RLS convention — this was an explicit, confirmed-correct exception.

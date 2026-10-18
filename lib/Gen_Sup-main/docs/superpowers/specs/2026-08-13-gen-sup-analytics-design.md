# Gen_SUP analytics module — design

## Context

Fifth module in the Gen_SUP build order (`dashboard` → `tenants` → `feature-flags` → `announcements` → **analytics** → tickets → impersonate).

Original CPMS `sup-svc` analytics module (`SUP-03`) is a single endpoint, revenue-only aggregation over the same `SupTenantSummary` read model `dashboard` already reads — MRR/ARR by plan and region, plan-mix percentages, trial→paid conversion rate, with a CSV export. Its own Zod schema accepted `granularity`/`planCode`/`region` filter params that were never actually wired into the queries — a stub gap in the original.

Gen_SUP's version goes further than a faithful CPMS port in two ways, both confirmed as in-scope:
1. **Real historical trend data.** No service in Gen_MS tracks MRR-over-time snapshots, so `granularity`-based charting is only buildable by Gen_SUP capturing its own periodic snapshots — this module's **second local-persistence table** (after `announcements`' `Announcement` model), populated by a host-driven capture method rather than live-data bucketing.
2. **Usage trends via Gen_USG.** Gen_MS has a real usage-metering service (Gen_USG) that CPMS never had access to. However, Gen_USG's every endpoint is single-tenant-scoped by Postgres RLS (`withTenant`/`SET LOCAL app.tenant_id`) with zero cross-tenant aggregation capability, and Gen_TNT still has no bulk tenant-list endpoint — the same "no tenant directory anywhere" gap `dashboard`/`tenants` have already had to work around. This module doesn't invent tenant discovery: the caller supplies which tenant IDs to include, and Gen_SUP loops Gen_USG's per-tenant summary endpoint and sums client-side.

## Scope

In scope:
- Live revenue snapshot (`GET /api/v1/analytics/revenue`): MRR/ARR totals, average revenue per tenant, MRR by plan, MRR by region, plan-mix distribution, trial→paid conversion rate over a date range. Real `planCode`/`region` query filters (reshape the `byPlan`/`byRegion` breakdown arrays), Valkey-cached (5 min TTL, own key/TTL — not shared with dashboard's), `?export=csv` streams the `byPlan` breakdown as a CSV download (bypasses cache, always computed fresh, matching CPMS's own "always fresh for export" behavior).
- Historical revenue trend (`GET /api/v1/analytics/revenue/history`): reads stored `RevenueSnapshot` rows filtered by `period`/date range — genuine granularity, backed by real captured data, not post-hoc bucketing of live numbers.
- Host-driven snapshot capture (`captureRevenueSnapshot(period)`, exposed on `GenSupInstance`) — no scheduler added to Gen_SUP; the host calls this on its own cadence and tells it which period label the call represents (mirrors both Gen_USG's own `runRollup(tenantIds, period)` shape and `announcements`' `dispatchScheduled()` host-driven convention).
- Usage trends (`GET /api/v1/analytics/usage?tenantIds=...`): caller-supplied tenant ID list, loops Gen_USG's `GET /api/v1/usage/summary?tenantId=` per ID, sums meters/trend client-side, tolerates individual tenant failures (partial results + a `failures` list), only fails the whole request if every lookup fails.

Out of scope (deferred, no current capability to build on):
- True cross-tenant usage aggregation without a caller-supplied tenant list — architecturally blocked by Gen_USG's per-tenant RLS scoping and Gen_TNT's missing bulk tenant-list endpoint. Same deferred-gap category as `dashboard`'s `TenantMetricsPort` and `tenants`' deferred `list`/search.
- Any write path into Gen_USG or Gen_TNT — this module is read-only against both.
- Snapshot retention/pruning policy for `RevenueSnapshot` — rows accumulate indefinitely in this pass, same "no cleanup yet" posture as `announcements`' `Announcement` table.

## Architecture

### Revenue analytics — extends `TenantMetricsPort`, no new port

Same shape as `dashboard`: a consumer-supplied port over a capability gap (no dedicated revenue-analytics sibling service exists in Gen_MS), not a new port. Extend the existing `domain/ports/tenant-metrics.port.ts`:

```ts
export interface TenantMetricsPort {
  countActive(): Promise<number>;
  countSignupsSince(date: Date): Promise<number>;
  sumActiveAndTrialMrr(): Promise<number>;
  countActiveTrials(): Promise<number>;
  countTrialsEndingBetween(start: Date, end: Date): Promise<number>;
  countChurnedSince(date: Date): Promise<number>;
  countByStatus(status: string): Promise<number>;

  // New for analytics:
  mrrByPlan(filter?: { region?: string }): Promise<Array<{ planCode: string | null; mrr: number; tenantCount: number }>>;
  mrrByRegion(filter?: { planCode?: string }): Promise<Array<{ region: string | null; mrr: number }>>;
  planDistribution(): Promise<Array<{ planCode: string | null; count: number }>>;
  trialConversion(from: Date, to: Date): Promise<{ trials: number; converted: number }>;
}
```

`noopTenantMetricsPort` gains matching all-empty/all-zero defaults (`mrrByPlan`/`mrrByRegion`/`planDistribution` → `async () => []`, `trialConversion` → `async () => ({ trials: 0, converted: 0 })`) — same safe-fallback philosophy as the existing methods. `averageRevenuePerTenant` needs no new port method — it's derived in the service from the already-existing `sumActiveAndTrialMrr()` / `countActive()`.

`AnalyticsService.getRevenueSnapshot(filter?: { planCode?, region? })`:
- Calls `sumActiveAndTrialMrr()`/`countActive()` for totals + average, `mrrByPlan({ region: filter?.region })`, `mrrByRegion({ planCode: filter?.planCode })`, `planDistribution()` (always unfiltered — a contextual "whole fleet" metric regardless of the request's plan/region filter), `trialConversion(from, to)`.
- ARR = MRR × 12 (matching CPMS's own convention — no separate ARR data source anywhere in Gen_MS).
- Reuses the existing `TenantMetricsUnavailableError` (502) if any port method throws — same error class `dashboard` already defined and uses, no new class needed here.
- Cached in Valkey under its own key (`sup:analytics:revenue`), own TTL (`ANALYTICS_CACHE_TTL_SEC`, default 300s, following `DASHBOARD_CACHE_TTL_SEC`'s exact pattern in `config/env.ts`) — skipped entirely when `export=csv` is requested, matching CPMS's own "always fresh for export" behavior. Cache read/write failures are non-fatal (logged, falls back to live compute), same as `dashboard`.

### Historical trend — new `RevenueSnapshot` Prisma model

```prisma
model RevenueSnapshot {
  id                      String   @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  period                  String   @db.VarChar(16) // "daily" | "weekly" | "monthly"
  capturedAt              DateTime @default(now()) @map("captured_at") @db.Timestamptz(6)
  totalMrr                Float    @map("total_mrr")
  totalArr                Float    @map("total_arr")
  averageRevenuePerTenant Float    @map("average_revenue_per_tenant")
  byPlan                  Json     @map("by_plan")
  byRegion                Json     @map("by_region")
  planDistribution        Json     @map("plan_distribution")

  @@index([period, capturedAt])
  @@map("revenue_snapshots")
}
```

`byPlan`/`byRegion`/`planDistribution` stored as `Json` (variable-shaped arrays) rather than normalized child tables — one row per capture event, same "don't over-normalize a point-in-time snapshot" reasoning as `announcements` storing `channels` as a plain array rather than a join table.

`AnalyticsService.captureRevenueSnapshot(period: "daily" | "weekly" | "monthly"): Promise<RevenueSnapshotDto>` — computes the same live revenue data as `getRevenueSnapshot()` (unfiltered), writes one `RevenueSnapshot` row, returns it. No date-boundary detection inside Gen_SUP — the host decides when to call this and which `period` label the call represents, exactly like Gen_USG's own `runRollup(tenantIds, period)` and `announcements`' `dispatchScheduled()`. Exposed on `GenSupInstance` as `analyticsService?: AnalyticsService` for the host to invoke on its own cron.

`AnalyticsService.getRevenueHistory(query: { period: string; from?: Date; to?: Date })` — reads `RevenueSnapshot` rows filtered by `period` and optional `capturedAt` range, ordered oldest-first (natural chart order), no artificial cap in this pass (snapshot volume is inherently low-frequency — daily/weekly/monthly captures, not per-request writes).

### Usage trends — new `UsgClientPort`/`HttpUsgClient`

Same shape as `feature-flags`' `FmmClientPort`/`HttpFmmClient` — Gen_USG's summary route is genuinely unauthenticated (confirmed against its actual source: no auth middleware on `/api/v1/usage/summary`, same "no auth of its own — front with the host's own authn/authz" pattern as Gen_FMM).

```ts
export interface UsgUsageSummary {
  tenantId: string;
  meters: Array<{ metric: string; unit: string; current: number; limit: number; pct: number }>;
  trend: Array<{ snapshotAt: string; period: string; metrics: Record<string, number> }>;
}

export interface UsgClientPort {
  getSummary(tenantId: string): Promise<UsgUsageSummary>;
}
```

`HttpUsgClient` — plain `fetch()` against `GEN_USG_BASE_URL`, no secret header (matching Gen_USG's real contract), `UsgHttpError extends Error` (carries `.status`), response bodies logged via the shared logger (lesson carried forward from `feature-flags`' own review-round fix — never embed the body in the thrown message, and read it via a `safeBody()` helper that calls `res.text()` directly, never itself — the exact regression class that shipped once in `HttpTntClient` and was caught only in a later review).

`AnalyticsService.getUsageAcrossTenants(tenantIds: string[])`:
```ts
async getUsageAcrossTenants(tenantIds: string[]): Promise<{
  tenants: UsgUsageSummary[];
  failures: Array<{ tenantId: string; error: string }>;
  totals: Record<string, number>; // summed current usage per metric, successful tenants only
}>
```
Loops `tenantIds`, calls `usgClient.getSummary(id)` per tenant inside a try/catch — a single tenant's failure is caught, logged, and recorded in `failures`, never aborts the rest (same per-item fault-tolerance philosophy as `announcements`' `dispatchScheduled()`). `totals` sums each meter's `current` value across every successfully-fetched tenant. If `tenantIds` is non-empty and every single lookup fails, the method throws a new `UsgClientError` (502) — a total wipeout across all requested tenants is a genuine upstream-unavailable signal, not a partial-degradation case.

## Error handling

One new error class:
```ts
export class UsgClientError extends AppError {
  constructor(message: string) {
    super(502, "USG_CLIENT_ERROR", `Gen_USG request failed: ${message}`);
  }
}
```
Reuses the existing `TenantMetricsUnavailableError` (502) for revenue-analytics port failures — no new class needed there, `dashboard` already defined it for exactly this shape of failure.

## Routes (`/api/v1/analytics`, `X-Internal-Secret` gated)

```
GET /api/v1/analytics/revenue?planCode=&region=&from=&to=&export=csv
GET /api/v1/analytics/revenue/history?period=daily&from=&to=
GET /api/v1/analytics/usage?tenantIds=id1,id2,id3
```

No POST/write routes — `captureRevenueSnapshot()` is host-invoked directly on the service instance (`instance.analyticsService?.captureRevenueSnapshot("daily")`), not an HTTP route, matching `announcements`' `dispatchScheduled()` convention exactly (a host's own cron process calls it in-process or via its own internal trigger, not over HTTP).

`tenantIds` on the usage route is a comma-separated query string, parsed and validated as an array of UUIDs (`z.string().transform(s => s.split(",")).pipe(z.array(z.string().uuid()).min(1))`).

## `createGenSup` wiring

- `GenSupModulesConfig.analytics?: boolean` (default `true`, same pattern as all four existing modules).
- `GenSupConfig.usgClient?: UsgClientPort`, `usgBaseUrl?: string` — mirrors `resolveFmmClient()`'s override-or-`requireEnv("GEN_USG_BASE_URL")` shape exactly.
- Reuses `config.tenantMetricsPort`/`config.prisma`/`config.databaseUrl`/`resolvePublisher()`-adjacent machinery already established — analytics needs Prisma (for `RevenueSnapshot`) but does NOT need the audit-event publisher (there's no mutating admin action here to audit — pure read/reporting, same as `dashboard`), so no `EventPublisher` dependency for this module.
- Mounts `/api/v1/analytics` when enabled.
- Every pre-existing test in `create-gen-sup.test.ts` must be audited individually — now a standing checklist item for every module's wiring task (this regression class has hit the project three times already).

## Testing

Unit-level, Vitest: `service.test.ts` with a mocked `TenantMetricsPort` (extending the existing dashboard test's fake-port pattern with the 4 new methods), mocked `PrismaClient` (`revenueSnapshot.create`/`findMany`, same mocking convention as `announcements`), and mocked `UsgClientPort` — covering: revenue snapshot computation + filter passthrough + caching, snapshot capture writes a row with the given period label, history query filters correctly, usage-across-tenants sums correctly and tolerates partial failures, total-wipeout throws `UsgClientError`. `http-usg-client.test.ts` (mocked `fetch`) covering `getSummary` success/404/5xx paths and the `safeBody` non-recursion regression test (same explicit test the `feature-flags` module added after the `HttpTntClient` incident). Route-level smoke tests in `create-gen-sup.test.ts` for all 3 routes gated on `X-Internal-Secret`, plus the standard pre-existing-test audit.

## Known limitations to document

- Cross-tenant usage analytics requires the caller to supply which tenant IDs to include — Gen_USG has no cross-tenant aggregation (per-tenant Postgres RLS) and Gen_TNT has no bulk tenant-list endpoint, so Gen_SUP cannot discover "all tenants" on its own.
- `RevenueSnapshot` capture is host-driven with no built-in scheduler, same as `announcements`' scheduled-dispatch sweep — nothing populates history rows unless the host calls `captureRevenueSnapshot()` on its own cadence.
- No retention/pruning policy for `RevenueSnapshot` rows in this pass.
- CSV export covers only the `byPlan` breakdown (matching CPMS's own original export scope), not the full revenue snapshot or usage data.

## A different kind of breaking change: `TenantMetricsPort` interface extension

Every prior breaking change in this project (`tenants`/`feature-flags`/`announcements` all defaulting to `true`) was a *runtime* default — an existing embedder's code kept compiling, it just needed a `modules: { x: false }` override or a new env var before its app would boot. Extending `TenantMetricsPort` with 4 new required methods is a *compile-time* break instead: any host that already wrote its own `TenantMetricsPort` implementation for `dashboard` (rather than using `noopTenantMetricsPort`) will fail to type-check until it adds `mrrByPlan`/`mrrByRegion`/`planDistribution`/`trialConversion`, whether or not that host ever enables the `analytics` module. This must be called out explicitly and separately in the integration guide's Upgrading section — it's not covered by the existing "disable the module" escape hatch, since the interface is shared by `dashboard` too.

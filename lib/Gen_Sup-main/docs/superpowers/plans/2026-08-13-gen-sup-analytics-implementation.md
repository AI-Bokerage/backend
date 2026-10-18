# Gen_SUP analytics module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an "analytics" module to Gen_SUP — revenue analytics (extends the existing `TenantMetricsPort`, matching CPMS's own feature plus real `planCode`/`region` filters), real historical MRR trend (Gen_SUP's second local-persistence table, host-driven capture), and usage trends via a new `UsgClientPort`/`HttpUsgClient` over Gen_USG (caller-supplied tenant IDs — no invented tenant discovery).

**Architecture:** `AnalyticsService(tenantMetricsPort, valkey, cacheTtlSec, prisma, usgClient)` — three concerns on one service class: (1) live revenue snapshot computed from the widened `TenantMetricsPort`, Valkey-cached; (2) `RevenueSnapshot` Prisma-backed history, written by a host-driven `captureRevenueSnapshot(period)` sweep (no scheduler, mirrors `announcements`' `dispatchScheduled()`); (3) `getUsageAcrossTenants(tenantIds)` looping a new `HttpUsgClient` per tenant, fault-tolerant per item.

**Tech Stack:** TypeScript, Express, Zod, Prisma/PostgreSQL, Valkey/ioredis, `fetch`, Vitest.

## Global Constraints

- No new npm dependencies — CSV export is hand-rolled (the `byPlan` array is small and simply-shaped), everything else reuses already-installed packages.
- `analytics` defaults to `true` in `GenSupModulesConfig`, same as all four existing modules — every pre-existing test in `create-gen-sup.test.ts` that doesn't already disable it must be audited individually (now the 4th time this project has hit this regression class — a standing checklist item for every module's wiring task).
- Extending `TenantMetricsPort` with 4 new required methods is a **compile-time breaking change**, distinct from every prior module's runtime-default break — any host with its own `TenantMetricsPort` implementation (this repo has two: `dashboard`'s test fake and `gen-sup-demo`'s `sampleTenantMetricsPort`) must be updated to satisfy the widened interface, whether or not `analytics` is even enabled. Task 1 must grep the whole repo for `TenantMetricsPort` implementers, not just the two known ones.
- Reuse the existing `resolvePublisher()`-style memoization pattern for shared resources: `Valkey` (currently only constructed by `dashboard`) and `Prisma` (currently only constructed by `announcements`) both become shared across the modules that need them, constructed once regardless of how many enabled modules ask for them.
- `HttpUsgClient` follows the `HttpFmmClient` conventions exactly: no secret header (Gen_USG's summary route is genuinely unauthenticated), a `safeBody()` helper that calls `res.text()` directly — never itself (the exact regression class that once shipped in `HttpTntClient` and was only caught in a later review) — and a body-in-log-not-message pattern for failures.
- `RevenueSnapshot`'s JSON columns (`byPlan`/`byRegion`/`planDistribution`) are the one legitimate use of an `as` cast in this module — Prisma's `Json` type is genuinely untyped at the DB level, unlike the `announcements` module's now-fixed `AnnouncementRow` mistake (a hand-copied duplicate of a fully-typed Prisma scalar model, which was a real defect fixed in that module's final review).

---

### Task 1: Extend `TenantMetricsPort` and update every implementer

**Files:**
- Modify: `packages/gen-sup-starter/src/domain/ports/tenant-metrics.port.ts`
- Modify: `packages/gen-sup-starter/src/modules/dashboard/v1/service.test.ts`
- Modify: `packages/gen-sup-demo/src/sample-tenant-metrics-port.ts`

**Interfaces:**
- Produces: `TenantMetricsPort` (4 new methods), `noopTenantMetricsPort` (updated) — consumed by Task 4 (`AnalyticsService`).

- [ ] **Step 1: Grep for every `TenantMetricsPort` implementer before touching anything**

Run: `grep -rl "TenantMetricsPort" packages/ --include="*.ts" | grep -v ".test.ts"` and separately check every `*.test.ts` file that constructs a port-shaped object literal (`fakePort`-style helpers). Confirm the only two implementers are `packages/gen-sup-starter/src/modules/dashboard/v1/service.test.ts`'s `fakePort()` helper and `packages/gen-sup-demo/src/sample-tenant-metrics-port.ts`'s `sampleTenantMetricsPort`. If you find a third, update it too and note it in your report — don't assume this plan's list is exhaustive.

- [ ] **Step 2: Extend the port interface and noop default**

```ts
// packages/gen-sup-starter/src/domain/ports/tenant-metrics.port.ts
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

// Default adapter: read-only reporting, so an all-zero/all-empty snapshot is
// a safe fallback (unlike a mutating flow, which should hard-fail without a
// real dependency). Lets createGenSup() boot with dashboard/analytics
// enabled and no TenantMetricsPort supplied yet.
export const noopTenantMetricsPort: TenantMetricsPort = {
  countActive: async () => 0,
  countSignupsSince: async () => 0,
  sumActiveAndTrialMrr: async () => 0,
  countActiveTrials: async () => 0,
  countTrialsEndingBetween: async () => 0,
  countChurnedSince: async () => 0,
  countByStatus: async () => 0,
  mrrByPlan: async () => [],
  mrrByRegion: async () => [],
  planDistribution: async () => [],
  trialConversion: async () => ({ trials: 0, converted: 0 }),
};
```

- [ ] **Step 3: Update dashboard's test fake port**

In `packages/gen-sup-starter/src/modules/dashboard/v1/service.test.ts`, add the 4 new methods to the `fakePort()` helper's returned object literal (it currently lists all 7 existing methods explicitly, so the object literal will fail to type-check once the interface grows unless these are added):

```ts
function fakePort(overrides: Partial<TenantMetricsPort> = {}): TenantMetricsPort {
  return {
    countActive: vi.fn(async () => 10),
    countSignupsSince: vi.fn(async (since: Date) => since.getTime()),
    sumActiveAndTrialMrr: vi.fn(async () => 5000),
    countActiveTrials: vi.fn(async () => 3),
    countTrialsEndingBetween: vi.fn(async () => 1),
    countChurnedSince: vi.fn(async () => 0),
    countByStatus: vi.fn(async () => 0),
    mrrByPlan: vi.fn(async () => []),
    mrrByRegion: vi.fn(async () => []),
    planDistribution: vi.fn(async () => []),
    trialConversion: vi.fn(async () => ({ trials: 0, converted: 0 })),
    ...overrides,
  };
}
```

Do not change anything else in this file — `DashboardService` never calls the 4 new methods, so no existing dashboard test's assertions change.

- [ ] **Step 4: Update the demo's sample port with richer sample data**

Read `packages/gen-sup-demo/src/sample-tenant-metrics-port.ts` first (current content is 5 sample tenants with `status`/`mrr`/`provisionedAt`/`trialEndsAt`). Replace the whole file:

```ts
// packages/gen-sup-demo/src/sample-tenant-metrics-port.ts
import type { TenantMetricsPort } from "@gen-ms/gen-sup-starter";

const SAMPLE_TENANTS = [
  { status: "ACTIVE", mrr: 199, provisionedAt: new Date("2026-08-08"), trialEndsAt: null, planCode: "STARTER", region: "us-east-1" },
  { status: "ACTIVE", mrr: 499, provisionedAt: new Date("2026-06-01"), trialEndsAt: null, planCode: "BUSINESS", region: "eu-west-1" },
  { status: "TRIAL", mrr: 0, provisionedAt: new Date("2026-08-09"), trialEndsAt: new Date("2026-08-15"), planCode: "STARTER", region: "us-east-1" },
  { status: "PAST_DUE", mrr: 99, provisionedAt: new Date("2026-05-01"), trialEndsAt: null, planCode: "STARTER", region: "ap-south-1" },
  { status: "SUSPENDED", mrr: 0, provisionedAt: new Date("2026-01-01"), trialEndsAt: null, planCode: null, region: "ap-south-1" },
];

export const sampleTenantMetricsPort: TenantMetricsPort = {
  async countActive() {
    return SAMPLE_TENANTS.filter((t) => t.status === "ACTIVE").length;
  },
  async countSignupsSince(date) {
    return SAMPLE_TENANTS.filter((t) => t.provisionedAt >= date).length;
  },
  async sumActiveAndTrialMrr() {
    return SAMPLE_TENANTS.filter((t) => t.status === "ACTIVE" || t.status === "TRIAL").reduce((sum, t) => sum + t.mrr, 0);
  },
  async countActiveTrials() {
    return SAMPLE_TENANTS.filter((t) => t.status === "TRIAL").length;
  },
  async countTrialsEndingBetween(start, end) {
    return SAMPLE_TENANTS.filter((t) => t.trialEndsAt && t.trialEndsAt >= start && t.trialEndsAt <= end).length;
  },
  async countChurnedSince(date) {
    // ponytail: fixed sample data doesn't vary by date range — real implementations should filter by date
    return SAMPLE_TENANTS.filter((t) => t.status === "PAST_DUE" || t.status === "SUSPENDED").length;
  },
  async countByStatus(status) {
    return SAMPLE_TENANTS.filter((t) => t.status === status).length;
  },
  async mrrByPlan(filter) {
    const filtered = SAMPLE_TENANTS.filter(
      (t) => (t.status === "ACTIVE" || t.status === "TRIAL") && (!filter?.region || t.region === filter.region),
    );
    const groups = new Map<string | null, { mrr: number; tenantCount: number }>();
    for (const t of filtered) {
      const g = groups.get(t.planCode) ?? { mrr: 0, tenantCount: 0 };
      g.mrr += t.mrr;
      g.tenantCount += 1;
      groups.set(t.planCode, g);
    }
    return Array.from(groups.entries()).map(([planCode, g]) => ({ planCode, ...g }));
  },
  async mrrByRegion(filter) {
    const filtered = SAMPLE_TENANTS.filter(
      (t) => (t.status === "ACTIVE" || t.status === "TRIAL") && (!filter?.planCode || t.planCode === filter.planCode),
    );
    const groups = new Map<string | null, number>();
    for (const t of filtered) {
      groups.set(t.region, (groups.get(t.region) ?? 0) + t.mrr);
    }
    return Array.from(groups.entries()).map(([region, mrr]) => ({ region, mrr }));
  },
  async planDistribution() {
    const groups = new Map<string | null, number>();
    for (const t of SAMPLE_TENANTS) {
      groups.set(t.planCode, (groups.get(t.planCode) ?? 0) + 1);
    }
    return Array.from(groups.entries()).map(([planCode, count]) => ({ planCode, count }));
  },
  async trialConversion() {
    // ponytail: fixed sample data doesn't vary by date range — real implementations should filter by date
    const trials = SAMPLE_TENANTS.filter((t) => t.trialEndsAt !== null).length;
    return { trials, converted: 0 };
  },
};
```

- [ ] **Step 5: Run the full test suite and build**

Run: `npm test --workspace=@gen-ms/gen-sup-starter && npm run build`
Expected: PASS (dashboard's existing tests unaffected; demo's build compiles against the widened interface)

- [ ] **Step 6: Commit**

```bash
git add packages/gen-sup-starter/src/domain/ports/tenant-metrics.port.ts packages/gen-sup-starter/src/modules/dashboard/v1/service.test.ts packages/gen-sup-demo/src/sample-tenant-metrics-port.ts
git commit -m "feat: extend TenantMetricsPort with grouped revenue-aggregation methods"
```

---

### Task 2: `RevenueSnapshot` Prisma model and migration

**Files:**
- Modify: `packages/gen-sup-starter/prisma/schema.prisma`
- Create: `packages/gen-sup-starter/prisma/migrations/20260813130000_add_revenue_snapshots/migration.sql`
- Modify: `packages/gen-sup-starter/src/infra/persistence/prisma-client.ts`

**Interfaces:**
- Produces: `RevenueSnapshot` (Prisma model + re-exported type) — consumed by Task 4 (`AnalyticsService`).

- [ ] **Step 1: Add the `RevenueSnapshot` model**

Append to `packages/gen-sup-starter/prisma/schema.prisma` (do not touch the existing `Announcement` model or the `generator`/`datasource` blocks):

```prisma
model RevenueSnapshot {
  id                      String   @id @default(dbgenerated("gen_random_uuid()")) @db.Uuid
  period                  String   @db.VarChar(16)
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

- [ ] **Step 2: Regenerate the Prisma client (no live database connection needed)**

Run, from `packages/gen-sup-starter/`:
```bash
npx prisma generate --schema=prisma/schema.prisma
```

- [ ] **Step 3: Create the migration**

First check whether Docker is available: `docker ps` (from any directory). If it succeeds (daemon reachable), run `docker compose up -d postgres` from the repo root, then from `packages/gen-sup-starter/`:
```bash
npx prisma migrate dev --name add_revenue_snapshots --schema=prisma/schema.prisma --skip-generate
```

**If Docker/a live Postgres is not reachable**, create the migration file by hand instead (this repo's `announcements` module already established this exact fallback pattern — no `migration_lock.toml` needed this time, it already exists from that module):

Create `packages/gen-sup-starter/prisma/migrations/20260813130000_add_revenue_snapshots/migration.sql`:
```sql
-- CreateTable
CREATE TABLE "revenue_snapshots" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "period" VARCHAR(16) NOT NULL,
    "captured_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "total_mrr" DOUBLE PRECISION NOT NULL,
    "total_arr" DOUBLE PRECISION NOT NULL,
    "average_revenue_per_tenant" DOUBLE PRECISION NOT NULL,
    "by_plan" JSONB NOT NULL,
    "by_region" JSONB NOT NULL,
    "plan_distribution" JSONB NOT NULL,

    CONSTRAINT "revenue_snapshots_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "revenue_snapshots_period_captured_at_idx" ON "revenue_snapshots"("period", "captured_at");
```

State clearly in your task report which path you took and why. If you used the hand-written path, you can independently verify it's correct without a live database by running `npx prisma migrate diff --from-empty --to-schema-datamodel=prisma/schema.prisma --script` and confirming the output matches — this is the exact verification the `announcements` module's Task 1 review already used successfully.

- [ ] **Step 4: Export the new type from the Prisma client wrapper**

Modify `packages/gen-sup-starter/src/infra/persistence/prisma-client.ts` — it currently has `export type { Announcement } from "../../../__generated__/prisma/index.js";`. Change that line to also export `RevenueSnapshot`:

```ts
export type { Announcement, RevenueSnapshot } from "../../../__generated__/prisma/index.js";
```

- [ ] **Step 5: Build and run the full test suite**

Run: `npm run build --workspace=@gen-ms/gen-sup-starter && npm test --workspace=@gen-ms/gen-sup-starter`
Expected: PASS (nothing consumes `RevenueSnapshot` yet)

- [ ] **Step 6: Commit**

```bash
git add packages/gen-sup-starter/prisma packages/gen-sup-starter/src/infra/persistence/prisma-client.ts
git commit -m "feat: add RevenueSnapshot Prisma model for historical MRR trend"
```

---

### Task 3: `UsgClientPort` + `HttpUsgClient`

**Files:**
- Create: `packages/gen-sup-starter/src/domain/ports/usg-client.port.ts`
- Create: `packages/gen-sup-starter/src/infra/external/http-usg-client.ts`
- Test: `packages/gen-sup-starter/src/infra/external/http-usg-client.test.ts`

**Interfaces:**
- Produces: `UsgClientPort`, `UsgUsageSummary`, `UsgMeterSummary`, `UsgTrendPoint`, `HttpUsgClient`, `UsgHttpError` — consumed by Task 5 (`AnalyticsService`'s usage methods) and Task 7 (`create-gen-sup.ts` wiring).

- [ ] **Step 1: Write the port interface**

```ts
// packages/gen-sup-starter/src/domain/ports/usg-client.port.ts
export interface UsgMeterSummary {
  metric: string;
  unit: string;
  current: number;
  limit: number;
  pct: number;
}

export interface UsgTrendPoint {
  snapshotAt: string;
  period: string;
  metrics: Record<string, number>;
}

export interface UsgUsageSummary {
  tenantId: string;
  meters: UsgMeterSummary[];
  trend: UsgTrendPoint[];
}

export interface UsgClientPort {
  getSummary(tenantId: string): Promise<UsgUsageSummary>;
}
```

- [ ] **Step 2: Write the failing test**

```ts
// packages/gen-sup-starter/src/infra/external/http-usg-client.test.ts
import { describe, it, expect, vi, afterEach } from "vitest";
import { HttpUsgClient, UsgHttpError } from "./http-usg-client.ts";
import { logger } from "../../common/logger.ts";

function mockFetchOnce(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      status,
      ok: status >= 200 && status < 300,
      json: async () => body,
      text: async () => JSON.stringify(body),
    }),
  );
}

describe("HttpUsgClient", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("getSummary returns the summary on 200", async () => {
    const summary = {
      tenantId: "t1",
      meters: [{ metric: "api_calls", unit: "count", current: 500, limit: 10000, pct: 5 }],
      trend: [{ snapshotAt: "2026-08-01T00:00:00Z", period: "daily", metrics: { api_calls: 500 } }],
    };
    mockFetchOnce(200, summary);
    const client = new HttpUsgClient("http://gen-usg");
    expect(await client.getSummary("t1")).toEqual(summary);
  });

  it("getSummary throws UsgHttpError on a non-2xx status", async () => {
    mockFetchOnce(404, { error: "not found" });
    const client = new HttpUsgClient("http://gen-usg");
    await expect(client.getSummary("missing")).rejects.toMatchObject({ status: 404 });
  });

  it("getSummary throws UsgHttpError on a 5xx status", async () => {
    mockFetchOnce(500, { error: "boom" });
    const client = new HttpUsgClient("http://gen-usg");
    await expect(client.getSummary("t1")).rejects.toMatchObject({ status: 500 });
  });

  it("does not send an X-Internal-Secret header (Gen_USG's summary route is unauthenticated)", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 200, ok: true, json: async () => ({ tenantId: "t1", meters: [], trend: [] }), text: async () => "{}" });
    vi.stubGlobal("fetch", fetchMock);
    const client = new HttpUsgClient("http://gen-usg");
    await client.getSummary("t1");
    const [, opts] = fetchMock.mock.calls[0];
    expect(opts?.headers).toBeUndefined();
  });

  it("logs the actual response body text on failure (not swallowed to empty)", async () => {
    mockFetchOnce(500, { error: "boom" });
    const warnSpy = vi.spyOn(logger, "warn").mockImplementation(() => undefined as never);
    const client = new HttpUsgClient("http://gen-usg");
    await expect(client.getSummary("t1")).rejects.toThrow(UsgHttpError);
    expect(warnSpy).toHaveBeenCalledWith(
      expect.objectContaining({ status: 500, body: JSON.stringify({ error: "boom" }) }),
      expect.any(String),
    );
    warnSpy.mockRestore();
  });
});
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- http-usg-client`
Expected: FAIL — `Cannot find module './http-usg-client.ts'`

- [ ] **Step 4: Implement `HttpUsgClient`**

```ts
// packages/gen-sup-starter/src/infra/external/http-usg-client.ts
import type { UsgClientPort, UsgUsageSummary } from "../../domain/ports/usg-client.port.ts";
import { logger } from "../../common/logger.ts";

export class UsgHttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "UsgHttpError";
  }
}

export class HttpUsgClient implements UsgClientPort {
  constructor(private readonly baseUrl: string) {}

  // IMPORTANT: this must call res.text() directly, never itself. The tenants
  // module's HttpTntClient once had a version of this that called
  // `this.safeBody(res)` instead of `res.text()` — infinite recursion caught
  // by its own try/catch, silently always logging an empty body.
  private async safeBody(res: Response): Promise<string> {
    try {
      return await res.text();
    } catch {
      return "";
    }
  }

  async getSummary(tenantId: string): Promise<UsgUsageSummary> {
    const res = await fetch(`${this.baseUrl}/api/v1/usage/summary?tenantId=${encodeURIComponent(tenantId)}`);
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-usg-client] getSummary failed");
      throw new UsgHttpError(res.status, `Gen_USG getSummary failed: ${res.status}`);
    }
    return (await res.json()) as UsgUsageSummary;
  }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- http-usg-client`
Expected: PASS (5 tests)

- [ ] **Step 6: Commit**

```bash
git add packages/gen-sup-starter/src/domain/ports/usg-client.port.ts packages/gen-sup-starter/src/infra/external/http-usg-client.ts packages/gen-sup-starter/src/infra/external/http-usg-client.test.ts
git commit -m "feat: add UsgClientPort and HttpUsgClient"
```

---

### Task 4: `AnalyticsService` — revenue snapshot, capture, and history

**Files:**
- Create: `packages/gen-sup-starter/src/modules/analytics/v1/types.ts`
- Create: `packages/gen-sup-starter/src/modules/analytics/v1/service.ts`
- Test: `packages/gen-sup-starter/src/modules/analytics/v1/service.test.ts`

**Interfaces:**
- Consumes: `TenantMetricsPort` (Task 1), `RevenueSnapshot`/`PrismaClient` (Task 2), `TenantMetricsUnavailableError` (existing).
- Produces: `AnalyticsService` (methods `getRevenueSnapshot`, `captureRevenueSnapshot`, `getRevenueHistory` in this task; `getUsageAcrossTenants` added in Task 5), `RevenueSnapshotDto`, `RevenueFilter`, `RevenueHistoryQuery`, `RevenueHistoryEntry`, `SnapshotPeriod` — consumed by Task 5 (same class), Task 6 (controller), Task 7 (wiring).

- [ ] **Step 1: Write `types.ts`**

```ts
// packages/gen-sup-starter/src/modules/analytics/v1/types.ts
import type { UsgUsageSummary } from "../../../domain/ports/usg-client.port.ts";

export interface RevenueBreakdownByPlan {
  planCode: string | null;
  mrr: number;
  tenantCount: number;
}

export interface RevenueBreakdownByRegion {
  region: string | null;
  mrr: number;
}

export interface PlanDistributionEntry {
  planCode: string | null;
  count: number;
  percentage: number;
}

export interface TrialConversionStats {
  trials: number;
  converted: number;
  rate: number;
}

export interface RevenueSnapshotDto {
  totalMrr: number;
  totalArr: number;
  averageRevenuePerTenant: number;
  byPlan: RevenueBreakdownByPlan[];
  byRegion: RevenueBreakdownByRegion[];
  planDistribution: PlanDistributionEntry[];
  trialConversion: TrialConversionStats;
  generatedAt: string;
}

export interface RevenueFilter {
  planCode?: string;
  region?: string;
  from?: Date;
  to?: Date;
}

export type SnapshotPeriod = "daily" | "weekly" | "monthly";

export interface RevenueHistoryQuery {
  period: SnapshotPeriod;
  from?: Date;
  to?: Date;
}

export interface RevenueHistoryEntry {
  id: string;
  period: string;
  capturedAt: string;
  totalMrr: number;
  totalArr: number;
  averageRevenuePerTenant: number;
  byPlan: RevenueBreakdownByPlan[];
  byRegion: RevenueBreakdownByRegion[];
  planDistribution: PlanDistributionEntry[];
}

export interface UsageAcrossTenantsResult {
  tenants: UsgUsageSummary[];
  failures: Array<{ tenantId: string; error: string }>;
  totals: Record<string, number>;
}
```

- [ ] **Step 2: Write the failing test for revenue snapshot/capture/history**

```ts
// packages/gen-sup-starter/src/modules/analytics/v1/service.test.ts
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { AnalyticsService } from "./service.ts";
import { TenantMetricsUnavailableError } from "../../../common/errors.ts";
import type { TenantMetricsPort } from "../../../domain/ports/tenant-metrics.port.ts";
import type { PrismaClient } from "../../../infra/persistence/prisma-client.ts";

function fakePort(overrides: Partial<TenantMetricsPort> = {}): TenantMetricsPort {
  return {
    countActive: vi.fn(async () => 10),
    countSignupsSince: vi.fn(async () => 0),
    sumActiveAndTrialMrr: vi.fn(async () => 5000),
    countActiveTrials: vi.fn(async () => 3),
    countTrialsEndingBetween: vi.fn(async () => 0),
    countChurnedSince: vi.fn(async () => 0),
    countByStatus: vi.fn(async () => 0),
    mrrByPlan: vi.fn(async () => [{ planCode: "BUSINESS", mrr: 3000, tenantCount: 6 }, { planCode: "STARTER", mrr: 2000, tenantCount: 20 }]),
    mrrByRegion: vi.fn(async () => [{ region: "us-east-1", mrr: 4000 }, { region: "eu-west-1", mrr: 1000 }]),
    planDistribution: vi.fn(async () => [{ planCode: "BUSINESS", count: 6 }, { planCode: "STARTER", count: 20 }, { planCode: null, count: 4 }]),
    trialConversion: vi.fn(async () => ({ trials: 10, converted: 4 })),
    ...overrides,
  };
}

function fakeValkey(store = new Map<string, string>()) {
  return {
    get: vi.fn(async (key: string) => store.get(key) ?? null),
    set: vi.fn(async (key: string, value: string) => {
      store.set(key, value);
      return "OK";
    }),
  };
}

function baseSnapshotRow(overrides: Record<string, unknown> = {}) {
  return {
    id: "rs1",
    period: "daily",
    capturedAt: new Date("2026-08-01T00:00:00Z"),
    totalMrr: 5000,
    totalArr: 60000,
    averageRevenuePerTenant: 500,
    byPlan: [{ planCode: "BUSINESS", mrr: 3000, tenantCount: 6 }],
    byRegion: [{ region: "us-east-1", mrr: 4000 }],
    planDistribution: [{ planCode: "BUSINESS", count: 6, percentage: 100 }],
    ...overrides,
  };
}

function fakePrisma(overrides: Record<string, unknown> = {}) {
  return {
    revenueSnapshot: {
      create: vi.fn(async ({ data }: { data: Record<string, unknown> }) => baseSnapshotRow(data)),
      findMany: vi.fn(async () => [baseSnapshotRow()]),
      ...overrides,
    },
  } as unknown as PrismaClient;
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-08-10T12:00:00Z"));
});

afterEach(() => {
  vi.useRealTimers();
});

describe("AnalyticsService", () => {
  describe("getRevenueSnapshot", () => {
    it("computes totals, ARR, average, and percentages from the port on a cache miss", async () => {
      const port = fakePort();
      const valkey = fakeValkey();
      const service = new AnalyticsService(port, valkey as never, 300, fakePrisma(), { getSummary: vi.fn() });

      const snapshot = await service.getRevenueSnapshot();

      expect(snapshot.totalMrr).toBe(5000);
      expect(snapshot.totalArr).toBe(60000);
      expect(snapshot.averageRevenuePerTenant).toBe(500);
      expect(snapshot.byPlan).toHaveLength(2);
      expect(snapshot.planDistribution.find((p) => p.planCode === "BUSINESS")?.percentage).toBeCloseTo(20, 5);
      expect(snapshot.trialConversion).toEqual({ trials: 10, converted: 4, rate: 0.4 });
      expect(valkey.set).toHaveBeenCalledTimes(1);
    });

    it("returns the cached value without calling the port when unfiltered and cached", async () => {
      const port = fakePort();
      const store = new Map<string, string>();
      store.set("sup:analytics:revenue", JSON.stringify({ totalMrr: 99 }));
      const valkey = fakeValkey(store);
      const service = new AnalyticsService(port, valkey as never, 300, fakePrisma(), { getSummary: vi.fn() });

      const snapshot = await service.getRevenueSnapshot();

      expect(snapshot).toEqual({ totalMrr: 99 });
      expect(port.sumActiveAndTrialMrr).not.toHaveBeenCalled();
    });

    it("bypasses the cache entirely when a planCode/region filter is supplied", async () => {
      const port = fakePort();
      const store = new Map<string, string>();
      store.set("sup:analytics:revenue", JSON.stringify({ totalMrr: 99 }));
      const valkey = fakeValkey(store);
      const service = new AnalyticsService(port, valkey as never, 300, fakePrisma(), { getSummary: vi.fn() });

      const snapshot = await service.getRevenueSnapshot({ region: "us-east-1" });

      expect(snapshot.totalMrr).toBe(5000);
      expect(port.mrrByPlan).toHaveBeenCalledWith({ region: "us-east-1" });
      expect(valkey.set).not.toHaveBeenCalled();
    });

    it("throws TenantMetricsUnavailableError when the port fails", async () => {
      const port = fakePort({ sumActiveAndTrialMrr: vi.fn(async () => { throw new Error("port down"); }) });
      const valkey = fakeValkey();
      const service = new AnalyticsService(port, valkey as never, 300, fakePrisma(), { getSummary: vi.fn() });

      await expect(service.getRevenueSnapshot()).rejects.toThrow(TenantMetricsUnavailableError);
    });
  });

  describe("captureRevenueSnapshot", () => {
    it("writes a RevenueSnapshot row with the given period label", async () => {
      const port = fakePort();
      const prisma = fakePrisma();
      const service = new AnalyticsService(port, fakeValkey() as never, 300, prisma, { getSummary: vi.fn() });

      const entry = await service.captureRevenueSnapshot("weekly");

      expect(prisma.revenueSnapshot.create).toHaveBeenCalledWith(expect.objectContaining({
        data: expect.objectContaining({ period: "weekly", totalMrr: 5000 }),
      }));
      // fakePrisma's create() mock spreads the create() call's `data` over
      // baseSnapshotRow's defaults, so the returned row reflects what was
      // actually passed in — period is "weekly" here, not the default "daily".
      expect(entry.period).toBe("weekly");
    });
  });

  describe("getRevenueHistory", () => {
    it("queries by period and returns mapped entries", async () => {
      const findMany = vi.fn(async () => [baseSnapshotRow()]);
      const prisma = fakePrisma({ findMany });
      const service = new AnalyticsService(fakePort(), fakeValkey() as never, 300, prisma, { getSummary: vi.fn() });

      const history = await service.getRevenueHistory({ period: "daily" });

      expect(history).toHaveLength(1);
      expect(typeof history[0]!.capturedAt).toBe("string");
      expect(findMany).toHaveBeenCalledWith(expect.objectContaining({ where: { period: "daily" }, orderBy: { capturedAt: "asc" } }));
    });

    it("includes a date range in the where clause when from/to are provided", async () => {
      const findMany = vi.fn(async () => []);
      const prisma = fakePrisma({ findMany });
      const service = new AnalyticsService(fakePort(), fakeValkey() as never, 300, prisma, { getSummary: vi.fn() });

      await service.getRevenueHistory({ period: "monthly", from: new Date("2026-01-01"), to: new Date("2026-06-01") });

      expect(findMany).toHaveBeenCalledWith(expect.objectContaining({
        where: { period: "monthly", capturedAt: { gte: new Date("2026-01-01"), lte: new Date("2026-06-01") } },
      }));
    });
  });
});
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- modules/analytics`
Expected: FAIL — `Cannot find module './service.ts'`

- [ ] **Step 4: Implement `service.ts` (revenue snapshot/capture/history only — `getUsageAcrossTenants` is added in Task 5)**

```ts
// packages/gen-sup-starter/src/modules/analytics/v1/service.ts
import type { Redis } from "ioredis";
import type { PrismaClient, RevenueSnapshot } from "../../../infra/persistence/prisma-client.ts";
import type { TenantMetricsPort } from "../../../domain/ports/tenant-metrics.port.ts";
import type { UsgClientPort } from "../../../domain/ports/usg-client.port.ts";
import { TenantMetricsUnavailableError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import type {
  RevenueSnapshotDto,
  RevenueFilter,
  RevenueHistoryQuery,
  RevenueHistoryEntry,
  SnapshotPeriod,
  PlanDistributionEntry,
  RevenueBreakdownByPlan,
  RevenueBreakdownByRegion,
} from "./types.ts";

const CACHE_KEY = "sup:analytics:revenue";
const TRIAL_CONVERSION_WINDOW_MS = 30 * 86_400_000; // default trailing 30-day window when from/to aren't supplied

function withPercentage(dist: Array<{ planCode: string | null; count: number }>): PlanDistributionEntry[] {
  const total = dist.reduce((sum, d) => sum + d.count, 0);
  return dist.map((d) => ({ ...d, percentage: total > 0 ? (d.count / total) * 100 : 0 }));
}

function toHistoryEntry(row: RevenueSnapshot): RevenueHistoryEntry {
  return {
    id: row.id,
    period: row.period,
    capturedAt: row.capturedAt.toISOString(),
    totalMrr: row.totalMrr,
    totalArr: row.totalArr,
    averageRevenuePerTenant: row.averageRevenuePerTenant,
    byPlan: row.byPlan as RevenueBreakdownByPlan[],
    byRegion: row.byRegion as RevenueBreakdownByRegion[],
    planDistribution: row.planDistribution as PlanDistributionEntry[],
  };
}

export class AnalyticsService {
  constructor(
    private readonly port: TenantMetricsPort,
    private readonly valkey: Redis,
    private readonly cacheTtlSec: number,
    private readonly prisma: PrismaClient,
    private readonly usgClient: UsgClientPort,
  ) {}

  private async computeRevenueSnapshot(filter: RevenueFilter): Promise<RevenueSnapshotDto> {
    const now = new Date();
    const to = filter.to ?? now;
    const from = filter.from ?? new Date(to.getTime() - TRIAL_CONVERSION_WINDOW_MS);

    let totalMrr: number;
    let activeCount: number;
    let byPlan: RevenueBreakdownByPlan[];
    let byRegion: RevenueBreakdownByRegion[];
    let planDistributionRaw: Array<{ planCode: string | null; count: number }>;
    let trialConversionRaw: { trials: number; converted: number };
    try {
      [totalMrr, activeCount, byPlan, byRegion, planDistributionRaw, trialConversionRaw] = await Promise.all([
        this.port.sumActiveAndTrialMrr(),
        this.port.countActive(),
        this.port.mrrByPlan(filter.region ? { region: filter.region } : undefined),
        this.port.mrrByRegion(filter.planCode ? { planCode: filter.planCode } : undefined),
        this.port.planDistribution(),
        this.port.trialConversion(from, to),
      ]);
    } catch (err) {
      throw new TenantMetricsUnavailableError(err);
    }

    const averageRevenuePerTenant = activeCount > 0 ? totalMrr / activeCount : 0;
    const rate = trialConversionRaw.trials > 0 ? trialConversionRaw.converted / trialConversionRaw.trials : 0;

    return {
      totalMrr,
      totalArr: totalMrr * 12,
      averageRevenuePerTenant,
      byPlan,
      byRegion,
      planDistribution: withPercentage(planDistributionRaw),
      trialConversion: { ...trialConversionRaw, rate },
      generatedAt: new Date().toISOString(),
    };
  }

  // Only the unfiltered (whole-fleet, no planCode/region/from/to) snapshot is
  // cached — a filtered/date-ranged query is computed fresh every time,
  // avoiding an explosion of per-filter cache key permutations.
  async getRevenueSnapshot(filter: RevenueFilter = {}): Promise<RevenueSnapshotDto> {
    const cacheable = !filter.planCode && !filter.region && !filter.from && !filter.to;

    if (cacheable) {
      try {
        const cached = await this.valkey.get(CACHE_KEY);
        if (cached) return JSON.parse(cached) as RevenueSnapshotDto;
      } catch (err) {
        logger.warn({ err }, "[Analytics] Valkey read failed — computing live");
      }
    }

    const snapshot = await this.computeRevenueSnapshot(filter);

    if (cacheable) {
      try {
        await this.valkey.set(CACHE_KEY, JSON.stringify(snapshot), "EX", this.cacheTtlSec);
      } catch (err) {
        logger.warn({ err }, "[Analytics] Valkey write failed — snapshot not cached");
      }
    }

    return snapshot;
  }

  async captureRevenueSnapshot(period: SnapshotPeriod): Promise<RevenueHistoryEntry> {
    const snapshot = await this.computeRevenueSnapshot({});
    const row = (await this.prisma.revenueSnapshot.create({
      data: {
        period,
        totalMrr: snapshot.totalMrr,
        totalArr: snapshot.totalArr,
        averageRevenuePerTenant: snapshot.averageRevenuePerTenant,
        byPlan: snapshot.byPlan,
        byRegion: snapshot.byRegion,
        planDistribution: snapshot.planDistribution,
      },
    })) as RevenueSnapshot;
    return toHistoryEntry(row);
  }

  async getRevenueHistory(query: RevenueHistoryQuery): Promise<RevenueHistoryEntry[]> {
    const where: { period: string; capturedAt?: { gte?: Date; lte?: Date } } = { period: query.period };
    if (query.from || query.to) {
      where.capturedAt = {};
      if (query.from) where.capturedAt.gte = query.from;
      if (query.to) where.capturedAt.lte = query.to;
    }
    const rows = (await this.prisma.revenueSnapshot.findMany({
      where,
      orderBy: { capturedAt: "asc" },
    })) as RevenueSnapshot[];
    return rows.map(toHistoryEntry);
  }
}
```

Note: the constructor already takes a `usgClient` parameter even though this task doesn't use it yet — Task 5 adds `getUsageAcrossTenants` to this same class without changing the constructor signature again. Tests in this task pass a minimal `{ getSummary: vi.fn() }` stub for that parameter since it's unused until Task 5.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- modules/analytics`
Expected: PASS (8 tests)

- [ ] **Step 6: Commit**

```bash
git add packages/gen-sup-starter/src/modules/analytics/v1/types.ts packages/gen-sup-starter/src/modules/analytics/v1/service.ts packages/gen-sup-starter/src/modules/analytics/v1/service.test.ts
git commit -m "feat: add AnalyticsService revenue snapshot, capture, and history"
```

---

### Task 5: `AnalyticsService.getUsageAcrossTenants()`

**Files:**
- Modify: `packages/gen-sup-starter/src/modules/analytics/v1/service.ts`
- Modify: `packages/gen-sup-starter/src/modules/analytics/v1/service.test.ts`
- Modify: `packages/gen-sup-starter/src/common/errors.ts`

**Interfaces:**
- Produces: `AnalyticsService.getUsageAcrossTenants(tenantIds: string[]): Promise<UsageAcrossTenantsResult>`, `UsgClientError` — consumed by Task 6 (controller).

- [ ] **Step 1: Add the `UsgClientError` class**

Append to `packages/gen-sup-starter/src/common/errors.ts` (do not touch any existing class):

```ts
export class UsgClientError extends AppError {
  constructor(message: string) {
    super(502, "USG_CLIENT_ERROR", `Gen_USG request failed: ${message}`);
  }
}
```

- [ ] **Step 2: Write the failing test**

Append to `service.test.ts`:

```ts
import { UsgClientError } from "../../../common/errors.ts";
import type { UsgClientPort, UsgUsageSummary } from "../../../domain/ports/usg-client.port.ts";

function fakeUsgSummary(overrides: Partial<UsgUsageSummary> = {}): UsgUsageSummary {
  return {
    tenantId: "t1",
    meters: [{ metric: "api_calls", unit: "count", current: 100, limit: 10000, pct: 1 }],
    trend: [],
    ...overrides,
  };
}

function fakeUsgClient(overrides: Partial<UsgClientPort> = {}): UsgClientPort {
  return {
    getSummary: vi.fn(async (tenantId: string) => fakeUsgSummary({ tenantId })),
    ...overrides,
  };
}

describe("AnalyticsService.getUsageAcrossTenants", () => {
  it("sums metric totals across successful tenants", async () => {
    const usgClient = fakeUsgClient({
      getSummary: vi.fn(async (tenantId: string) => fakeUsgSummary({
        tenantId,
        meters: [{ metric: "api_calls", unit: "count", current: tenantId === "t1" ? 100 : 200, limit: 10000, pct: 1 }],
      })),
    });
    const service = new AnalyticsService(fakePort(), fakeValkey() as never, 300, fakePrisma(), usgClient);

    const result = await service.getUsageAcrossTenants(["t1", "t2"]);

    expect(result.tenants).toHaveLength(2);
    expect(result.failures).toEqual([]);
    expect(result.totals).toEqual({ api_calls: 300 });
  });

  it("tolerates a single tenant's failure and still returns the rest", async () => {
    const usgClient = fakeUsgClient({
      getSummary: vi.fn(async (tenantId: string) => {
        if (tenantId === "bad") throw new Error("upstream 500");
        return fakeUsgSummary({ tenantId });
      }),
    });
    const service = new AnalyticsService(fakePort(), fakeValkey() as never, 300, fakePrisma(), usgClient);

    const result = await service.getUsageAcrossTenants(["t1", "bad"]);

    expect(result.tenants).toHaveLength(1);
    expect(result.failures).toEqual([{ tenantId: "bad", error: "upstream 500" }]);
    expect(result.totals).toEqual({ api_calls: 100 });
  });

  it("throws UsgClientError when every tenant lookup fails", async () => {
    const usgClient = fakeUsgClient({
      getSummary: vi.fn(async () => { throw new Error("all down"); }),
    });
    const service = new AnalyticsService(fakePort(), fakeValkey() as never, 300, fakePrisma(), usgClient);

    await expect(service.getUsageAcrossTenants(["t1", "t2"])).rejects.toThrow(UsgClientError);
  });
});
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- modules/analytics`
Expected: FAIL — `service.getUsageAcrossTenants is not a function`

- [ ] **Step 4: Implement `getUsageAcrossTenants`**

Add this method to the `AnalyticsService` class in `service.ts` (alongside the existing methods), and add `UsgClientError` and `UsageAcrossTenantsResult` to the file's existing imports:

```ts
  async getUsageAcrossTenants(tenantIds: string[]): Promise<UsageAcrossTenantsResult> {
    const tenants: UsgUsageSummary[] = [];
    const failures: Array<{ tenantId: string; error: string }> = [];

    for (const tenantId of tenantIds) {
      try {
        const summary = await this.usgClient.getSummary(tenantId);
        tenants.push(summary);
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        failures.push({ tenantId, error: message });
        logger.warn({ err, tenantId }, "[Analytics] getSummary failed for tenant — continuing");
      }
    }

    if (tenantIds.length > 0 && tenants.length === 0) {
      throw new UsgClientError(`all ${tenantIds.length} tenant usage lookups failed`);
    }

    const totals: Record<string, number> = {};
    for (const t of tenants) {
      for (const m of t.meters) {
        totals[m.metric] = (totals[m.metric] ?? 0) + m.current;
      }
    }

    return { tenants, failures, totals };
  }
```

Add `import type { UsgUsageSummary } from "../../../domain/ports/usg-client.port.ts";` and `import { TenantMetricsUnavailableError, UsgClientError } from "../../../common/errors.ts";` (extending the existing errors import) at the top of `service.ts`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `npm test --workspace=@gen-ms/gen-sup-starter -- modules/analytics`
Expected: PASS (11 tests total: 8 from Task 4 + 3 new)

- [ ] **Step 6: Run the full suite and build**

Run: `npm test --workspace=@gen-ms/gen-sup-starter && npm run build --workspace=@gen-ms/gen-sup-starter`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add packages/gen-sup-starter/src/modules/analytics/v1/service.ts packages/gen-sup-starter/src/modules/analytics/v1/service.test.ts packages/gen-sup-starter/src/common/errors.ts
git commit -m "feat: add AnalyticsService.getUsageAcrossTenants with per-tenant fault tolerance"
```

---

### Task 6: Schema, controller, router, CSV export

**Files:**
- Create: `packages/gen-sup-starter/src/modules/analytics/v1/schema.ts`
- Create: `packages/gen-sup-starter/src/modules/analytics/v1/controller.ts`
- Create: `packages/gen-sup-starter/src/modules/analytics/v1/router.ts`

**Interfaces:**
- Consumes: `AnalyticsService` (Tasks 4-5), `internalSecret` middleware (existing).
- Produces: `createAnalyticsRouter(deps: { analyticsService, internalSecretValue })` — consumed by Task 7.

No dedicated test file — matching the established convention, controller/router behavior is verified via route-level smoke tests in `create-gen-sup.test.ts` (Task 7).

- [ ] **Step 1: Write `schema.ts`**

```ts
// packages/gen-sup-starter/src/modules/analytics/v1/schema.ts
import { z } from "zod";

const SNAPSHOT_PERIODS = ["daily", "weekly", "monthly"] as const;

export const revenueQuerySchema = z.object({
  planCode: z.string().optional(),
  region: z.string().optional(),
  from: z.string().datetime({ offset: true }).optional(),
  to: z.string().datetime({ offset: true }).optional(),
  export: z.enum(["csv"]).optional(),
});

export const revenueHistoryQuerySchema = z.object({
  period: z.enum(SNAPSHOT_PERIODS),
  from: z.string().datetime({ offset: true }).optional(),
  to: z.string().datetime({ offset: true }).optional(),
});

export const usageQuerySchema = z.object({
  tenantIds: z
    .string()
    .transform((s) => s.split(",").map((id) => id.trim()))
    .pipe(z.array(z.string().uuid()).min(1)),
});
```

- [ ] **Step 2: Write `controller.ts`**

```ts
// packages/gen-sup-starter/src/modules/analytics/v1/controller.ts
import type { Request, Response } from "express";
import { revenueQuerySchema, revenueHistoryQuerySchema, usageQuerySchema } from "./schema.ts";
import type { AnalyticsService } from "./service.ts";
import type { RevenueBreakdownByPlan } from "./types.ts";

function toCsv(rows: RevenueBreakdownByPlan[]): string {
  const header = "planCode,mrr,tenantCount";
  const lines = rows.map((r) => `${r.planCode ?? ""},${r.mrr},${r.tenantCount}`);
  return [header, ...lines].join("\n");
}

export function makeAnalyticsController(service: AnalyticsService) {
  return {
    async getRevenue(req: Request, res: Response) {
      const query = revenueQuerySchema.parse(req.query);
      const snapshot = await service.getRevenueSnapshot({
        planCode: query.planCode,
        region: query.region,
        from: query.from ? new Date(query.from) : undefined,
        to: query.to ? new Date(query.to) : undefined,
      });
      if (query.export === "csv") {
        res.setHeader("Content-Type", "text/csv");
        res.setHeader("Content-Disposition", "attachment; filename=mrr-by-plan.csv");
        res.send(toCsv(snapshot.byPlan));
        return;
      }
      res.json(snapshot);
    },
    async getRevenueHistory(req: Request, res: Response) {
      const query = revenueHistoryQuerySchema.parse(req.query);
      const history = await service.getRevenueHistory({
        period: query.period,
        from: query.from ? new Date(query.from) : undefined,
        to: query.to ? new Date(query.to) : undefined,
      });
      res.json({ history });
    },
    async getUsage(req: Request, res: Response) {
      const { tenantIds } = usageQuerySchema.parse(req.query);
      const result = await service.getUsageAcrossTenants(tenantIds);
      res.json(result);
    },
  };
}
```

- [ ] **Step 3: Write `router.ts`**

```ts
// packages/gen-sup-starter/src/modules/analytics/v1/router.ts
import { Router } from "express";
import { makeAnalyticsController } from "./controller.ts";
import type { AnalyticsService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";

export interface AnalyticsRouterDeps {
  analyticsService: AnalyticsService;
  internalSecretValue: string;
}

export function createAnalyticsRouter(deps: AnalyticsRouterDeps): Router {
  const router = Router();
  const controller = makeAnalyticsController(deps.analyticsService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret);

  router.get("/revenue", (req, res, next) => controller.getRevenue(req, res).catch(next));
  router.get("/revenue/history", (req, res, next) => controller.getRevenueHistory(req, res).catch(next));
  router.get("/usage", (req, res, next) => controller.getUsage(req, res).catch(next));

  return router;
}
```

- [ ] **Step 4: Build to confirm no type errors**

Run: `npm run build --workspace=@gen-ms/gen-sup-starter`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add packages/gen-sup-starter/src/modules/analytics/v1/schema.ts packages/gen-sup-starter/src/modules/analytics/v1/controller.ts packages/gen-sup-starter/src/modules/analytics/v1/router.ts
git commit -m "feat: add analytics controller, router, and schema"
```

---

### Task 7: Wire into `createGenSup` and the public barrel

**Files:**
- Modify: `packages/gen-sup-starter/src/create-gen-sup.ts`
- Modify: `packages/gen-sup-starter/src/config/env.ts`
- Modify: `packages/gen-sup-starter/src/index.ts`
- Modify: `.env.example`
- Modify: `packages/gen-sup-starter/src/create-gen-sup.test.ts`
- Modify: `packages/gen-sup-demo/src/index.ts`

**Interfaces:**
- Consumes: everything from Tasks 1-6.
- Produces: `GenSupConfig.usgClient`/`usgBaseUrl`, `GenSupModulesConfig.analytics`, `GenSupInstance.analyticsService` — the module is now live end-to-end.

- [ ] **Step 1: Add the env vars**

Append to `.env.example`:
```
GEN_USG_BASE_URL=http://localhost:3600
```

Add to `packages/gen-sup-starter/src/config/env.ts`'s `EnvSchema` (alongside `DASHBOARD_CACHE_TTL_SEC`):
```ts
  ANALYTICS_CACHE_TTL_SEC: z.coerce.number().default(300),
```

- [ ] **Step 2: Modify `create-gen-sup.ts`**

Add these imports alongside the existing announcements-related ones:
```ts
import type { UsgClientPort } from "./domain/ports/usg-client.port.ts";
import { HttpUsgClient } from "./infra/external/http-usg-client.ts";
import { AnalyticsService } from "./modules/analytics/v1/service.ts";
import { createAnalyticsRouter } from "./modules/analytics/v1/router.ts";
```

Extend `GenSupModulesConfig`:
```ts
export interface GenSupModulesConfig {
  dashboard?: boolean;
  tenants?: boolean;
  featureFlags?: boolean;
  announcements?: boolean;
  analytics?: boolean;
}
```

Extend `GenSupConfig`:
```ts
export interface GenSupConfig {
  tenantMetricsPort?: TenantMetricsPort;
  valkeyUrl?: string;
  internalSecret?: string;
  tntClient?: TntClientPort;
  tntBaseUrl?: string;
  tntInternalSecret?: string;
  fmmClient?: FmmClientPort;
  fmmBaseUrl?: string;
  prisma?: PrismaClient;
  databaseUrl?: string;
  usgClient?: UsgClientPort;
  usgBaseUrl?: string;
  eventPublisher?: EventPublisher;
  rabbitMqUrl?: string;
  modules?: GenSupModulesConfig;
}
```

Add `resolveUsgClient` alongside `resolveTntClient`/`resolveFmmClient`:
```ts
function resolveUsgClient(override: UsgClientPort | undefined, baseUrlOverride: string | undefined): UsgClientPort {
  if (override) return override;
  const baseUrl = baseUrlOverride ?? requireEnv("GEN_USG_BASE_URL");
  return new HttpUsgClient(baseUrl);
}
```

Delete the standalone `resolvePrismaClient` function — it's replaced by a memoized closure (below), matching `resolvePublisher()`'s shape, since `announcements` and `analytics` now both need Prisma and must share one client when both are enabled with no override:
```ts
// DELETE this function:
function resolvePrismaClient(override: PrismaClient | undefined, databaseUrlOverride: string | undefined): PrismaClient {
  if (override) return override;
  const databaseUrl = databaseUrlOverride ?? requireEnv("DATABASE_URL");
  return createPrismaClient(databaseUrl);
}
```

Extend `GenSupInstance` (add `analyticsService`, and update the `valkey`/`prisma` doc comments to mention `analytics`):
```ts
export interface GenSupInstance {
  app: Express;
  // Present only when dashboard or analytics is enabled (the two modules
  // that need a Valkey client). Exposed so a host can close the connection
  // on shutdown, e.g. `instance.valkey?.quit()`.
  valkey?: Redis;
  // Present only when at least one of tenants/featureFlags/announcements is
  // enabled and no eventPublisher override was supplied (an override is the
  // caller's own resource to manage). Exposed so a host can close the
  // RabbitMQ connection on shutdown, e.g. `instance.eventPublisher?.close()`.
  eventPublisher?: RabbitMqBus;
  // Present only when announcements or analytics is enabled and no prisma
  // override was supplied. Exposed so a host can close the connection on
  // shutdown, e.g. `instance.prisma?.$disconnect()`.
  prisma?: PrismaClient;
  // Present only when the announcements module is enabled. Exposed so a host
  // can call `instance.announcementsService?.dispatchScheduled()` on its own
  // interval/cron — Gen_SUP adds no internal scheduler.
  announcementsService?: AnnouncementsService;
  // Present only when the analytics module is enabled. Exposed so a host can
  // call `instance.analyticsService?.captureRevenueSnapshot(period)` on its
  // own interval/cron — Gen_SUP adds no internal scheduler for this either.
  analyticsService?: AnalyticsService;
}
```

In `createGenSup`, add `analytics` to the resolved `modules` object:
```ts
  const modules: Required<GenSupModulesConfig> = {
    dashboard: config.modules?.dashboard ?? true,
    tenants: config.modules?.tenants ?? true,
    featureFlags: config.modules?.featureFlags ?? true,
    announcements: config.modules?.announcements ?? true,
    analytics: config.modules?.analytics ?? true,
  };
```

Replace the existing `let valkey: Redis | undefined;` + inline `if (modules.dashboard) { valkey = createValkeyClient(...); ... }` block with a shared, memoized resolver (mirroring `resolvePublisher()`'s exact shape), and do the same for Prisma. Replace this existing block:
```ts
  let valkey: Redis | undefined;
  if (modules.dashboard) {
    valkey = createValkeyClient(resolveValkeyUrl(config.valkeyUrl));
    const dashboardService = new DashboardService(tenantMetricsPort, valkey, env.DASHBOARD_CACHE_TTL_SEC);
    app.use("/api/v1/dashboard", createDashboardRouter({ dashboardService, internalSecretValue }));
  }

  let eventPublisher: RabbitMqBus | undefined;
  let prismaInstance: PrismaClient | undefined;
  let announcementsService: AnnouncementsService | undefined;
  function resolvePublisher(): EventPublisher {
    if (config.eventPublisher) return config.eventPublisher;
    if (eventPublisher) return eventPublisher;
    const bus = new RabbitMqBus(config.rabbitMqUrl ?? requireEnv("RABBITMQ_URL"));
    eventPublisher = bus;
    return bus;
  }

  if (modules.tenants) {
    const tntClient = resolveTntClient(config.tntClient, config.tntBaseUrl, config.tntInternalSecret);
    const publisher = resolvePublisher();
    const tenantsService = new TenantsService(tntClient, publisher);
    app.use("/api/v1/tenants", createTenantsRouter({ tenantsService, internalSecretValue }));
  }

  if (modules.featureFlags) {
    const fmmClient = resolveFmmClient(config.fmmClient, config.fmmBaseUrl);
    const publisher = resolvePublisher();
    const featureFlagsService = new FeatureFlagsService(fmmClient, publisher);
    app.use("/api/v1/feature-flags", createFeatureFlagsRouter({ featureFlagsService, internalSecretValue }));
  }

  if (modules.announcements) {
    const prisma = resolvePrismaClient(config.prisma, config.databaseUrl);
    if (!config.prisma) prismaInstance = prisma;
    const publisher = resolvePublisher();
    announcementsService = new AnnouncementsService(prisma, publisher);
    app.use("/api/v1/announcements", createAnnouncementsRouter({ announcementsService, internalSecretValue }));
  }
```

with:
```ts
  let valkey: Redis | undefined;
  function resolveValkeyInstance(): Redis {
    if (valkey) return valkey;
    const client = createValkeyClient(resolveValkeyUrl(config.valkeyUrl));
    valkey = client;
    return client;
  }

  if (modules.dashboard) {
    const valkeyClient = resolveValkeyInstance();
    const dashboardService = new DashboardService(tenantMetricsPort, valkeyClient, env.DASHBOARD_CACHE_TTL_SEC);
    app.use("/api/v1/dashboard", createDashboardRouter({ dashboardService, internalSecretValue }));
  }

  let eventPublisher: RabbitMqBus | undefined;
  let prismaInstance: PrismaClient | undefined;
  let announcementsService: AnnouncementsService | undefined;
  let analyticsService: AnalyticsService | undefined;

  function resolvePublisher(): EventPublisher {
    if (config.eventPublisher) return config.eventPublisher;
    if (eventPublisher) return eventPublisher;
    const bus = new RabbitMqBus(config.rabbitMqUrl ?? requireEnv("RABBITMQ_URL"));
    eventPublisher = bus;
    return bus;
  }

  function resolvePrisma(): PrismaClient {
    if (config.prisma) return config.prisma;
    if (prismaInstance) return prismaInstance;
    const databaseUrl = config.databaseUrl ?? requireEnv("DATABASE_URL");
    const client = createPrismaClient(databaseUrl);
    prismaInstance = client;
    return client;
  }

  if (modules.tenants) {
    const tntClient = resolveTntClient(config.tntClient, config.tntBaseUrl, config.tntInternalSecret);
    const publisher = resolvePublisher();
    const tenantsService = new TenantsService(tntClient, publisher);
    app.use("/api/v1/tenants", createTenantsRouter({ tenantsService, internalSecretValue }));
  }

  if (modules.featureFlags) {
    const fmmClient = resolveFmmClient(config.fmmClient, config.fmmBaseUrl);
    const publisher = resolvePublisher();
    const featureFlagsService = new FeatureFlagsService(fmmClient, publisher);
    app.use("/api/v1/feature-flags", createFeatureFlagsRouter({ featureFlagsService, internalSecretValue }));
  }

  if (modules.announcements) {
    const prisma = resolvePrisma();
    const publisher = resolvePublisher();
    announcementsService = new AnnouncementsService(prisma, publisher);
    app.use("/api/v1/announcements", createAnnouncementsRouter({ announcementsService, internalSecretValue }));
  }

  if (modules.analytics) {
    const valkeyClient = resolveValkeyInstance();
    const prisma = resolvePrisma();
    const usgClient = resolveUsgClient(config.usgClient, config.usgBaseUrl);
    analyticsService = new AnalyticsService(tenantMetricsPort, valkeyClient, env.ANALYTICS_CACHE_TTL_SEC, prisma, usgClient);
    app.use("/api/v1/analytics", createAnalyticsRouter({ analyticsService, internalSecretValue }));
  }
```

Update the final `return` statement:
```ts
  return { app, valkey, eventPublisher, prisma: prismaInstance, announcementsService, analyticsService };
```

- [ ] **Step 3: Update `index.ts` barrel**

Add:
```ts
export type { UsgClientPort, UsgUsageSummary, UsgMeterSummary, UsgTrendPoint } from "./domain/ports/usg-client.port.ts";
export { HttpUsgClient, UsgHttpError } from "./infra/external/http-usg-client.ts";
export type {
  RevenueSnapshotDto,
  RevenueFilter,
  RevenueHistoryQuery,
  RevenueHistoryEntry,
  SnapshotPeriod,
  RevenueBreakdownByPlan,
  RevenueBreakdownByRegion,
  PlanDistributionEntry,
  TrialConversionStats,
  UsageAcrossTenantsResult,
} from "./modules/analytics/v1/types.ts";
export { AnalyticsService } from "./modules/analytics/v1/service.ts";
```

Change the existing `export type { Announcement } from "./infra/persistence/prisma-client.ts";` line to also export `RevenueSnapshot` (it should already say this after Task 2 — verify, don't duplicate the export statement).

Extend the existing errors export block to include `UsgClientError`:
```ts
export {
  AppError,
  GenSupConfigError,
  TenantMetricsUnavailableError,
  TenantSlugTakenError,
  TenantNotFoundError,
  TenantTransitionConflictError,
  TntClientError,
  FlagNotFoundError,
  OverrideNotFoundError,
  FmmClientError,
  UsgClientError,
} from "./common/errors.ts";
```

- [ ] **Step 4: Audit every pre-existing test in `create-gen-sup.test.ts`**

Read the CURRENT file first — do not assume its test count from this plan's description. As of the `announcements` module's merge it has 12 tests. Every one that constructs `createGenSup(...)` without disabling `analytics` will now fail (missing `GEN_USG_BASE_URL`/`DATABASE_URL`, or needing a `usgClient`/`prisma` override), *except* the first (`throws GenSupConfigError...`, which fails before any module resolution runs). For each of the other 11, add `analytics: false` to its existing `modules: {...}` object. Update the comment on the `/health`-with-everything-disabled test to also mention `GEN_USG_BASE_URL`.

- [ ] **Step 5: Add new tests for the analytics module**

Append to `create-gen-sup.test.ts`:

```ts
  it("exposes GET /api/v1/analytics/revenue gated on X-Internal-Secret", async () => {
    const fakePrisma = {
      revenueSnapshot: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => { throw new Error("not used in this test"); },
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const usgClient = { getSummary: async () => { throw new Error("not used in this test"); } };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      valkeyUrl: "redis://localhost:1", // unreachable on purpose — service falls back to live compute
      prisma: fakePrisma,
      usgClient,
      modules: { dashboard: false, tenants: false, featureFlags: false, announcements: false },
    });

    const unauthorized = await request(app).get("/api/v1/analytics/revenue");
    expect(unauthorized.status).toBe(401);

    const ok = await request(app).get("/api/v1/analytics/revenue").set("X-Internal-Secret", "s3cret");
    expect(ok.status).toBe(200);
    expect(ok.body.totalMrr).toBe(0);
  });

  it("exposes GET /api/v1/analytics/revenue/history gated on X-Internal-Secret", async () => {
    const fakePrisma = {
      revenueSnapshot: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => [],
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const usgClient = { getSummary: async () => { throw new Error("not used in this test"); } };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      valkeyUrl: "redis://localhost:1",
      prisma: fakePrisma,
      usgClient,
      modules: { dashboard: false, tenants: false, featureFlags: false, announcements: false },
    });

    const res = await request(app)
      .get("/api/v1/analytics/revenue/history?period=daily")
      .set("X-Internal-Secret", "s3cret");
    expect(res.status).toBe(200);
    expect(res.body.history).toEqual([]);
  });

  it("exposes GET /api/v1/analytics/usage gated on X-Internal-Secret", async () => {
    const fakePrisma = {
      revenueSnapshot: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => { throw new Error("not used in this test"); },
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const usgClient = {
      getSummary: async (tenantId: string) => ({ tenantId, meters: [{ metric: "api_calls", unit: "count", current: 1, limit: 100, pct: 1 }], trend: [] }),
    };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      valkeyUrl: "redis://localhost:1",
      prisma: fakePrisma,
      usgClient,
      modules: { dashboard: false, tenants: false, featureFlags: false, announcements: false },
    });

    const unauthorized = await request(app).get("/api/v1/analytics/usage?tenantIds=11111111-1111-1111-1111-111111111111");
    expect(unauthorized.status).toBe(401);

    const ok = await request(app)
      .get("/api/v1/analytics/usage?tenantIds=11111111-1111-1111-1111-111111111111")
      .set("X-Internal-Secret", "s3cret");
    expect(ok.status).toBe(200);
    expect(ok.body.totals).toEqual({ api_calls: 1 });
  });

  it("returns 400 VALIDATION_ERROR (not a 500) when GET /api/v1/analytics/usage has a malformed tenantId", async () => {
    const fakePrisma = {
      revenueSnapshot: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => { throw new Error("not used in this test"); },
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const usgClient = { getSummary: async () => { throw new Error("not used in this test"); } };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      valkeyUrl: "redis://localhost:1",
      prisma: fakePrisma,
      usgClient,
      modules: { dashboard: false, tenants: false, featureFlags: false, announcements: false },
    });

    const res = await request(app)
      .get("/api/v1/analytics/usage?tenantIds=not-a-uuid")
      .set("X-Internal-Secret", "s3cret");
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });
```

- [ ] **Step 6: Update `gen-sup-demo`**

Read `packages/gen-sup-demo/src/index.ts` first (its current `modules` override is `{ tenants: false, featureFlags: false, announcements: false }`). Add `analytics: false`, since the demo doesn't wire a `usgClient`:

```ts
export function startDemo() {
  const { app } = createGenSup({ tenantMetricsPort: sampleTenantMetricsPort, modules: { tenants: false, featureFlags: false, announcements: false, analytics: false } });
  const server = app.listen(PORT, () => {
    console.log(`Gen_SUP demo listening on port ${PORT}`);
  });
  return { app, server };
}
```

- [ ] **Step 7: Run the full test suite and build**

Run: `npm test && npm run build`
Expected: PASS (all tests, including the 4 new ones and the 11 updated ones)

- [ ] **Step 8: Commit**

```bash
git add packages/gen-sup-starter/src/create-gen-sup.ts packages/gen-sup-starter/src/config/env.ts packages/gen-sup-starter/src/index.ts .env.example packages/gen-sup-starter/src/create-gen-sup.test.ts packages/gen-sup-demo/src/index.ts
git commit -m "feat: wire analytics module into createGenSup and public barrel"
```

---

### Task 8: Documentation

**Files:**
- Modify: `docs/integration-guide.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: nothing (docs only).
- Produces: nothing consumed by later tasks — this is the final task.

- [ ] **Step 1: Add an "Analytics" API reference section to `docs/integration-guide.md`**

Insert after the existing "Announcements" section, before "## Error codes":

```markdown
### Analytics — `/api/v1/analytics`

Revenue analytics (extends `TenantMetricsPort` — no new port, same read-only
reporting shape as `dashboard`) plus a real historical MRR trend (Gen_SUP's
second local-persistence table) and usage trends via Gen_USG.

```
GET /api/v1/analytics/revenue?planCode=&region=&from=&to=&export=csv
X-Internal-Secret: <secret>
```

→ `200` with `{ totalMrr, totalArr, averageRevenuePerTenant, byPlan, byRegion,
planDistribution, trialConversion, generatedAt }`. `planCode`/`region` are
real filters that reshape `byRegion`/`byPlan` respectively (`planDistribution`
is always the whole-fleet view, unaffected by either filter). `from`/`to`
scope `trialConversion`'s date range only (default: trailing 30 days) — they
don't bucket the other numbers, which are always a point-in-time snapshot.
Cached for `ANALYTICS_CACHE_TTL_SEC` (default 300s) — **only when unfiltered**;
any `planCode`/`region`/`from`/`to` bypasses the cache and computes fresh, as
does `?export=csv` (which streams the `byPlan` breakdown as a CSV download).

```
GET /api/v1/analytics/revenue/history?period=daily&from=&to=
```

→ `200` with `{ history: [...] }` — stored `RevenueSnapshot` rows, real
historical granularity (not post-hoc bucketing of live numbers). **Nothing
populates this automatically** — call `instance.analyticsService?.captureRevenueSnapshot("daily"|"weekly"|"monthly")`
on your own cron; Gen_SUP adds no scheduler, matching `announcements`'
`dispatchScheduled()` convention.

```
GET /api/v1/analytics/usage?tenantIds=id1,id2,id3
```

→ `200` with `{ tenants: [...], failures: [...], totals: {...} }` — loops
Gen_USG's per-tenant `/api/v1/usage/summary` for the tenant IDs **you
supply** and sums `current` per metric. Gen_SUP cannot discover "all
tenants" on its own (Gen_USG has no cross-tenant aggregation — every
endpoint is scoped by Postgres row-level security to one tenant per call —
and Gen_TNT has no bulk tenant-list endpoint either), so the caller must
already know which tenants to include. A single tenant's lookup failure is
recorded in `failures` and doesn't fail the request; if every tenant in the
list fails, the whole request 502s (`USG_CLIENT_ERROR`).
```

- [ ] **Step 2: Update the Error codes table**

Add one row:
```markdown
| `USG_CLIENT_ERROR` | 502 | Every tenant usage lookup in the request failed |
```

- [ ] **Step 3: Update "Known limitations"**

Update the module list and add analytics-specific notes:

```markdown
- `dashboard`, `tenants`, `feature-flags`, `announcements`, and `analytics`
  are the only modules built so far. `tickets` and `impersonate` are planned.
- `analytics` is the second module with local persistence (`RevenueSnapshot`,
  alongside `announcements`' `Announcement` table).
- Cross-tenant usage analytics requires the caller to supply which tenant
  IDs to include — Gen_USG has no cross-tenant aggregation and Gen_TNT has
  no bulk tenant-list endpoint, so Gen_SUP cannot discover "all tenants" on
  its own.
- `RevenueSnapshot` capture is host-driven with no built-in scheduler, same
  as `announcements`' scheduled-dispatch sweep. No retention/pruning policy
  exists for these rows yet.
- CSV export covers only the `byPlan` breakdown, not the full revenue
  snapshot or usage data.
```

- [ ] **Step 4: Add two "Upgrading" notes — the module-toggle break AND the port-extension break**

Same breaking-change-callout style already used for `tenants`/`feature-flags`/`announcements`, but this module introduces two distinct kinds of break:

```markdown
`analytics` also now defaults to `true` — an existing embedder must pass
`modules: { analytics: false }` or supply `GEN_USG_BASE_URL` and
`DATABASE_URL` (or `usgClient`/`prisma` overrides) before upgrading, the
same as prior modules' own breaking-change notes above (`DATABASE_URL` is
shared with `announcements` if that module is already enabled).

Separately — and this one applies **even if you never enable `analytics`**:
`TenantMetricsPort` gained 4 new required methods (`mrrByPlan`,
`mrrByRegion`, `planDistribution`, `trialConversion`). If you supply your
own `TenantMetricsPort` implementation (rather than relying on
`noopTenantMetricsPort`), your implementation must add these 4 methods
before your code will type-check against this version, regardless of which
modules you enable.
```

- [ ] **Step 5: Update the Local development section if it lists modules by name**

Check `docs/integration-guide.md`'s "Local development" section — if it enumerates modules, add `analytics`.

- [ ] **Step 6: Update `README.md`**

Update the Gen_SUP status line to mention all five modules (`dashboard`, `tenants`, `feature-flags`, `announcements`, `analytics`).

- [ ] **Step 7: Run the full test suite and build once more**

Run: `npm test && npm run build`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add docs/integration-guide.md README.md
git commit -m "docs: document the analytics module in the integration guide and README"
```

---

## Plan Self-Review Notes

- **Spec coverage:** Live revenue snapshot with real `planCode`/`region` filters and cache-skip-on-filter behavior (Task 4), historical trend via a new Prisma table + host-driven capture (Task 4), usage-across-tenants with per-item fault tolerance (Task 5), CSV export (Task 6), the `TenantMetricsPort` breaking-change grep-and-fix (Task 1) — all covered by a task each.
- **Type consistency:** `RevenueSnapshotDto`/`RevenueFilter`/`RevenueHistoryQuery`/`RevenueHistoryEntry`/`UsageAcrossTenantsResult` (Task 4's `types.ts`) flow unchanged through `service.ts` (Tasks 4-5) into `controller.ts` (Task 6) — checked no field-name drift. `AnalyticsService`'s constructor signature (`port, valkey, cacheTtlSec, prisma, usgClient`) is fixed in Task 4 and never changes again in Task 5 — only new methods are added to the same class, avoiding the churn of adding constructor params after callers already exist.
- **Regression-class carryover:** Task 7 explicitly walks all 12 pre-existing `create-gen-sup.test.ts` tests (not just "the newest one") — this is now the 4th module to require this exact audit.
- **The two distinct breaking changes are each documented separately** (Task 8, Step 4) — conflating "disable the module" with "update your port implementation" would leave an embedder who only reads the module-toggle note surprised by a compile error even with `analytics` disabled.
- **Shared-resource memoization** (Valkey across `dashboard`/`analytics`, Prisma across `announcements`/`analytics`) is spelled out as an explicit before/after diff in Task 7 rather than left to implementer judgment, since it requires restructuring code Tasks 1-6 don't touch — this is the highest-risk mechanical step in the whole plan and the plan gives the implementer the literal replacement block rather than a description of the change.

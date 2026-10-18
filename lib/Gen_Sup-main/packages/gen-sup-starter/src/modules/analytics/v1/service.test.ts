import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { AnalyticsService } from "./service.ts";
import { TenantMetricsUnavailableError } from "../../../common/errors.ts";
import type { TenantMetricsPort } from "../../../domain/ports/tenant-metrics.port.ts";
import type { PrismaClient } from "../../../infra/persistence/prisma-client.ts";
import { UsgClientError } from "../../../common/errors.ts";
import type { UsgClientPort, UsgUsageSummary } from "../../../domain/ports/usg-client.port.ts";

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

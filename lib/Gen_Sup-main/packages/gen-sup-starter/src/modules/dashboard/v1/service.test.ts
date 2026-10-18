import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { DashboardService } from "./service.ts";
import { TenantMetricsUnavailableError } from "../../../common/errors.ts";
import type { TenantMetricsPort } from "../../../domain/ports/tenant-metrics.port.ts";

function fakePort(overrides: Partial<TenantMetricsPort> = {}): TenantMetricsPort {
  return {
    countActive: vi.fn(async () => 10),
    // Date-aware (returns the boundary's own epoch ms) rather than a flat
    // constant, so a bug that swaps the today/week/month call-site arguments
    // in service.ts shows up as a wrong value instead of passing silently.
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

function fakeValkey(store = new Map<string, string>()) {
  return {
    get: vi.fn(async (key: string) => store.get(key) ?? null),
    set: vi.fn(async (key: string, value: string) => {
      store.set(key, value);
      return "OK";
    }),
  };
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-08-10T12:00:00Z"));
});

afterEach(() => {
  vi.useRealTimers();
});

describe("DashboardService", () => {
  it("computes KPIs from the port on a cache miss", async () => {
    const port = fakePort();
    const valkey = fakeValkey();
    const service = new DashboardService(port, valkey as never, 300);

    const kpis = await service.getKpis();

    expect(kpis.activeTenants).toBe(10);
    expect(kpis.mrr).toBe(5000);
    expect(port.countActive).toHaveBeenCalledTimes(1);
    expect(valkey.set).toHaveBeenCalledTimes(1);

    // Verifies the today/week/month boundaries reach countSignupsSince in the
    // right slots (mirrors service.ts's own boundary math under the frozen clock).
    const now = new Date("2026-08-10T12:00:00Z");
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const weekStart = new Date(now.getTime() - 7 * 86_400_000);
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
    expect(kpis.signupsToday).toBe(todayStart.getTime());
    expect(kpis.signupsThisWeek).toBe(weekStart.getTime());
    expect(kpis.signupsThisMonth).toBe(monthStart.getTime());
  });

  it("returns the cached value without calling the port on a cache hit", async () => {
    const port = fakePort();
    const store = new Map<string, string>();
    store.set("sup:dashboard:kpi", JSON.stringify({ activeTenants: 99 }));
    const valkey = fakeValkey(store);
    const service = new DashboardService(port, valkey as never, 300);

    const kpis = await service.getKpis();

    expect(kpis).toEqual({ activeTenants: 99 });
    expect(port.countActive).not.toHaveBeenCalled();
  });

  it("bypasses the cache when forceRefresh is true", async () => {
    const port = fakePort();
    const store = new Map<string, string>();
    store.set("sup:dashboard:kpi", JSON.stringify({ activeTenants: 99 }));
    const valkey = fakeValkey(store);
    const service = new DashboardService(port, valkey as never, 300);

    const kpis = await service.getKpis(true);

    expect(kpis.activeTenants).toBe(10);
    expect(port.countActive).toHaveBeenCalledTimes(1);
  });

  it("throws TenantMetricsUnavailableError and does not cache when the port fails", async () => {
    const port = fakePort({ countActive: vi.fn(async () => { throw new Error("port down"); }) });
    const valkey = fakeValkey();
    const service = new DashboardService(port, valkey as never, 300);

    await expect(service.getKpis()).rejects.toThrow(TenantMetricsUnavailableError);
    expect(valkey.set).not.toHaveBeenCalled();
  });

  it("computes live instead of failing when Valkey itself is unavailable", async () => {
    const port = fakePort();
    const valkey = {
      get: vi.fn(async () => { throw new Error("ECONNREFUSED"); }),
      set: vi.fn(async () => { throw new Error("ECONNREFUSED"); }),
    };
    const service = new DashboardService(port, valkey as never, 300);

    const kpis = await service.getKpis();

    expect(kpis.activeTenants).toBe(10);
  });
});

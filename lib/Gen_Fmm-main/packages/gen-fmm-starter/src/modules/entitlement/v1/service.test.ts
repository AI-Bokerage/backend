import { describe, it, expect, vi } from "vitest";
import { EntitlementService } from "./service.ts";
import { EntitlementCache } from "./cache.ts";
import type { ICatalogRepo, FeatureFlagRecord } from "../../../domain/ports/catalog.repository.port.ts";
import type { ITenantOverrideRepo, TenantOverrideRecord } from "../../../domain/ports/tenant-override.repository.port.ts";
import type { ICacheStore } from "../../../domain/ports/cache-store.port.ts";

const TENANT = "11111111-1111-1111-1111-111111111111";

function fakeCacheStore(): ICacheStore {
  const data = new Map<string, string>();
  return {
    async get(k) { return data.get(k) ?? null; },
    async set(k, v) { data.set(k, v); },
    async del(k) { data.delete(k); },
    async setNx(k, v) { if (data.has(k)) return false; data.set(k, v); return true; },
  };
}

function fakeCatalogRepo(overrides: Partial<ICatalogRepo> = {}): ICatalogRepo {
  return {
    createModule: vi.fn(), findModuleByCode: vi.fn(), listModules: vi.fn(async () => []), updateModule: vi.fn(),
    createFlag: vi.fn(), findFlagByKey: vi.fn(async () => null), listFlags: vi.fn(async () => []), updateFlag: vi.fn(), deleteFlag: vi.fn(),
    upsertPlanModule: vi.fn(), listPlanModules: vi.fn(async () => []), deletePlanModule: vi.fn(),
    ...overrides,
  };
}

function fakeOverrideRepo(overrides: Partial<ITenantOverrideRepo> = {}): ITenantOverrideRepo {
  return {
    upsert: vi.fn(), findOne: vi.fn(async () => null), findAllForTenant: vi.fn(async () => []),
    listAll: vi.fn(async () => []), delete: vi.fn(),
    ...overrides,
  };
}

describe("EntitlementService.check", () => {
  it("returns FLAG_NOT_FOUND for an unknown flag and still records usage", async () => {
    const recordUsage = vi.fn();
    const service = new EntitlementService(
      fakeCatalogRepo(), fakeOverrideRepo(),
      new EntitlementCache(fakeCacheStore(), 10, 1000, 60), recordUsage,
    );
    const result = await service.check(TENANT, "missing");
    expect(result.enabled).toBe(false);
    expect(result.reason).toBe("FLAG_NOT_FOUND");
    expect(recordUsage).toHaveBeenCalledWith(TENANT, "missing", false, "FLAG_NOT_FOUND", null);
  });

  it("a tenant override wins over the flag default", async () => {
    const flag: FeatureFlagRecord = { key: "f1", moduleCode: null, defaultEnabled: false, isGradualRollout: false, rolloutPercentage: 0 };
    const override: TenantOverrideRecord = { tenantId: TENANT, flagKey: "f1", enabled: true, config: {}, reason: null, expiresAt: null, createdBy: null };
    const catalogRepo = fakeCatalogRepo({ findFlagByKey: vi.fn(async () => flag) });
    const overrideRepo = fakeOverrideRepo({ findOne: vi.fn(async () => override) });
    const service = new EntitlementService(catalogRepo, overrideRepo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    const result = await service.check(TENANT, "f1");
    expect(result).toMatchObject({ enabled: true, reason: "TENANT_OVERRIDE" });
  });

  it("a cache hit does not call the repos again", async () => {
    const flag: FeatureFlagRecord = { key: "f1", moduleCode: null, defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 0 };
    const findFlagByKey = vi.fn(async () => flag);
    const recordUsage = vi.fn();
    const catalogRepo = fakeCatalogRepo({ findFlagByKey });
    const service = new EntitlementService(catalogRepo, fakeOverrideRepo(), new EntitlementCache(fakeCacheStore(), 10, 1000, 60), recordUsage);
    await service.check(TENANT, "f1");
    await service.check(TENANT, "f1");
    expect(findFlagByKey).toHaveBeenCalledTimes(1);
    expect(recordUsage).toHaveBeenCalledTimes(2);
  });

  // Regression for the critical bug: the cache key (fmm:check:<tenant>:<flag>)
  // has no planCode component. Before the fix, whichever caller populated the
  // cache first (with or without a plan) "won" for every other caller until
  // TTL expiry — a no-plan check() run first would wrongly also suppress a
  // later plan-bearing check() that should have been granted via the plan's
  // module. On the SAME warm cache, the two calls below must resolve
  // differently and each correctly.
  it("on a warm cache, a plan-bearing check() still grants via PLAN_ENTITLEMENT even though a no-plan check() populated the cache first", async () => {
    const flag: FeatureFlagRecord = { key: "f1", moduleCode: "reporting", defaultEnabled: false, isGradualRollout: false, rolloutPercentage: 0 };
    const catalogRepo = fakeCatalogRepo({
      findFlagByKey: vi.fn(async () => flag),
      listPlanModules: vi.fn(async (planCode: string) =>
        planCode === "pro" ? [{ planCode: "pro", moduleCode: "reporting", entitlement: {} }] : [],
      ),
    });
    const cache = new EntitlementCache(fakeCacheStore(), 10, 60_000, 60);
    const service = new EntitlementService(catalogRepo, fakeOverrideRepo(), cache, vi.fn());

    // No-plan call first — populates the (plan-independent) cache entry.
    const noPlan = await service.check(TENANT, "f1");
    expect(noPlan).toMatchObject({ enabled: false, reason: "FLAG_DEFAULT" });

    // Same warm cache, but this caller passes a plan that grants the flag's
    // module — must resolve enabled, not silently inherit the no-plan miss.
    const withPlan = await service.check(TENANT, "f1", "pro");
    expect(withPlan).toMatchObject({ enabled: true, reason: "PLAN_ENTITLEMENT" });
    expect(withPlan.cacheHit).not.toBe("miss");
  });
});

describe("EntitlementService.bulk", () => {
  it("resolves every flag and records usage for each", async () => {
    const flags: FeatureFlagRecord[] = [
      { key: "f1", moduleCode: null, defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 0 },
      { key: "f2", moduleCode: null, defaultEnabled: false, isGradualRollout: false, rolloutPercentage: 0 },
    ];
    const recordUsage = vi.fn();
    const catalogRepo = fakeCatalogRepo({ listFlags: vi.fn(async () => flags) });
    const service = new EntitlementService(catalogRepo, fakeOverrideRepo(), new EntitlementCache(fakeCacheStore(), 10, 1000, 60), recordUsage);
    const result = await service.bulk(TENANT);
    expect(result.flags.f1.enabled).toBe(true);
    expect(result.flags.f2.enabled).toBe(false);
    expect(recordUsage).toHaveBeenCalledTimes(2);
  });
});

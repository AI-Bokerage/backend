import { describe, it, expect, vi } from "vitest";
import { OverrideService } from "./service.ts";
import { BusinessRuleError } from "../../../common/errors.ts";
import type { ITenantOverrideRepo, TenantOverrideRecord } from "../../../domain/ports/tenant-override.repository.port.ts";
import { EntitlementCache } from "../../entitlement/v1/cache.ts";
import type { ICacheStore } from "../../../domain/ports/cache-store.port.ts";

function fakeCacheStore(): ICacheStore {
  const data = new Map<string, string>();
  return {
    async get(k) { return data.get(k) ?? null; },
    async set(k, v) { data.set(k, v); },
    async del(k) { data.delete(k); },
    async setNx(k, v) { if (data.has(k)) return false; data.set(k, v); return true; },
  };
}

function fakeRepo(overrides: Partial<ITenantOverrideRepo> = {}): ITenantOverrideRepo {
  return {
    upsert: vi.fn(),
    findOne: vi.fn(async () => null),
    findAllForTenant: vi.fn(async () => []),
    listAll: vi.fn(async () => []),
    delete: vi.fn(async () => false),
    ...overrides,
  };
}

const TENANT = "11111111-1111-1111-1111-111111111111";

describe("OverrideService", () => {
  it("rejects an expiresAt already in the past", async () => {
    const service = new OverrideService(fakeRepo(), new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    await expect(
      service.upsert({ tenantId: TENANT, flagKey: "f1", enabled: true, expiresAt: new Date(Date.now() - 1000) }),
    ).rejects.toThrow(BusinessRuleError);
  });

  it("accepts an expiresAt in the future, persists, invalidates cache, fires onFlagChanged", async () => {
    const record: TenantOverrideRecord = {
      tenantId: TENANT, flagKey: "f1", enabled: true, config: {}, reason: null,
      expiresAt: new Date(Date.now() + 60_000), createdBy: null,
    };
    const repo = fakeRepo({ upsert: vi.fn(async () => record) });
    const cache = new EntitlementCache(fakeCacheStore(), 10, 1000, 60);
    const invalidateSpy = vi.spyOn(cache, "invalidateTenantFlag");
    const onFlagChanged = vi.fn();
    const service = new OverrideService(repo, cache, onFlagChanged);
    const result = await service.upsert({ tenantId: TENANT, flagKey: "f1", enabled: true, expiresAt: record.expiresAt! });
    expect(result).toEqual(record);
    expect(invalidateSpy).toHaveBeenCalledWith(TENANT, "f1");
    expect(onFlagChanged).toHaveBeenCalledWith("f1", TENANT);
  });

  it("accepts no expiresAt (permanent override)", async () => {
    const record: TenantOverrideRecord = {
      tenantId: TENANT, flagKey: "f1", enabled: false, config: {}, reason: null, expiresAt: null, createdBy: null,
    };
    const repo = fakeRepo({ upsert: vi.fn(async () => record) });
    const service = new OverrideService(repo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    const result = await service.upsert({ tenantId: TENANT, flagKey: "f1", enabled: false });
    expect(result.expiresAt).toBeNull();
  });

  it("delete only invalidates cache when a row was actually deleted", async () => {
    const repo = fakeRepo({ delete: vi.fn(async () => false) });
    const cache = new EntitlementCache(fakeCacheStore(), 10, 1000, 60);
    const invalidateSpy = vi.spyOn(cache, "invalidateTenantFlag");
    const onFlagChanged = vi.fn();
    const service = new OverrideService(repo, cache, onFlagChanged);
    const result = await service.delete(TENANT, "f1");
    expect(result).toBe(false);
    expect(invalidateSpy).not.toHaveBeenCalled();
    expect(onFlagChanged).not.toHaveBeenCalled();
  });
});

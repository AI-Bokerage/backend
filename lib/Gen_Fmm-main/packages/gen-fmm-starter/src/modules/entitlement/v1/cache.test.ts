import { describe, it, expect, vi } from "vitest";
import { EntitlementCache } from "./cache.ts";
import type { ICacheStore } from "../../../domain/ports/cache-store.port.ts";

function fakeCache(): ICacheStore & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    async get(key) { return data.get(key) ?? null; },
    async set(key, value) { data.set(key, value); },
    async del(key) { data.delete(key); },
    async setNx(key, value) {
      if (data.has(key)) return false;
      data.set(key, value);
      return true;
    },
  };
}

const BASE = { tenantId: "t1", flagKey: "f1", enabled: true, reason: "FLAG_DEFAULT" as const };

describe("EntitlementCache", () => {
  it("a first call is a miss and calls the fetcher", async () => {
    const cache = new EntitlementCache(fakeCache(), 100, 60_000, 60);
    const fetcher = vi.fn(async () => BASE);
    const result = await cache.resolveCheckCached("t1", "f1", fetcher);
    expect(result.cacheHit).toBe("miss");
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("a second call hits L1 without calling the fetcher again", async () => {
    const cache = new EntitlementCache(fakeCache(), 100, 60_000, 60);
    const fetcher = vi.fn(async () => BASE);
    await cache.resolveCheckCached("t1", "f1", fetcher);
    const second = await cache.resolveCheckCached("t1", "f1", fetcher);
    expect(second.cacheHit).toBe("l1");
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("invalidateTenantFlag purges L1 so the next call misses again", async () => {
    const cache = new EntitlementCache(fakeCache(), 100, 60_000, 60);
    const fetcher = vi.fn(async () => BASE);
    await cache.resolveCheckCached("t1", "f1", fetcher);
    cache.invalidateTenantFlag("t1", "f1");
    const result = await cache.resolveCheckCached("t1", "f1", fetcher);
    expect(result.cacheHit).toBe("miss");
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("resolveBulkCached is a miss on first call and caches the result", async () => {
    const underlying = fakeCache();
    const cache = new EntitlementCache(underlying, 100, 60_000, 300);
    const fetcher = vi.fn(async () => ({ tenantId: "t1", flags: { f1: { enabled: true, reason: "FLAG_DEFAULT" as const } } }));
    const result = await cache.resolveBulkCached("t1", fetcher);
    expect(result.flags.f1.enabled).toBe(true);
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(underlying.data.has("fmm:flags:t1")).toBe(true);
  });

  it("invalidateTenantFlag also purges the tenant's bulk cache entry", async () => {
    const underlying = fakeCache();
    const cache = new EntitlementCache(underlying, 100, 60_000, 300);
    await cache.resolveBulkCached("t1", async () => ({ tenantId: "t1", flags: {} }));
    cache.invalidateTenantFlag("t1", "f1");
    expect(underlying.data.has("fmm:flags:t1")).toBe(false);
  });
});

import { BoundedTtlCache } from "../../../infra/cache/bounded-ttl-cache.ts";
import { getOrSet } from "../../../infra/cache/get-or-set.ts";
import { FmmCacheKey } from "../../../infra/cache/cache-keys.ts";
import type { ICacheStore } from "../../../domain/ports/cache-store.port.ts";
import type { EntitlementCheckResult, BulkEntitlementResult } from "./types.ts";

type UncachedCheckResult = Omit<EntitlementCheckResult, "cacheHit" | "latencyMs">;

export class EntitlementCache {
  private readonly l1: BoundedTtlCache<EntitlementCheckResult>;

  constructor(
    private readonly l2: ICacheStore,
    l1MaxEntries: number,
    private readonly l1TtlMs: number,
    private readonly l2TtlSeconds: number,
  ) {
    this.l1 = new BoundedTtlCache(l1MaxEntries);
  }

  async resolveCheckCached(
    tenantId: string,
    flagKey: string,
    fetcher: () => Promise<UncachedCheckResult>,
  ): Promise<EntitlementCheckResult> {
    const start = Date.now();
    const key = FmmCacheKey.check(tenantId, flagKey);

    const l1Hit = this.l1.get(key);
    if (l1Hit) return { ...l1Hit, cacheHit: "l1", latencyMs: Date.now() - start };

    // tier starts "l2" and is only flipped to "miss" if the fetcher itself
    // actually runs — distinguishes a real L2 hit from a DB fetch inside
    // getOrSet's own cache-population path.
    let tier: "l2" | "miss" = "l2";
    const wrapped = async (): Promise<UncachedCheckResult> => {
      tier = "miss";
      return fetcher();
    };

    const resolved = (await getOrSet(this.l2, key, wrapped, this.l2TtlSeconds)) ?? (await fetcher());
    const result: EntitlementCheckResult = { ...resolved, cacheHit: tier, latencyMs: Date.now() - start };
    this.l1.set(key, result, this.l1TtlMs);
    return result;
  }

  async resolveBulkCached(
    tenantId: string,
    fetcher: () => Promise<BulkEntitlementResult>,
  ): Promise<BulkEntitlementResult> {
    const key = FmmCacheKey.bulk(tenantId);
    return getOrSet(this.l2, key, fetcher, this.l2TtlSeconds);
  }

  invalidateTenantFlag(tenantId: string, flagKey: string): void {
    this.l1.delete(FmmCacheKey.check(tenantId, flagKey));
    this.l1.deleteByPrefix(FmmCacheKey.tenantBulkPrefix(tenantId));
    void this.l2.del(FmmCacheKey.check(tenantId, flagKey));
    void this.l2.del(FmmCacheKey.bulk(tenantId));
  }

  invalidateFlag(flagKey: string): void {
    // ponytail: a catalog-level flag change (default/rollout %) can affect
    // every tenant, but this in-process L1 has no cross-tenant index to
    // scan, and the ICacheStore port has no SCAN primitive to glob-purge L2
    // safely. Correctness after a catalog change relies on the short
    // check-cache TTL (CHECK_CACHE_TTL_SECS) plus the host-supplied
    // onFlagChanged broadcast (see create-gen-fmm.ts) for other pods' L1.
    // Upgrade path: add a SCAN-capable method to ICacheStore if exact
    // immediate cross-tenant invalidation is ever required.
    void flagKey;
  }
}

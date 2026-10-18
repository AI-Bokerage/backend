import type { ICatalogRepo } from "../../../domain/ports/catalog.repository.port.ts";
import type { ITenantOverrideRepo } from "../../../domain/ports/tenant-override.repository.port.ts";
import { resolveEntitlement } from "./resolve.ts";
import type { EntitlementCache } from "./cache.ts";
import type { EntitlementCheckResult, BulkEntitlementResult } from "./types.ts";

export type RecordUsage = (
  tenantId: string,
  flagKey: string,
  enabled: boolean,
  reason: string,
  planCode: string | null,
) => void;

export class EntitlementService {
  constructor(
    private readonly catalogRepo: ICatalogRepo,
    private readonly overrideRepo: ITenantOverrideRepo,
    private readonly cache: EntitlementCache,
    private readonly recordUsage: RecordUsage,
  ) {}

  async check(tenantId: string, flagKey: string, planCode?: string): Promise<EntitlementCheckResult> {
    // The cache key (fmm:check:<tenant>:<flag>) has no planCode component, so
    // whatever gets cached here must be correct for every caller regardless
    // of which planCode (if any) they pass — never resolve the plan tier
    // inside this fetcher. moduleCode is threaded through so the plan tier
    // can be applied below, on every call, after the cache read.
    const cached = await this.cache.resolveCheckCached(tenantId, flagKey, async () => {
      const [flag, override] = await Promise.all([
        this.catalogRepo.findFlagByKey(flagKey),
        this.overrideRepo.findOne(tenantId, flagKey),
      ]);
      const resolved = resolveEntitlement(flag, override, [], tenantId, flagKey);
      return { tenantId, flagKey, moduleCode: flag?.moduleCode ?? null, ...resolved };
    });

    const result = await this.applyPlanTier(cached, planCode);
    this.recordUsage(tenantId, flagKey, result.enabled, result.reason, planCode ?? null);
    return result;
  }

  async bulk(tenantId: string, planCode?: string): Promise<BulkEntitlementResult> {
    const cached = await this.cache.resolveBulkCached(tenantId, async () => {
      const [allFlags, overrides] = await Promise.all([
        this.catalogRepo.listFlags({}),
        this.overrideRepo.findAllForTenant(tenantId),
      ]);
      const overrideMap = new Map(overrides.map((o) => [o.flagKey, o]));

      const flags: BulkEntitlementResult["flags"] = {};
      for (const flag of allFlags) {
        const override = overrideMap.get(flag.key) ?? null;
        flags[flag.key] = { moduleCode: flag.moduleCode, ...resolveEntitlement(flag, override, [], tenantId, flag.key) };
      }
      return { tenantId, flags };
    });

    // planCode is plan-tier context, applied fresh on every call (never
    // cached — see the comment in check() above), so a single planModules
    // lookup here covers every flag in the bulk result.
    const planModuleCodes = planCode ? (await this.catalogRepo.listPlanModules(planCode)).map((pm) => pm.moduleCode) : [];

    const flags: BulkEntitlementResult["flags"] = {};
    for (const [flagKey, r] of Object.entries(cached.flags)) {
      const result =
        r.reason !== "TENANT_OVERRIDE" && r.moduleCode && planModuleCodes.includes(r.moduleCode)
          ? { ...r, enabled: true, reason: "PLAN_ENTITLEMENT" as const }
          : r;
      flags[flagKey] = result;
      this.recordUsage(tenantId, flagKey, result.enabled, result.reason, planCode ?? null);
    }
    return { tenantId, flags };
  }

  // Applies the plan-entitlement tier AFTER the (plan-independent) cache
  // read/write, on every call — a plan can only ever upgrade a
  // FLAG_DEFAULT/ROLLOUT result to enabled, never override a live
  // TENANT_OVERRIDE, matching resolveEntitlement's own tier order.
  private async applyPlanTier(
    cached: EntitlementCheckResult,
    planCode: string | undefined,
  ): Promise<EntitlementCheckResult> {
    if (!planCode || cached.reason === "TENANT_OVERRIDE" || !cached.moduleCode) return cached;
    const planModules = await this.catalogRepo.listPlanModules(planCode);
    if (!planModules.some((pm) => pm.moduleCode === cached.moduleCode)) return cached;
    return { ...cached, enabled: true, reason: "PLAN_ENTITLEMENT" };
  }
}

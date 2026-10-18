export type EntitlementReason = "TENANT_OVERRIDE" | "PLAN_ENTITLEMENT" | "FLAG_DEFAULT" | "ROLLOUT" | "FLAG_NOT_FOUND";

export interface EntitlementCheckResult {
  tenantId: string;
  flagKey: string;
  enabled: boolean;
  reason: EntitlementReason;
  cacheHit: "l1" | "l2" | "miss";
  latencyMs: number;
  // The flag's moduleCode, threaded through the (plan-independent) cached
  // result so the plan-entitlement tier can be applied AFTER the cache
  // read/write, on every call, without planCode ever being part of the cache
  // key. See EntitlementService.check/bulk. Optional so pre-existing test
  // fixtures without it still type-check.
  moduleCode?: string | null;
}

export interface BulkEntitlementResult {
  tenantId: string;
  flags: Record<string, { enabled: boolean; reason: EntitlementReason; moduleCode?: string | null }>;
}

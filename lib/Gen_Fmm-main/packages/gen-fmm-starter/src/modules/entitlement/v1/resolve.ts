import { createHash } from "node:crypto";
import type { FeatureFlagRecord } from "../../../domain/ports/catalog.repository.port.ts";
import type { TenantOverrideRecord } from "../../../domain/ports/tenant-override.repository.port.ts";
import type { EntitlementReason } from "./types.ts";

export interface ResolvedEntitlement {
  enabled: boolean;
  reason: EntitlementReason;
}

export function computeRolloutBucket(tenantId: string, flagKey: string): number {
  const hash = createHash("sha256").update(`${tenantId}:${flagKey}`).digest("hex");
  return parseInt(hash.substring(0, 8), 16) % 100;
}

export function resolveEntitlement(
  flag: FeatureFlagRecord | null,
  override: TenantOverrideRecord | null,
  planModuleCodes: string[],
  tenantId: string,
  flagKey: string,
  now: Date = new Date(),
): ResolvedEntitlement {
  if (!flag) return { enabled: false, reason: "FLAG_NOT_FOUND" };

  if (override && (!override.expiresAt || override.expiresAt >= now)) {
    return { enabled: override.enabled, reason: "TENANT_OVERRIDE" };
  }

  if (flag.moduleCode && planModuleCodes.includes(flag.moduleCode)) {
    return { enabled: true, reason: "PLAN_ENTITLEMENT" };
  }

  if (!flag.isGradualRollout) {
    return { enabled: flag.defaultEnabled, reason: "FLAG_DEFAULT" };
  }

  const bucket = computeRolloutBucket(tenantId, flagKey);
  return { enabled: bucket < flag.rolloutPercentage, reason: "ROLLOUT" };
}

import { describe, it, expect } from "vitest";
import { resolveEntitlement, computeRolloutBucket } from "./resolve.ts";
import type { FeatureFlagRecord } from "../../../domain/ports/catalog.repository.port.ts";
import type { TenantOverrideRecord } from "../../../domain/ports/tenant-override.repository.port.ts";

const TENANT = "11111111-1111-1111-1111-111111111111";

function flag(overrides: Partial<FeatureFlagRecord> = {}): FeatureFlagRecord {
  return {
    key: "new_dashboard",
    moduleCode: "reporting",
    defaultEnabled: false,
    isGradualRollout: false,
    rolloutPercentage: 0,
    ...overrides,
  };
}

function override(overrides: Partial<TenantOverrideRecord> = {}): TenantOverrideRecord {
  return {
    tenantId: TENANT,
    flagKey: "new_dashboard",
    enabled: true,
    config: {},
    reason: null,
    expiresAt: null,
    createdBy: null,
    ...overrides,
  };
}

describe("resolveEntitlement", () => {
  it("returns FLAG_NOT_FOUND, disabled, when the flag doesn't exist", () => {
    const result = resolveEntitlement(null, null, [], TENANT, "missing");
    expect(result).toEqual({ enabled: false, reason: "FLAG_NOT_FOUND" });
  });

  it("a live tenant override wins over everything else", () => {
    const result = resolveEntitlement(flag({ defaultEnabled: false }), override({ enabled: true }), [], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "TENANT_OVERRIDE" });
  });

  it("a disabled override wins over a true default", () => {
    const result = resolveEntitlement(flag({ defaultEnabled: true }), override({ enabled: false }), ["reporting"], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: false, reason: "TENANT_OVERRIDE" });
  });

  it("an expired override falls through to the next tier", () => {
    const past = new Date(Date.now() - 60_000);
    const result = resolveEntitlement(flag({ defaultEnabled: true, moduleCode: null }), override({ expiresAt: past }), [], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "FLAG_DEFAULT" });
  });

  it("an override expiring in the future still applies", () => {
    const future = new Date(Date.now() + 60_000);
    const result = resolveEntitlement(flag(), override({ enabled: true, expiresAt: future }), [], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "TENANT_OVERRIDE" });
  });

  it("grants via plan entitlement when the flag's module is in the plan", () => {
    const result = resolveEntitlement(flag({ moduleCode: "reporting" }), null, ["reporting", "billing"], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "PLAN_ENTITLEMENT" });
  });

  it("falls through plan entitlement when the module isn't in the plan", () => {
    const result = resolveEntitlement(flag({ moduleCode: "reporting", defaultEnabled: false }), null, ["billing"], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: false, reason: "FLAG_DEFAULT" });
  });

  it("falls through plan entitlement when the flag has no module", () => {
    const result = resolveEntitlement(flag({ moduleCode: null, defaultEnabled: true }), null, ["reporting"], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "FLAG_DEFAULT" });
  });

  it("returns FLAG_DEFAULT true when not gradual rollout and default is true", () => {
    const result = resolveEntitlement(flag({ moduleCode: null, defaultEnabled: true, isGradualRollout: false }), null, [], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "FLAG_DEFAULT" });
  });

  it("rollout at 100% always enables", () => {
    const result = resolveEntitlement(flag({ moduleCode: null, isGradualRollout: true, rolloutPercentage: 100 }), null, [], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: true, reason: "ROLLOUT" });
  });

  it("rollout at 0% always disables", () => {
    const result = resolveEntitlement(flag({ moduleCode: null, isGradualRollout: true, rolloutPercentage: 0 }), null, [], TENANT, "new_dashboard");
    expect(result).toEqual({ enabled: false, reason: "ROLLOUT" });
  });

  it("gradual rollout ignores defaultEnabled entirely", () => {
    const result = resolveEntitlement(
      flag({ moduleCode: null, isGradualRollout: true, rolloutPercentage: 100, defaultEnabled: false }),
      null, [], TENANT, "new_dashboard",
    );
    expect(result).toEqual({ enabled: true, reason: "ROLLOUT" });
  });
});

describe("computeRolloutBucket", () => {
  it("always returns a value in [0, 99]", () => {
    for (let i = 0; i < 50; i++) {
      const bucket = computeRolloutBucket(`tenant-${i}`, "some_flag");
      expect(bucket).toBeGreaterThanOrEqual(0);
      expect(bucket).toBeLessThanOrEqual(99);
    }
  });

  it("is deterministic for the same inputs", () => {
    expect(computeRolloutBucket(TENANT, "new_dashboard")).toBe(computeRolloutBucket(TENANT, "new_dashboard"));
  });

  it("varies across different tenant/flag pairs", () => {
    const buckets = new Set<number>();
    for (let i = 0; i < 20; i++) buckets.add(computeRolloutBucket(`tenant-${i}`, "new_dashboard"));
    expect(buckets.size).toBeGreaterThan(1);
  });
});

import { describe, it, expect, vi } from "vitest";
import { FeatureFlagsService } from "./service.ts";
import { FmmHttpError } from "../../../infra/external/http-fmm-client.ts";
import { FlagNotFoundError, FmmClientError } from "../../../common/errors.ts";
import type { FmmClientPort, FmmFlagResponse, FmmOverrideResponse } from "../../../domain/ports/fmm-client.port.ts";
import type { EventPublisher } from "../../../domain/ports/event-publisher.port.ts";

function baseFlag(overrides: Partial<FmmFlagResponse> = {}): FmmFlagResponse {
  return {
    key: "f1",
    moduleCode: null,
    defaultEnabled: false,
    isGradualRollout: false,
    rolloutPercentage: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function baseOverride(overrides: Partial<FmmOverrideResponse> = {}): FmmOverrideResponse {
  return {
    tenantId: "t1",
    flagKey: "f1",
    enabled: true,
    config: {},
    reason: null,
    expiresAt: null,
    createdBy: null,
    ...overrides,
  };
}

function fakeFmmClient(overrides: Partial<FmmClientPort> = {}): FmmClientPort {
  return {
    listFlags: vi.fn(async () => [baseFlag()]),
    updateFlag: vi.fn(async () => baseFlag({ defaultEnabled: true })),
    setOverride: vi.fn(async () => baseOverride()),
    listOverridesForTenant: vi.fn(async () => [baseOverride()]),
    clearOverride: vi.fn(async () => true),
    ...overrides,
  };
}

function fakeEventPublisher(overrides: Partial<EventPublisher> = {}): EventPublisher {
  return { publish: vi.fn(async () => undefined), ...overrides };
}

describe("FeatureFlagsService", () => {
  describe("listFlags", () => {
    it("returns the flags from the client", async () => {
      const service = new FeatureFlagsService(fakeFmmClient(), fakeEventPublisher());
      const result = await service.listFlags();
      expect(result).toHaveLength(1);
      expect(result[0]!.key).toBe("f1");
    });

    it("throws FmmClientError (not misreported as FlagNotFoundError) when Gen_FMM returns a 404 on list", async () => {
      const fmmClient = fakeFmmClient({ listFlags: vi.fn(async () => { throw new FmmHttpError(404, "route missing"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.listFlags()).rejects.toThrow(FmmClientError);
    });
  });

  describe("updateFlag", () => {
    it("updates the flag, does not forward reason to the client, and publishes sup.flag.updated", async () => {
      const fmmClient = fakeFmmClient();
      const eventPublisher = fakeEventPublisher();
      const service = new FeatureFlagsService(fmmClient, eventPublisher);

      const result = await service.updateFlag("f1", { defaultEnabled: true, reason: "rolling out to everyone" });

      expect(result.defaultEnabled).toBe(true);
      expect(fmmClient.updateFlag).toHaveBeenCalledWith("f1", { defaultEnabled: true });
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit",
        "sup.flag.updated",
        expect.objectContaining({ event_type: "sup.flag.updated", data: expect.objectContaining({ flag_key: "f1", reason: "rolling out to everyone" }) }),
      );
    });

    it("throws FlagNotFoundError when Gen_FMM returns 404", async () => {
      const fmmClient = fakeFmmClient({ updateFlag: vi.fn(async () => { throw new FmmHttpError(404, "not found"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.updateFlag("missing", { defaultEnabled: true, reason: "test" })).rejects.toThrow(FlagNotFoundError);
    });

    it("throws FmmClientError (not an uncaught error) on a 5xx", async () => {
      const fmmClient = fakeFmmClient({ updateFlag: vi.fn(async () => { throw new FmmHttpError(500, "boom"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.updateFlag("f1", { defaultEnabled: true, reason: "test" })).rejects.toThrow(FmmClientError);
    });

    it("does not fail the request when audit publishing fails", async () => {
      const eventPublisher = fakeEventPublisher({ publish: vi.fn(async () => { throw new Error("broker down"); }) });
      const service = new FeatureFlagsService(fakeFmmClient(), eventPublisher);
      await expect(service.updateFlag("f1", { defaultEnabled: true, reason: "test" })).resolves.toBeDefined();
    });
  });

  describe("setOverride", () => {
    it("sets the override and publishes sup.override.set", async () => {
      const fmmClient = fakeFmmClient();
      const eventPublisher = fakeEventPublisher();
      const service = new FeatureFlagsService(fmmClient, eventPublisher);

      const result = await service.setOverride("t1", "f1", { enabled: true, reason: "beta cohort" });

      expect(result.enabled).toBe(true);
      expect(fmmClient.setOverride).toHaveBeenCalledWith(
        expect.objectContaining({ tenantId: "t1", flagKey: "f1", enabled: true, reason: "beta cohort" }),
      );
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit",
        "sup.override.set",
        expect.objectContaining({ tenant_id: "t1", event_type: "sup.override.set" }),
      );
    });

    it("throws FmmClientError (not TenantNotFoundError-style leakage) on a 5xx", async () => {
      const fmmClient = fakeFmmClient({ setOverride: vi.fn(async () => { throw new FmmHttpError(500, "boom"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.setOverride("t1", "f1", { enabled: true, reason: "test" })).rejects.toThrow(FmmClientError);
    });

    it("throws FmmClientError (not FlagNotFoundError/OverrideNotFoundError) when Gen_FMM returns a 404", async () => {
      const fmmClient = fakeFmmClient({ setOverride: vi.fn(async () => { throw new FmmHttpError(404, "not found"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.setOverride("t1", "f1", { enabled: true, reason: "test" })).rejects.toThrow(FmmClientError);
    });

    it("throws FlagNotFoundError when Gen_FMM returns a 422 (flagKey not in its catalog)", async () => {
      const fmmClient = fakeFmmClient({ setOverride: vi.fn(async () => { throw new FmmHttpError(422, "REFERENCED_RECORD_NOT_FOUND"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.setOverride("t1", "f1", { enabled: true, reason: "test" })).rejects.toThrow(FlagNotFoundError);
    });
  });

  describe("listOverridesForTenant", () => {
    it("returns the overrides from the client", async () => {
      const service = new FeatureFlagsService(fakeFmmClient(), fakeEventPublisher());
      const result = await service.listOverridesForTenant("t1");
      expect(result).toHaveLength(1);
    });

    it("throws FmmClientError (not misreported) when Gen_FMM returns a 404", async () => {
      const fmmClient = fakeFmmClient({ listOverridesForTenant: vi.fn(async () => { throw new FmmHttpError(404, "route missing"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.listOverridesForTenant("t1")).rejects.toThrow(FmmClientError);
    });
  });

  describe("clearOverride", () => {
    it("clears the override and publishes sup.override.cleared", async () => {
      const fmmClient = fakeFmmClient();
      const eventPublisher = fakeEventPublisher();
      const service = new FeatureFlagsService(fmmClient, eventPublisher);

      await service.clearOverride("t1", "f1");

      expect(fmmClient.clearOverride).toHaveBeenCalledWith("t1", "f1");
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit",
        "sup.override.cleared",
        expect.objectContaining({ tenant_id: "t1", event_type: "sup.override.cleared" }),
      );
    });

    it("succeeds (idempotent) even when no override existed to clear", async () => {
      const fmmClient = fakeFmmClient({ clearOverride: vi.fn(async () => false) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.clearOverride("t1", "f1")).resolves.toBeUndefined();
    });

    it("throws FmmClientError on a genuine 5xx from Gen_FMM", async () => {
      const fmmClient = fakeFmmClient({ clearOverride: vi.fn(async () => { throw new FmmHttpError(500, "boom"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.clearOverride("t1", "f1")).rejects.toThrow(FmmClientError);
    });

    it("throws FmmClientError (not misreported) when Gen_FMM throws a genuine 404, distinct from its idempotent false-return", async () => {
      const fmmClient = fakeFmmClient({ clearOverride: vi.fn(async () => { throw new FmmHttpError(404, "not found"); }) });
      const service = new FeatureFlagsService(fmmClient, fakeEventPublisher());
      await expect(service.clearOverride("t1", "f1")).rejects.toThrow(FmmClientError);
    });
  });
});

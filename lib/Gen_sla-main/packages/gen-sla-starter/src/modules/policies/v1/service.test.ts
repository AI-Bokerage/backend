import { describe, it, expect, vi } from "vitest";
import { PolicyService } from "./service.ts";
import { SlaPolicyNotFoundError, SlaPolicyConflictError, SlaPolicyHasActiveInstancesError, SlaInvalidTimingError } from "../../../common/errors.ts";
import type { ISlaPolicyRepo, SlaPolicyRecord } from "../../../domain/ports/policy-repo.port.ts";

function basePolicy(overrides: Partial<SlaPolicyRecord> = {}): SlaPolicyRecord {
  return {
    id: "policy-1",
    tenantId: "tenant-1",
    name: "First Response",
    entityType: "TICKET",
    slaType: "FIRST_RESPONSE",
    durationMins: 60,
    warningMins: 45,
    isEnabled: true,
    description: null,
    createdBy: null,
    updatedBy: null,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

function fakeRepo(overrides: Partial<ISlaPolicyRepo> = {}): ISlaPolicyRepo {
  return {
    create: vi.fn(async () => basePolicy()),
    findById: vi.fn(async () => basePolicy()),
    findByEntityAndType: vi.fn(async () => null),
    findAll: vi.fn(async () => ({ data: [basePolicy()], total: 1 })),
    update: vi.fn(async () => ({ count: 1 })),
    countActiveInstances: vi.fn(async () => 0),
    delete: vi.fn(async () => ({ count: 1 })),
    ...overrides,
  };
}

describe("PolicyService", () => {
  it("throws SlaPolicyConflictError when an enabled policy for entityType+slaType already exists", async () => {
    const repo = fakeRepo({ findByEntityAndType: vi.fn(async () => basePolicy()) });
    const service = new PolicyService(repo);
    await expect(
      service.createPolicy("tenant-1", {
        name: "x", entityType: "TICKET", slaType: "FIRST_RESPONSE", durationMins: 60, warningMins: 45, isEnabled: true,
      }),
    ).rejects.toThrow(SlaPolicyConflictError);
  });

  it("creates a policy when no conflict exists", async () => {
    const repo = fakeRepo();
    const service = new PolicyService(repo);
    const result = await service.createPolicy("tenant-1", {
      name: "x", entityType: "TICKET", slaType: "FIRST_RESPONSE", durationMins: 60, warningMins: 45, isEnabled: true,
    });
    expect(repo.create).toHaveBeenCalled();
    expect(result.id).toBe("policy-1");
  });

  it("throws SlaPolicyNotFoundError from getPolicy when missing", async () => {
    const repo = fakeRepo({ findById: vi.fn(async () => null) });
    const service = new PolicyService(repo);
    await expect(service.getPolicy("tenant-1", "missing")).rejects.toThrow(SlaPolicyNotFoundError);
  });

  it("throws SlaPolicyHasActiveInstancesError from deletePolicy when active instances reference it", async () => {
    const repo = fakeRepo({ countActiveInstances: vi.fn(async () => 2) });
    const service = new PolicyService(repo);
    await expect(service.deletePolicy("tenant-1", "policy-1")).rejects.toThrow(SlaPolicyHasActiveInstancesError);
  });

  it("deletePolicy succeeds when there are no active instances", async () => {
    const repo = fakeRepo();
    const service = new PolicyService(repo);
    await expect(service.deletePolicy("tenant-1", "policy-1")).resolves.toBeUndefined();
    expect(repo.delete).toHaveBeenCalledWith("policy-1", "tenant-1");
  });

  it("updatePolicy throws SlaInvalidTimingError when warningMins >= durationMins for a normal (non-zero) duration", async () => {
    const repo = fakeRepo({ findById: vi.fn(async () => basePolicy({ durationMins: 60, warningMins: 45 })) });
    const service = new PolicyService(repo);
    await expect(service.updatePolicy("tenant-1", "policy-1", { warningMins: 60 })).rejects.toThrow(SlaInvalidTimingError);
  });

  it("updatePolicy does NOT throw when durationMins is 0 (fixed-deadline sentinel) even if warningMins is large", async () => {
    const repo = fakeRepo({
      findById: vi.fn(async () => basePolicy({ durationMins: 0, warningMins: 1440 })),
      update: vi.fn(async () => ({ count: 1 })),
    });
    const service = new PolicyService(repo);
    await expect(service.updatePolicy("tenant-1", "policy-1", { warningMins: 2000 })).resolves.toBeDefined();
  });

  it("updatePolicy throws SlaPolicyNotFoundError when the policy doesn't exist", async () => {
    const repo = fakeRepo({ findById: vi.fn(async () => null) });
    const service = new PolicyService(repo);
    await expect(service.updatePolicy("tenant-1", "missing", { name: "x" })).rejects.toThrow(SlaPolicyNotFoundError);
  });
});

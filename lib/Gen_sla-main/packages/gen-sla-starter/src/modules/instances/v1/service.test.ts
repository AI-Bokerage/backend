import { describe, it, expect, vi } from "vitest";
import { InstanceService } from "./service.ts";
import { SlaInstanceNotFoundError } from "../../../common/errors.ts";
import type { ISlaInstanceRepo, SlaInstanceRecord } from "../../../domain/ports/instance-repo.port.ts";
import type { ISlaPolicyRepo, SlaPolicyRecord } from "../../../domain/ports/policy-repo.port.ts";
import type { IOutboxWriter } from "../../../domain/ports/outbox-writer.port.ts";

function baseInstance(overrides: Partial<SlaInstanceRecord> = {}): SlaInstanceRecord {
  return {
    id: "inst-1", tenantId: "tenant-1", policyId: "policy-1", entityType: "TICKET",
    entityId: "entity-1", slaType: "FIRST_RESPONSE", status: "ACTIVE",
    startedAt: new Date(), dueAt: new Date(Date.now() + 3_600_000), warningAt: new Date(Date.now() + 2_700_000),
    breachedAt: null, resolvedAt: null, metadata: {},
    ...overrides,
  };
}

function basePolicy(overrides: Partial<SlaPolicyRecord> = {}): SlaPolicyRecord {
  return {
    id: "policy-1", tenantId: "tenant-1", name: "First Response", entityType: "TICKET",
    slaType: "FIRST_RESPONSE", durationMins: 60, warningMins: 45, isEnabled: true,
    description: null, createdBy: null, updatedBy: null, createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

function fakeInstanceRepo(overrides: Partial<ISlaInstanceRepo> = {}): ISlaInstanceRepo {
  return {
    create: vi.fn(async () => baseInstance()),
    findById: vi.fn(async () => baseInstance()),
    findByEntityAndType: vi.fn(async () => null),
    findAll: vi.fn(async () => ({ data: [baseInstance()], total: 1 })),
    updateStatus: vi.fn(async (_id, _t, status) => baseInstance({ status })),
    appendHistory: vi.fn(async () => undefined),
    findActiveInstancesDue: vi.fn(async () => []),
    findActiveInstancesNearWarning: vi.fn(async () => []),
    cancelByEntityId: vi.fn(async () => ({ count: 1 })),
    resolveByEntityId: vi.fn(async () => ({ count: 1 })),
    findActiveByEntityId: vi.fn(async () => []),
    createEscalation: vi.fn(async () => undefined),
    ...overrides,
  };
}

function fakePolicyRepo(overrides: Partial<ISlaPolicyRepo> = {}): ISlaPolicyRepo {
  return {
    create: vi.fn(),
    findById: vi.fn(),
    findByEntityAndType: vi.fn(async () => basePolicy()),
    findAll: vi.fn(),
    update: vi.fn(),
    countActiveInstances: vi.fn(),
    delete: vi.fn(),
    ...overrides,
  };
}

function fakeOutbox(): IOutboxWriter {
  return { enqueue: vi.fn(async () => undefined) };
}

describe("InstanceService", () => {
  it("getInstance throws SlaInstanceNotFoundError when missing", async () => {
    const service = new InstanceService(fakeInstanceRepo({ findById: vi.fn(async () => null) }), fakePolicyRepo(), fakeOutbox());
    await expect(service.getInstance("tenant-1", "missing")).rejects.toThrow(SlaInstanceNotFoundError);
  });

  it("createInstance skips creating a duplicate when an ACTIVE/WARNING instance already exists", async () => {
    const instanceRepo = fakeInstanceRepo({ findByEntityAndType: vi.fn(async () => baseInstance()) });
    const outbox = fakeOutbox();
    const service = new InstanceService(instanceRepo, fakePolicyRepo(), outbox);

    await service.createInstance({
      tenantId: "tenant-1", policyId: "policy-1", entityType: "TICKET", entityId: "entity-1",
      slaType: "FIRST_RESPONSE", startedAt: new Date(), dueAt: new Date(), warningAt: new Date(), metadata: {},
    });

    expect(instanceRepo.create).not.toHaveBeenCalled();
    expect(outbox.enqueue).not.toHaveBeenCalled();
  });

  it("createInstance returns the existing record without creating a new one when a RESOLVED instance already exists (DB unique constraint is unconditional)", async () => {
    const instanceRepo = fakeInstanceRepo({ findByEntityAndType: vi.fn(async () => baseInstance({ status: "RESOLVED" })) });
    const outbox = fakeOutbox();
    const service = new InstanceService(instanceRepo, fakePolicyRepo(), outbox);

    const result = await service.createInstance({
      tenantId: "tenant-1", policyId: "policy-1", entityType: "TICKET", entityId: "entity-1",
      slaType: "FIRST_RESPONSE", startedAt: new Date(), dueAt: new Date(), warningAt: new Date(), metadata: {},
    });

    expect(result.status).toBe("RESOLVED");
    expect(instanceRepo.create).not.toHaveBeenCalled();
    expect(outbox.enqueue).not.toHaveBeenCalled();
  });

  it("createFromPolicy returns null when no enabled policy exists for entityType+slaType", async () => {
    const policyRepo = fakePolicyRepo({ findByEntityAndType: vi.fn(async () => null) });
    const service = new InstanceService(fakeInstanceRepo(), policyRepo, fakeOutbox());
    const result = await service.createFromPolicy("tenant-1", "TICKET", "entity-1", "FIRST_RESPONSE", {}, new Date());
    expect(result).toBeNull();
  });

  it("transitionToWarning is a no-op when the instance isn't ACTIVE", async () => {
    const instanceRepo = fakeInstanceRepo({ findById: vi.fn(async () => baseInstance({ status: "BREACHED" })) });
    const service = new InstanceService(instanceRepo, fakePolicyRepo(), fakeOutbox());
    await service.transitionToWarning("inst-1", "tenant-1");
    expect(instanceRepo.updateStatus).not.toHaveBeenCalled();
  });

  it("transitionToWarning moves ACTIVE to WARNING and enqueues an outbox event", async () => {
    const instanceRepo = fakeInstanceRepo();
    const outbox = fakeOutbox();
    const service = new InstanceService(instanceRepo, fakePolicyRepo(), outbox);
    await service.transitionToWarning("inst-1", "tenant-1");
    expect(instanceRepo.updateStatus).toHaveBeenCalledWith("inst-1", "tenant-1", "WARNING");
    expect(outbox.enqueue).toHaveBeenCalledTimes(1);
  });

  it("transitionToBreached moves ACTIVE/WARNING to BREACHED with breachedAt and enqueues an outbox event", async () => {
    const instanceRepo = fakeInstanceRepo();
    const outbox = fakeOutbox();
    const service = new InstanceService(instanceRepo, fakePolicyRepo(), outbox);
    await service.transitionToBreached("inst-1", "tenant-1");
    expect(instanceRepo.updateStatus).toHaveBeenCalledWith("inst-1", "tenant-1", "BREACHED", { breachedAt: expect.any(Date) });
    expect(outbox.enqueue).toHaveBeenCalledTimes(1);
  });
});

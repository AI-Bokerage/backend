import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { startSlaTimerWorker } from "./sla-timer.worker.ts";
import type { ISlaInstanceRepo, SlaInstanceRecord } from "../domain/ports/instance-repo.port.ts";
import type { InstanceService } from "../modules/instances/v1/service.ts";
import type { IDistributedLock } from "../domain/ports/distributed-lock.port.ts";

function instanceStub(id: string): SlaInstanceRecord {
  return {
    id, tenantId: "tenant-1", policyId: "policy-1", entityType: "TICKET", entityId: "entity-1",
    slaType: "FIRST_RESPONSE", status: "ACTIVE", startedAt: new Date(), dueAt: new Date(), warningAt: new Date(),
    breachedAt: null, resolvedAt: null, metadata: {},
  };
}

describe("startSlaTimerWorker", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("acquires a lock per due/near-warning instance and calls the matching transition", async () => {
    const instanceRepo: ISlaInstanceRepo = {
      findActiveInstancesNearWarning: vi.fn(async () => [instanceStub("warn-1")]),
      findActiveInstancesDue: vi.fn(async () => [instanceStub("due-1")]),
    } as any;
    const instanceService: Pick<InstanceService, "transitionToWarning" | "transitionToBreached"> = {
      transitionToWarning: vi.fn(async () => undefined),
      transitionToBreached: vi.fn(async () => undefined),
    };
    const lock: IDistributedLock = { acquire: vi.fn(async () => true) };

    const worker = startSlaTimerWorker({ instanceRepo, instanceService, lock, pollIntervalMs: 30_000, batchSize: 100, lockTtlS: 60 });
    await vi.runOnlyPendingTimersAsync();

    expect(instanceService.transitionToWarning).toHaveBeenCalledWith("warn-1", "tenant-1");
    expect(instanceService.transitionToBreached).toHaveBeenCalledWith("due-1", "tenant-1");
    worker.close();
  });

  it("skips an instance when the lock is already held", async () => {
    const instanceRepo: ISlaInstanceRepo = {
      findActiveInstancesNearWarning: vi.fn(async () => [instanceStub("warn-1")]),
      findActiveInstancesDue: vi.fn(async () => []),
    } as any;
    const instanceService: Pick<InstanceService, "transitionToWarning" | "transitionToBreached"> = {
      transitionToWarning: vi.fn(async () => undefined),
      transitionToBreached: vi.fn(async () => undefined),
    };
    const lock: IDistributedLock = { acquire: vi.fn(async () => false) };

    const worker = startSlaTimerWorker({ instanceRepo, instanceService, lock, pollIntervalMs: 30_000, batchSize: 100, lockTtlS: 60 });
    await vi.runOnlyPendingTimersAsync();

    expect(instanceService.transitionToWarning).not.toHaveBeenCalled();
    worker.close();
  });

  it("continues processing remaining instances when one instance's transition throws", async () => {
    const instanceRepo: ISlaInstanceRepo = {
      findActiveInstancesNearWarning: vi.fn(async () => [instanceStub("warn-1"), instanceStub("warn-2")]),
      findActiveInstancesDue: vi.fn(async () => []),
    } as any;
    const transitionToWarning = vi.fn(async (id: string) => {
      if (id === "warn-1") throw new Error("transition failed");
    });
    const instanceService: Pick<InstanceService, "transitionToWarning" | "transitionToBreached"> = {
      transitionToWarning,
      transitionToBreached: vi.fn(async () => undefined),
    };
    const lock: IDistributedLock = { acquire: vi.fn(async () => true) };

    const worker = startSlaTimerWorker({ instanceRepo, instanceService, lock, pollIntervalMs: 30_000, batchSize: 100, lockTtlS: 60 });
    await vi.runOnlyPendingTimersAsync();

    expect(transitionToWarning).toHaveBeenCalledWith("warn-1", "tenant-1");
    expect(transitionToWarning).toHaveBeenCalledWith("warn-2", "tenant-1");
    worker.close();
  });
});

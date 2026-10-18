import { describe, it, expect, vi, afterEach } from "vitest";
import { ReconciliationService } from "./service.ts";
import { registerMeter, _clearRegistryForTests } from "../../meters/v1/registry.ts";
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IMeterRepo } from "../../../domain/ports/meter.repository.port.ts";
import type { IReconciliationRepo } from "../../../domain/ports/reconciliation.repository.port.ts";

describe("ReconciliationService", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("no drift when counter matches the snapshot baseline", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn().mockResolvedValue(10),
      mget: vi.fn(), setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn(), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = {
      insertSnapshot: vi.fn(), findTrend: vi.fn(),
      latestSnapshot: vi.fn().mockResolvedValue({ id: "s1", tenantId: "t1", snapshotAt: new Date(), period: "daily", metrics: { seats: 10 }, createdAt: new Date() }),
    };
    const reconciliationRepo: IReconciliationRepo = { insertLog: vi.fn() };
    const service = new ReconciliationService(counterStore, meterRepo, reconciliationRepo, 2);
    const result = await service.runSweep(["t1"]);
    expect(result.driftsCorrected).toBe(0);
    expect(reconciliationRepo.insertLog).toHaveBeenCalledWith(expect.objectContaining({ corrected: false, driftPct: 0 }));
  });

  it("corrects the counter when drift exceeds the threshold and the baseline is non-zero", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn().mockResolvedValue(50),
      mget: vi.fn(), setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn(), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = {
      insertSnapshot: vi.fn(), findTrend: vi.fn(),
      latestSnapshot: vi.fn().mockResolvedValue({ id: "s1", tenantId: "t1", snapshotAt: new Date(), period: "daily", metrics: { seats: 10 }, createdAt: new Date() }),
    };
    const reconciliationRepo: IReconciliationRepo = { insertLog: vi.fn() };
    const service = new ReconciliationService(counterStore, meterRepo, reconciliationRepo, 2);
    const result = await service.runSweep(["t1"]);
    expect(result.driftsCorrected).toBe(1);
    expect(counterStore.setBatch).toHaveBeenCalledWith([{ key: "genusg:t1:seats", value: "10", ttlSec: 315360000 }]);
  });

  it("a zero baseline never triggers a correction", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn().mockResolvedValue(50),
      mget: vi.fn(), setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn(), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = { insertSnapshot: vi.fn(), findTrend: vi.fn(), latestSnapshot: vi.fn().mockResolvedValue(null) };
    const reconciliationRepo: IReconciliationRepo = { insertLog: vi.fn() };
    const service = new ReconciliationService(counterStore, meterRepo, reconciliationRepo, 2);
    const result = await service.runSweep(["t1"]);
    expect(result.driftsCorrected).toBe(0);
    expect(counterStore.setBatch).not.toHaveBeenCalled();
  });

  it("one tenant's failure never blocks the rest", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn().mockRejectedValueOnce(new Error("redis down")).mockResolvedValueOnce(5),
      mget: vi.fn(), setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn(), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = {
      insertSnapshot: vi.fn(), findTrend: vi.fn(),
      latestSnapshot: vi.fn().mockResolvedValue({ id: "s1", tenantId: "t2", snapshotAt: new Date(), period: "daily", metrics: { seats: 5 }, createdAt: new Date() }),
    };
    const reconciliationRepo: IReconciliationRepo = { insertLog: vi.fn() };
    const service = new ReconciliationService(counterStore, meterRepo, reconciliationRepo, 2);
    const result = await service.runSweep(["bad-tenant", "t2"]);
    expect(result.tenantsProcessed).toBe(2);
    expect(reconciliationRepo.insertLog).toHaveBeenCalledTimes(1);
  });

  it("a resource-mode meter's drift is logged uncorrected — no setBatch, corrected:false", async () => {
    registerMeter("storage_gb", { unit: "gb", mode: "resource" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn(),
      mget: vi.fn(), setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(),
      zsumScores: vi.fn().mockResolvedValue(50), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = {
      insertSnapshot: vi.fn(), findTrend: vi.fn(),
      latestSnapshot: vi.fn().mockResolvedValue({ id: "s1", tenantId: "t1", snapshotAt: new Date(), period: "daily", metrics: { storage_gb: 10 }, createdAt: new Date() }),
    };
    const reconciliationRepo: IReconciliationRepo = { insertLog: vi.fn() };
    const service = new ReconciliationService(counterStore, meterRepo, reconciliationRepo, 2);
    const result = await service.runSweep(["t1"]);
    expect(result.driftsCorrected).toBe(0);
    expect(counterStore.setBatch).not.toHaveBeenCalled();
    expect(reconciliationRepo.insertLog).toHaveBeenCalledWith(
      expect.objectContaining({ metric: "storage_gb", corrected: false, counterValue: 50n, dbValue: 10n }),
    );
  });
});

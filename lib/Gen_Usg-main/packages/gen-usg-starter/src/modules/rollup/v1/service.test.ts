import { describe, it, expect, vi, afterEach } from "vitest";
import { RollupService } from "./service.ts";
import { registerMeter, _clearRegistryForTests } from "../../meters/v1/registry.ts";
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IMeterRepo } from "../../../domain/ports/meter.repository.port.ts";

describe("RollupService", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("snapshots every tenant's registered-metric counters", async () => {
    registerMeter("seats", { unit: "seat" });
    registerMeter("api_calls", { unit: "call" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn().mockResolvedValueOnce(3).mockResolvedValueOnce(40).mockResolvedValueOnce(3).mockResolvedValueOnce(40),
      mget: vi.fn(),
      setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn(), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = { insertSnapshot: vi.fn(), findTrend: vi.fn(), latestSnapshot: vi.fn() };
    const service = new RollupService(counterStore, meterRepo);
    const result = await service.runRollup(["t1", "t2"], "daily");
    expect(result).toEqual({ period: "daily", tenantsProcessed: 2, failures: 0 });
    expect(meterRepo.insertSnapshot).toHaveBeenCalledTimes(2);
    expect(meterRepo.insertSnapshot).toHaveBeenCalledWith("t1", expect.any(Date), "daily", { seats: 3, api_calls: 40 });
  });

  it("one tenant's failure never blocks the rest", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn().mockResolvedValue(1),
      mget: vi.fn(),
      setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn(), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = {
      insertSnapshot: vi.fn().mockRejectedValueOnce(new Error("db down")).mockResolvedValueOnce(undefined),
      findTrend: vi.fn(), latestSnapshot: vi.fn(),
    };
    const service = new RollupService(counterStore, meterRepo);
    const result = await service.runRollup(["bad-tenant", "good-tenant"], "monthly");
    expect(result).toEqual({ period: "monthly", tenantsProcessed: 2, failures: 1 });
  });

  it("routes mode:resource metrics through the sorted-set sum, not get()", async () => {
    registerMeter("seats", { unit: "seat" });
    registerMeter("storage_gb", { unit: "gb", mode: "resource" });
    const counterStore: ICounterStore = {
      incrBy: vi.fn(), get: vi.fn().mockResolvedValue(3),
      mget: vi.fn(),
      setNX: vi.fn(), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(), zsumScores: vi.fn().mockResolvedValue(77), setBatch: vi.fn(),
    };
    const meterRepo: IMeterRepo = { insertSnapshot: vi.fn(), findTrend: vi.fn(), latestSnapshot: vi.fn() };
    const service = new RollupService(counterStore, meterRepo);
    await service.runRollup(["t1"], "daily");
    expect(counterStore.zsumScores).toHaveBeenCalledWith("genusg:resource:t1:storage_gb");
    expect(meterRepo.insertSnapshot).toHaveBeenCalledWith("t1", expect.any(Date), "daily", { seats: 3, storage_gb: 77 });
  });
});

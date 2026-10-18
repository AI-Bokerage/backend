import { describe, it, expect, vi, afterEach } from "vitest";
import { CheckService } from "./service.ts";
import { GraceOverageService } from "../../grace-overage/v1/service.ts";
import { registerMeter, _clearRegistryForTests } from "../../meters/v1/registry.ts";
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { ILimitProvider } from "../../../domain/ports/limit-provider.port.ts";
import type { IGraceOverageRepo } from "../../../domain/ports/grace-overage.repository.port.ts";
import { MeterNotRegisteredError } from "../../../common/errors.ts";

const OPTS = { limitCacheTtlSec: 300, limitLockTtlSec: 5, softWarnPct80: 0.8, softWarnPct95: 0.95, graceWindowDays: 7 };

function makeCounterStore(overrides: Partial<ICounterStore> = {}): ICounterStore {
  return {
    incrBy: vi.fn(), get: vi.fn().mockResolvedValue(0), mget: vi.fn().mockResolvedValue([null]),
    setNX: vi.fn().mockResolvedValue(true), del: vi.fn(), zadd: vi.fn(), zrem: vi.fn(),
    zsumScores: vi.fn(), setBatch: vi.fn(), ...overrides,
  };
}

function makeGraceRepo(overrides: Partial<IGraceOverageRepo> = {}): IGraceOverageRepo {
  return {
    findOpen: vi.fn().mockResolvedValue(null), open: vi.fn(), bump: vi.fn(),
    listOpenForTenant: vi.fn(), listExpiredOpen: vi.fn(), close: vi.fn(), ...overrides,
  };
}

describe("CheckService", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("throws MeterNotRegisteredError for an unregistered metric", async () => {
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(makeCounterStore(), limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    await expect(service.check("t1", "nope")).rejects.toThrow(MeterNotRegisteredError);
  });

  it("ALLOW when unlimited (-1)", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(5), mget: vi.fn().mockResolvedValue([-1]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "seats");
    expect(result).toEqual({ allowed: true, outcome: "ALLOW", metric: "seats", current: 5, limit: -1, pct: 0 });
  });

  it("SOFT_WARN_80 between 80% and 95%", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(84), mget: vi.fn().mockResolvedValue([100]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "seats");
    expect(result.outcome).toBe("SOFT_WARN_80");
    expect(result.allowed).toBe(true);
  });

  it("SOFT_WARN_95 wins over 80 when both thresholds are crossed", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(96), mget: vi.fn().mockResolvedValue([100]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "seats");
    expect(result.outcome).toBe("SOFT_WARN_95");
  });

  it("BLOCK when over limit and not grace-eligible", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(100), mget: vi.fn().mockResolvedValue([100]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "seats", 5);
    expect(result).toMatchObject({ allowed: false, outcome: "BLOCK", code: "USAGE_QUOTA_EXCEEDED" });
  });

  it("GRACE when over limit and grace-eligible", async () => {
    registerMeter("active_users", { unit: "user", graceEligible: true });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(100), mget: vi.fn().mockResolvedValue([100]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const graceRepo = makeGraceRepo({
      open: vi.fn().mockResolvedValue({
        id: "g1", tenantId: "t1", metric: "active_users", graceStartedAt: new Date(),
        graceExpiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), overageCount: 1,
        status: "OPEN", billedAt: null, createdAt: new Date(), updatedAt: new Date(),
      }),
    });
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(graceRepo), OPTS);
    const result = await service.check("t1", "active_users", 1);
    expect(result.outcome).toBe("GRACE");
    expect(result.allowed).toBe(true);
    expect(result.grace).toBeDefined();
  });

  it("stampede lock: a losing caller fails open to Infinity if the cache is still empty after the wait", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({
      get: vi.fn().mockResolvedValue(1),
      mget: vi.fn().mockResolvedValue([null]),
      setNX: vi.fn().mockResolvedValue(false),
    });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "seats");
    expect(result.outcome).toBe("ALLOW");
    expect(result.limit).toBe(Infinity);
  });

  it("fails open to Infinity when ILimitProvider throws", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(1), mget: vi.fn().mockResolvedValue([null]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn().mockRejectedValue(new Error("provider down")) };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "seats");
    expect(result.outcome).toBe("ALLOW");
    expect(result.limit).toBe(Infinity);
  });

  it("seeds every returned metric's limit in one setBatch call on a cache miss", async () => {
    registerMeter("seats", { unit: "seat" });
    const counterStore = makeCounterStore({ get: vi.fn().mockResolvedValue(1), mget: vi.fn().mockResolvedValue([null]) });
    const limitProvider: ILimitProvider = { getLimits: vi.fn().mockResolvedValue({ seats: 10, api_calls: 1000 }) };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    await service.check("t1", "seats");
    expect(counterStore.setBatch).toHaveBeenCalledWith([
      { key: "genusg:limit:t1:seats", value: "10", ttlSec: 300 },
      { key: "genusg:limit:t1:api_calls", value: "1000", ttlSec: 300 },
    ]);
  });

  it("resource-mode meters read current usage from zsumScores, not get", async () => {
    registerMeter("storage_gb", { unit: "gb", mode: "resource" });
    const counterStore = makeCounterStore({
      get: vi.fn().mockResolvedValue(0),
      zsumScores: vi.fn().mockResolvedValue(42),
      mget: vi.fn().mockResolvedValue([100]),
    });
    const limitProvider: ILimitProvider = { getLimits: vi.fn() };
    const service = new CheckService(counterStore, limitProvider, new GraceOverageService(makeGraceRepo()), OPTS);
    const result = await service.check("t1", "storage_gb", 0);
    expect(result.current).toBe(42);
    expect(counterStore.zsumScores).toHaveBeenCalledWith("genusg:resource:t1:storage_gb");
    expect(counterStore.get).not.toHaveBeenCalled();
  });
});

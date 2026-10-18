import { describe, it, expect, afterEach } from "vitest";
import request from "supertest";
import { createGenUsg } from "../../src/create-gen-usg.ts";
import { registerMeter, _clearRegistryForTests } from "../../src/modules/meters/v1/registry.ts";
import type { ICounterStore } from "../../src/domain/ports/counter-store.port.ts";
import type { IMeterRepo } from "../../src/domain/ports/meter.repository.port.ts";
import type { IGraceOverageRepo } from "../../src/domain/ports/grace-overage.repository.port.ts";
import type { IReconciliationRepo } from "../../src/domain/ports/reconciliation.repository.port.ts";
import type { IIdempotencyRepo } from "../../src/domain/ports/idempotency.repository.port.ts";
import type { ILimitProvider } from "../../src/domain/ports/limit-provider.port.ts";

const noopCounterStore: ICounterStore = {
  incrBy: async () => 1, get: async () => 0, mget: async (keys) => keys.map(() => -1),
  setNX: async () => true, del: async () => {}, zadd: async () => {}, zrem: async () => {},
  zsumScores: async () => 0, setBatch: async () => {},
};
const noopMeterRepo: IMeterRepo = { insertSnapshot: async () => {}, findTrend: async () => [], latestSnapshot: async () => null };
const noopGraceOverageRepo: IGraceOverageRepo = {
  findOpen: async () => null, open: async () => { throw new Error("not used"); }, bump: async () => { throw new Error("not used"); },
  listOpenForTenant: async () => [], listExpiredOpen: async () => [], close: async () => {},
};
const noopReconciliationRepo: IReconciliationRepo = { insertLog: async () => {} };
const noopIdempotencyRepo: IIdempotencyRepo = { exists: async () => false, insert: async () => {} };
const noopLimitProvider: ILimitProvider = { getLimits: async () => ({}) };

const fullOverrides = {
  counterStore: noopCounterStore, meterRepo: noopMeterRepo, graceOverageRepo: noopGraceOverageRepo,
  reconciliationRepo: noopReconciliationRepo, idempotencyRepo: noopIdempotencyRepo, limitProvider: noopLimitProvider,
  internalSecret: "test-secret",
};

describe("createGenUsg", () => {
  afterEach(() => { _clearRegistryForTests(); delete process.env.DATABASE_URL; delete process.env.REDIS_URL; });

  it("boots cleanly with every port overridden and no env set", () => {
    expect(() => createGenUsg(fullOverrides)).not.toThrow();
  });

  it("throws when check/summary are enabled but no limitProvider is supplied", () => {
    const { limitProvider, ...rest } = fullOverrides;
    expect(() => createGenUsg(rest)).toThrow(/limitProvider is required/);
  });

  it("does not require limitProvider when check and summary are both disabled", () => {
    const { limitProvider, ...rest } = fullOverrides;
    expect(() => createGenUsg({ ...rest, modules: { check: false, summary: false } })).not.toThrow();
  });

  it("GET /health returns ok without any auth", async () => {
    const instance = createGenUsg(fullOverrides);
    const res = await request(instance.app).get("/health");
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: "ok" });
  });

  it("a disabled module's routes genuinely 404", async () => {
    const instance = createGenUsg({ ...fullOverrides, modules: { increment: false } });
    const res = await request(instance.app).post("/internal/usage/increment").set("x-internal-secret", "test-secret").send({});
    expect(res.status).toBe(404);
  });

  it("POST /internal/usage/increment without the internal secret is 401", async () => {
    const instance = createGenUsg(fullOverrides);
    const res = await request(instance.app).post("/internal/usage/increment").send({});
    expect(res.status).toBe(401);
  });

  it("increment()/check() work as direct in-process calls, not just HTTP", async () => {
    registerMeter("seats", { unit: "seat" });
    const instance = createGenUsg(fullOverrides);
    const incResult = await instance.increment({ tenantId: "11111111-1111-1111-1111-111111111111", metric: "seats", delta: 1 });
    expect(incResult.metric).toBe("seats");
    const checkResult = await instance.check("11111111-1111-1111-1111-111111111111", "seats");
    expect(checkResult.outcome).toBe("ALLOW");
  });

  it("runDailyRollup/runReconciliationSweep/closeExpiredGraceWindows are host-triggered functions", async () => {
    registerMeter("seats", { unit: "seat" });
    const instance = createGenUsg(fullOverrides);
    const rollup = await instance.runDailyRollup(["11111111-1111-1111-1111-111111111111"]);
    expect(rollup).toEqual({ period: "daily", tenantsProcessed: 1, failures: 0 });
    const sweep = await instance.runReconciliationSweep(["11111111-1111-1111-1111-111111111111"]);
    expect(sweep.tenantsProcessed).toBe(1);
    expect(await instance.closeExpiredGraceWindows(["11111111-1111-1111-1111-111111111111"])).toEqual([]);
  });
});

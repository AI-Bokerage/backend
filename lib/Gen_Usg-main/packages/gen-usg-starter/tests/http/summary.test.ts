import { describe, it, expect, afterEach } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { SummaryController } from "../../src/modules/summary/v1/controller.ts";
import { summaryRoutes } from "../../src/modules/summary/v1/routes.ts";
import { SummaryService } from "../../src/modules/summary/v1/service.ts";
import { CheckService } from "../../src/modules/check/v1/service.ts";
import { GraceOverageService } from "../../src/modules/grace-overage/v1/service.ts";
import { registerMeter, _clearRegistryForTests } from "../../src/modules/meters/v1/registry.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import type { ICounterStore } from "../../src/domain/ports/counter-store.port.ts";
import type { IMeterRepo } from "../../src/domain/ports/meter.repository.port.ts";
import type { IGraceOverageRepo } from "../../src/domain/ports/grace-overage.repository.port.ts";
import type { ILimitProvider } from "../../src/domain/ports/limit-provider.port.ts";

function makeApp() {
  // SummaryService reads each metric's current value via readCurrentValue,
  // which for a "counter"-mode meter (the default) calls counterStore.get on
  // the counter key ("genusg:{tenantId}:{metric}") — hence get() returning 7
  // below. CheckService.resolveLimits separately calls counterStore.mget on
  // limit keys ("genusg:limit:...") to check its cache; returning null for
  // every key forces a cache miss that falls back to ILimitProvider.
  const counterStore: ICounterStore = {
    incrBy: async () => 1, get: async () => 7,
    mget: async (keys: string[]) => keys.map(() => null),
    setNX: async () => true, del: async () => {}, zadd: async () => {}, zrem: async () => {},
    zsumScores: async () => 0, setBatch: async () => {},
  };
  const meterRepo: IMeterRepo = { insertSnapshot: async () => {}, findTrend: async () => [], latestSnapshot: async () => null };
  const graceOverageRepo: IGraceOverageRepo = {
    findOpen: async () => null, open: async () => { throw new Error("not used"); }, bump: async () => { throw new Error("not used"); },
    listOpenForTenant: async () => [], listExpiredOpen: async () => [], close: async () => {},
  };
  const limitProvider: ILimitProvider = { getLimits: async () => ({ seats: 10 }) };
  const checkService = new CheckService(counterStore, limitProvider, new GraceOverageService(graceOverageRepo), {
    limitCacheTtlSec: 300, limitLockTtlSec: 5, softWarnPct80: 0.8, softWarnPct95: 0.95, graceWindowDays: 7,
  });
  const summaryService = new SummaryService(counterStore, meterRepo, graceOverageRepo, checkService, 7);
  const app = express();
  app.use("/api/v1/usage/summary", summaryRoutes(new SummaryController(summaryService)));
  app.use(errorHandler);
  return app;
}

describe("GET /api/v1/usage/summary", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("returns meters/trend/graceOverages for a trusted tenantId, no auth required", async () => {
    registerMeter("seats", { unit: "seat" });
    const res = await request(makeApp()).get("/api/v1/usage/summary").query({ tenantId: "11111111-1111-1111-1111-111111111111" });
    expect(res.status).toBe(200);
    expect(res.body.meters).toEqual([{ metric: "seats", unit: "seat", current: 7, limit: 10, pct: 70 }]);
    expect(res.body.trend).toEqual([]);
    expect(res.body.graceOverages).toEqual([]);
  });

  it("400s on a non-UUID tenantId", async () => {
    const res = await request(makeApp()).get("/api/v1/usage/summary").query({ tenantId: "not-a-uuid" });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });
});

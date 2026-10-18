import { describe, it, expect, afterEach } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { IncrementController } from "../../src/modules/increment/v1/controller.ts";
import { incrementRoutes } from "../../src/modules/increment/v1/routes.ts";
import { IncrementService } from "../../src/modules/increment/v1/service.ts";
import { CheckController } from "../../src/modules/check/v1/controller.ts";
import { checkRoutes } from "../../src/modules/check/v1/routes.ts";
import { CheckService } from "../../src/modules/check/v1/service.ts";
import { GraceOverageService } from "../../src/modules/grace-overage/v1/service.ts";
import { registerMeter, _clearRegistryForTests } from "../../src/modules/meters/v1/registry.ts";
import { internalSecretMiddleware } from "../../src/middleware/internal-secret.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import type { ICounterStore } from "../../src/domain/ports/counter-store.port.ts";
import type { IIdempotencyRepo } from "../../src/domain/ports/idempotency.repository.port.ts";
import type { ILimitProvider } from "../../src/domain/ports/limit-provider.port.ts";
import type { IGraceOverageRepo } from "../../src/domain/ports/grace-overage.repository.port.ts";

function makeApp() {
  const counterStore: ICounterStore = {
    incrBy: async () => 1, get: async () => 0, mget: async (keys) => keys.map(() => -1),
    setNX: async () => true, del: async () => {}, zadd: async () => {}, zrem: async () => {},
    zsumScores: async () => 0, setBatch: async () => {},
  };
  const idempotencyRepo: IIdempotencyRepo = { exists: async () => false, insert: async () => {} };
  const limitProvider: ILimitProvider = { getLimits: async () => ({}) };
  const graceRepo: IGraceOverageRepo = {
    findOpen: async () => null, open: async () => { throw new Error("not used"); }, bump: async () => { throw new Error("not used"); },
    listOpenForTenant: async () => [], listExpiredOpen: async () => [], close: async () => {},
  };
  const incrementService = new IncrementService(counterStore, idempotencyRepo, { backdateDays: 30, dedupTtlSec: 86400 });
  const checkService = new CheckService(counterStore, limitProvider, new GraceOverageService(graceRepo), {
    limitCacheTtlSec: 300, limitLockTtlSec: 5, softWarnPct80: 0.8, softWarnPct95: 0.95, graceWindowDays: 7,
  });
  const app = express();
  app.use(express.json());
  const gate = internalSecretMiddleware("test-secret");
  app.use("/internal/usage/increment", gate, incrementRoutes(new IncrementController(incrementService)));
  app.use("/internal/usage/check", gate, checkRoutes(new CheckController(checkService)));
  app.use(errorHandler);
  return app;
}

describe("increment & check HTTP routes", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("POST /internal/usage/increment without the internal secret is 401", async () => {
    const res = await request(makeApp()).post("/internal/usage/increment").send({ tenantId: "11111111-1111-1111-1111-111111111111", metric: "seats", delta: 1 });
    expect(res.status).toBe(401);
  });

  it("POST /internal/usage/increment with the internal secret succeeds", async () => {
    registerMeter("seats", { unit: "seat" });
    const res = await request(makeApp())
      .post("/internal/usage/increment")
      .set("x-internal-secret", "test-secret")
      .send({ tenantId: "11111111-1111-1111-1111-111111111111", metric: "seats", delta: 1 });
    expect(res.status).toBe(200);
    expect(res.body.metric).toBe("seats");
  });

  it("POST /internal/usage/increment for an unregistered metric is 400", async () => {
    const res = await request(makeApp())
      .post("/internal/usage/increment")
      .set("x-internal-secret", "test-secret")
      .send({ tenantId: "11111111-1111-1111-1111-111111111111", metric: "nope", delta: 1 });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("METER_NOT_REGISTERED");
  });

  it("POST /internal/usage/check always returns 200 with the verdict in the body", async () => {
    registerMeter("seats", { unit: "seat" });
    const res = await request(makeApp())
      .post("/internal/usage/check")
      .set("x-internal-secret", "test-secret")
      .send({ tenantId: "11111111-1111-1111-1111-111111111111", metric: "seats" });
    expect(res.status).toBe(200);
    expect(res.body.outcome).toBe("ALLOW");
  });
});

import { describe, it, expect, vi } from "vitest";
import request from "supertest";
import express from "express";
import { Prisma } from "@prisma/client";
import { createGenFmm } from "../../src/create-gen-fmm.ts";
import type { ICatalogRepo, FeatureFlagRecord } from "../../src/domain/ports/catalog.repository.port.ts";
import type { ITenantOverrideRepo } from "../../src/domain/ports/tenant-override.repository.port.ts";
import type { ITelemetryRepo } from "../../src/domain/ports/telemetry.repository.port.ts";
import type { ICacheStore } from "../../src/domain/ports/cache-store.port.ts";

function fakeCatalogRepo(overrides: Partial<ICatalogRepo> = {}): ICatalogRepo {
  return {
    createModule: vi.fn(), findModuleByCode: vi.fn(async () => null), listModules: vi.fn(async () => []), updateModule: vi.fn(),
    createFlag: vi.fn(), findFlagByKey: vi.fn(async () => null), listFlags: vi.fn(async () => []), updateFlag: vi.fn(), deleteFlag: vi.fn(),
    upsertPlanModule: vi.fn(), listPlanModules: vi.fn(async () => []), deletePlanModule: vi.fn(),
    ...overrides,
  };
}
function fakeOverrideRepo(overrides: Partial<ITenantOverrideRepo> = {}): ITenantOverrideRepo {
  return {
    upsert: vi.fn(), findOne: vi.fn(async () => null), findAllForTenant: vi.fn(async () => []), listAll: vi.fn(async () => []), delete: vi.fn(),
    ...overrides,
  };
}
function fakeTelemetryRepo(): ITelemetryRepo {
  return { insertMany: vi.fn(async () => 0), query: vi.fn(async () => ({ events: [], total: 0 })) };
}
function fakeCacheStore(): ICacheStore {
  const data = new Map<string, string>();
  return {
    async get(k) { return data.get(k) ?? null; },
    async set(k, v) { data.set(k, v); },
    async del(k) { data.delete(k); },
    async setNx(k, v) { if (data.has(k)) return false; data.set(k, v); return true; },
  };
}

const TENANT = "11111111-1111-1111-1111-111111111111";

describe("createGenFmm factory", () => {
  it("throws GenFmmConfigError when modules.check is enabled with no internal secret", () => {
    expect(() =>
      createGenFmm({
        catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
        cacheStore: fakeCacheStore(), internalSecret: "",
      }),
    ).toThrow(/internalSecret/);
  });

  it("throws GenFmmConfigError eagerly when overrides admin-listing is enabled but GEN_FMM_ADMIN_DATABASE_URL is unset", () => {
    // overrideRepo intentionally omitted: the factory would default-construct
    // PrismaTenantOverrideRepo, whose listAll() route (mounted because
    // modules.overrides defaults true) needs the admin/RLS-bypass connection.
    expect(() =>
      createGenFmm({
        catalogRepo: fakeCatalogRepo(), telemetryRepo: fakeTelemetryRepo(),
        cacheStore: fakeCacheStore(), internalSecret: "test-secret",
      }),
    ).toThrow(/GEN_FMM_ADMIN_DATABASE_URL/);
  });

  it("does not require GEN_FMM_ADMIN_DATABASE_URL when the host supplies its own overrideRepo", () => {
    expect(() =>
      createGenFmm({
        catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
        cacheStore: fakeCacheStore(), internalSecret: "test-secret",
      }),
    ).not.toThrow();
  });

  it("GET /health returns ok", async () => {
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app).get("/health");
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: "ok" });
  });

  it("GET /api/v1/entitlement/:tenantId/:flagKey resolves FLAG_NOT_FOUND with 200", async () => {
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app).get(`/api/v1/entitlement/${TENANT}/missing_flag`);
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ enabled: false, reason: "FLAG_NOT_FOUND" });
  });

  it("GET /internal/v1/fmm/check/:tenantId/:flagKey without the header is rejected", async () => {
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app).get(`/internal/v1/fmm/check/${TENANT}/f1`);
    expect(res.status).toBe(401);
  });

  it("GET /internal/v1/fmm/check/:tenantId/:flagKey with the header succeeds", async () => {
    const flag: FeatureFlagRecord = { key: "f1", moduleCode: null, defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 0 };
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo({ findFlagByKey: vi.fn(async () => flag) }),
      overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app).get(`/internal/v1/fmm/check/${TENANT}/f1`).set("x-internal-secret", "test-secret");
    expect(res.status).toBe(200);
    expect(res.body.enabled).toBe(true);
  });

  it("genFmm.check() works as a direct in-process call, bypassing HTTP entirely", async () => {
    const flag: FeatureFlagRecord = { key: "f1", moduleCode: null, defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 0 };
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo({ findFlagByKey: vi.fn(async () => flag) }),
      overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const result = await genFmm.check(TENANT, "f1");
    expect(result.enabled).toBe(true);
  });

  it("genFmm.requireFeature() 403s a disabled feature", async () => {
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    genFmm.app.get("/gated", (req, res, next) => { req.tenantId = TENANT; next(); }, genFmm.requireFeature("missing_flag"), (_req, res) => res.json({ ok: true }));
    const res = await request(genFmm.app).get("/gated");
    expect(res.status).toBe(403);
    expect(res.body.error).toBe("FEATURE_DISABLED");
  });

  it("a generic (non-AppError) error thrown from a host-added route is reported as a JSON 500", async () => {
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    genFmm.app.get("/boom", () => { throw new Error("kaboom"); });
    const res = await request(genFmm.app).get("/boom");
    expect(res.status).toBe(500);
    expect(res.body.error).toBe("INTERNAL_ERROR");
  });

  it("an unmatched route returns the JSON NOT_FOUND shape, not Express's default HTML 404", async () => {
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app).get("/does-not-exist");
    expect(res.status).toBe(404);
    expect(res.body).toEqual({ error: "NOT_FOUND", message: "Not found" });
  });

  it("when mounted as a sub-app, errors are forwarded to the parent's error middleware instead of being self-handled", async () => {
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    genFmm.app.get("/boom", () => { throw new Error("kaboom"); });

    const parentApp = express();
    parentApp.use("/fmm", genFmm.app);
    let parentSawErr: unknown;
    // Parent-level error-handling middleware — must come after the mount.
    parentApp.use((err: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
      parentSawErr = err;
      res.status(599).json({ error: "PARENT_HANDLED" });
    });

    const res = await request(parentApp).get("/fmm/boom");
    expect(parentSawErr).toBeInstanceOf(Error);
    expect((parentSawErr as Error).message).toBe("kaboom");
    expect(res.status).toBe(599);
    expect(res.body).toEqual({ error: "PARENT_HANDLED" });
  });

  it("DELETE /api/v1/overrides/:tenantId/:flagKey deletes via the route path params (regression: tenantId must come from req.params, not req.query)", async () => {
    const deleteFn = vi.fn(async () => true);
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo({ delete: deleteFn }), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app).delete(`/api/v1/overrides/${TENANT}/f1`);
    expect(res.status).toBe(204);
    expect(deleteFn).toHaveBeenCalledWith(TENANT, "f1");
  });

  it("GET /api/v1/catalog/modules?isActive=false forwards isActive:false to the repo (regression: z.coerce.boolean() previously coerced the string \"false\" to true)", async () => {
    const listModules = vi.fn(async () => []);
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo({ listModules }), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app).get("/api/v1/catalog/modules?isActive=false");
    expect(res.status).toBe(200);
    expect(listModules).toHaveBeenCalledWith({ isActive: false });
  });

  it("a malformed JSON request body is reported as a 400, not a 500", async () => {
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(), telemetryRepo: fakeTelemetryRepo(),
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app)
      .post("/api/v1/overrides")
      .set("Content-Type", "application/json")
      .send("{not valid json");
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("INVALID_JSON");
  });

  it("a Prisma FK-violation (P2003) is reported as a 422 REFERENCED_RECORD_NOT_FOUND, not a 500", async () => {
    const p2003 = new Prisma.PrismaClientKnownRequestError("Foreign key constraint failed", {
      code: "P2003",
      clientVersion: "5.20.0",
    });
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo({ upsert: vi.fn(async () => { throw p2003; }) }),
      telemetryRepo: fakeTelemetryRepo(), cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    const res = await request(genFmm.app)
      .post("/api/v1/overrides")
      .send({ tenantId: TENANT, flagKey: "no_such_flag", enabled: true });
    expect(res.status).toBe(422);
    expect(res.body.error).toBe("REFERENCED_RECORD_NOT_FOUND");
  });

  it("genFmm.flushTelemetryBuffer() flushes buffered usage events", async () => {
    const insertMany = vi.fn(async (events: unknown[]) => events.length);
    const genFmm = createGenFmm({
      catalogRepo: fakeCatalogRepo(), overrideRepo: fakeOverrideRepo(),
      telemetryRepo: { insertMany, query: vi.fn(async () => ({ events: [], total: 0 })) },
      cacheStore: fakeCacheStore(), internalSecret: "test-secret",
    });
    await genFmm.check(TENANT, "f1");
    const flushed = await genFmm.flushTelemetryBuffer();
    expect(flushed).toBe(1);
    expect(insertMany).toHaveBeenCalledTimes(1);
  });
});

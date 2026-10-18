import { describe, it, expect, beforeEach, vi } from "vitest";
import request from "supertest";
import type { Redis } from "ioredis";
import { createGenSup } from "./create-gen-sup.ts";
import { noopTenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
import { GenSupConfigError } from "./common/errors.ts";

describe("createGenSup", () => {
  beforeEach(() => {
    delete process.env.GEN_SUP_INTERNAL_SECRET;
  });

  it("throws GenSupConfigError when no internalSecret override and no env var are set", () => {
    expect(() => createGenSup({ tenantMetricsPort: noopTenantMetricsPort })).toThrow(GenSupConfigError);
  });

  it("exposes GET /api/v1/dashboard/kpis gated on X-Internal-Secret", async () => {
    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      valkeyUrl: "redis://localhost:1", // unreachable on purpose — service falls back to live compute
      modules: { tenants: false, featureFlags: false, announcements: false, analytics: false }, // this test is dashboard-only; tenants/featureFlags/announcements/analytics now default to true and would otherwise require GEN_TNT_BASE_URL / GEN_FMM_BASE_URL / DATABASE_URL / GEN_USG_BASE_URL etc.
    });

    const unauthorized = await request(app).get("/api/v1/dashboard/kpis");
    expect(unauthorized.status).toBe(401);

    const ok = await request(app).get("/api/v1/dashboard/kpis").set("X-Internal-Secret", "s3cret");
    expect(ok.status).toBe(200);
    expect(ok.body.activeTenants).toBe(0);
  });

  it("exposes GET /health with no auth required, with every optional module disabled", async () => {
    // dashboard:false / tenants:false / featureFlags:false / announcements:false /
    // analytics:false means no VALKEY_URL / GEN_TNT_BASE_URL / GEN_TNT_INTERNAL_SECRET /
    // RABBITMQ_URL / GEN_FMM_BASE_URL / DATABASE_URL / GEN_USG_BASE_URL is required to
    // construct the app — this also exercises the modules toggle itself.
    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      modules: { dashboard: false, tenants: false, featureFlags: false, announcements: false, analytics: false },
    });
    const res = await request(app).get("/health");
    expect(res.status).toBe(200);
  });

  it("exposes POST /api/v1/tenants gated on X-Internal-Secret when a tntClient and eventPublisher are supplied", async () => {
    const tntClient = {
      createTenant: async () => ({ id: "t1", slug: "acme", name: "Acme", status: "PROVISIONING", region: "us-east-1", primaryOwnerUserId: "u1", provisioningJobId: "j1", createdAt: "2026-01-01T00:00:00Z" }),
      getTenant: async () => null,
      getTenantBySlug: async () => null,
      suspendTenant: async () => { throw new Error("not used in this test"); },
      reactivateTenant: async () => { throw new Error("not used in this test"); },
    };
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      tntClient,
      eventPublisher,
      modules: { dashboard: false, featureFlags: false, announcements: false, analytics: false },
    });

    const unauthorized = await request(app).post("/api/v1/tenants").send({ name: "Acme", slug: "acme", region: "us-east-1", ownerEmail: "owner@acme.com" });
    expect(unauthorized.status).toBe(401);

    const ok = await request(app)
      .post("/api/v1/tenants")
      .set("X-Internal-Secret", "s3cret")
      .send({ name: "Acme", slug: "acme", region: "us-east-1", ownerEmail: "owner@acme.com" });
    expect(ok.status).toBe(201);
    expect(ok.body.slug).toBe("acme");
  });

  it("returns 400 VALIDATION_ERROR (not a 500) for a malformed UUID :id path param", async () => {
    const tntClient = {
      createTenant: async () => { throw new Error("not used in this test"); },
      getTenant: async () => { throw new Error("not used in this test"); },
      getTenantBySlug: async () => { throw new Error("not used in this test"); },
      suspendTenant: async () => { throw new Error("not used in this test"); },
      reactivateTenant: async () => { throw new Error("not used in this test"); },
    };
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      tntClient,
      eventPublisher,
      modules: { dashboard: false, featureFlags: false, announcements: false, analytics: false },
    });

    const res = await request(app).get("/api/v1/tenants/not-a-uuid").set("X-Internal-Secret", "s3cret");
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });

  it("exposes GET /api/v1/feature-flags gated on X-Internal-Secret when an fmmClient is supplied", async () => {
    const fmmClient = {
      listFlags: async () => [{ key: "f1", moduleCode: null, defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 100, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" }],
      updateFlag: async () => { throw new Error("not used in this test"); },
      setOverride: async () => { throw new Error("not used in this test"); },
      listOverridesForTenant: async () => { throw new Error("not used in this test"); },
      clearOverride: async () => { throw new Error("not used in this test"); },
    };
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      fmmClient,
      eventPublisher,
      modules: { dashboard: false, tenants: false, announcements: false, analytics: false },
    });

    const unauthorized = await request(app).get("/api/v1/feature-flags");
    expect(unauthorized.status).toBe(401);

    const ok = await request(app).get("/api/v1/feature-flags").set("X-Internal-Secret", "s3cret");
    expect(ok.status).toBe(200);
    expect(ok.body.flags).toHaveLength(1);
  });

  it("returns 400 VALIDATION_ERROR (not a 500) when PATCH /api/v1/feature-flags/:key is missing reason", async () => {
    const fmmClient = {
      listFlags: async () => { throw new Error("not used in this test"); },
      updateFlag: async () => { throw new Error("not used in this test"); },
      setOverride: async () => { throw new Error("not used in this test"); },
      listOverridesForTenant: async () => { throw new Error("not used in this test"); },
      clearOverride: async () => { throw new Error("not used in this test"); },
    };
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      fmmClient,
      eventPublisher,
      modules: { dashboard: false, tenants: false, announcements: false, analytics: false },
    });

    const res = await request(app)
      .patch("/api/v1/feature-flags/f1")
      .set("X-Internal-Secret", "s3cret")
      .send({ defaultEnabled: true });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });

  it("returns 400 VALIDATION_ERROR (not a silent no-op) when PATCH /api/v1/feature-flags/:key has only reason and no actual flag field", async () => {
    const fmmClient = {
      listFlags: async () => { throw new Error("not used in this test"); },
      updateFlag: async () => { throw new Error("not used in this test"); },
      setOverride: async () => { throw new Error("not used in this test"); },
      listOverridesForTenant: async () => { throw new Error("not used in this test"); },
      clearOverride: async () => { throw new Error("not used in this test"); },
    };
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      fmmClient,
      eventPublisher,
      modules: { dashboard: false, tenants: false, announcements: false, analytics: false },
    });

    const res = await request(app)
      .patch("/api/v1/feature-flags/f1")
      .set("X-Internal-Secret", "s3cret")
      .send({ reason: "just a reason, no field to change" });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });

  it("exposes PUT /api/v1/feature-flags/overrides/:tenantId/:flagKey gated on X-Internal-Secret", async () => {
    const fmmClient = {
      listFlags: async () => { throw new Error("not used in this test"); },
      updateFlag: async () => { throw new Error("not used in this test"); },
      setOverride: async () => ({ tenantId: "11111111-1111-1111-1111-111111111111", flagKey: "f1", enabled: true, config: {}, reason: "beta cohort", expiresAt: null, createdBy: null }),
      listOverridesForTenant: async () => { throw new Error("not used in this test"); },
      clearOverride: async () => { throw new Error("not used in this test"); },
    };
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      fmmClient,
      eventPublisher,
      modules: { dashboard: false, tenants: false, announcements: false, analytics: false },
    });

    const res = await request(app)
      .put("/api/v1/feature-flags/overrides/11111111-1111-1111-1111-111111111111/f1")
      .set("X-Internal-Secret", "s3cret")
      .send({ enabled: true, reason: "beta cohort" });
    expect(res.status).toBe(200);
    expect(res.body.override).toEqual(expect.objectContaining({ tenantId: "11111111-1111-1111-1111-111111111111", flagKey: "f1", enabled: true }));
  });

  it("exposes POST /api/v1/announcements gated on X-Internal-Secret when a prisma override is supplied", async () => {
    const fakePrisma = {
      announcement: {
        create: async ({ data }: { data: Record<string, unknown> }) => ({
          id: "a1", ...data, scheduledFor: null, sentAt: new Date(), createdAt: new Date(),
        }),
        findMany: async () => { throw new Error("not used in this test"); },
        count: async () => { throw new Error("not used in this test"); },
        update: async () => { throw new Error("not used in this test"); },
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      prisma: fakePrisma,
      eventPublisher,
      modules: { dashboard: false, tenants: false, featureFlags: false, analytics: false },
    });

    const unauthorized = await request(app).post("/api/v1/announcements").send({
      title: "Scheduled maintenance", body: "We will be performing maintenance this weekend.",
      channels: ["EMAIL"], createdBy: "11111111-1111-1111-1111-111111111111",
    });
    expect(unauthorized.status).toBe(401);

    const ok = await request(app)
      .post("/api/v1/announcements")
      .set("X-Internal-Secret", "s3cret")
      .send({
        title: "Scheduled maintenance", body: "We will be performing maintenance this weekend.",
        channels: ["EMAIL"], createdBy: "11111111-1111-1111-1111-111111111111",
      });
    expect(ok.status).toBe(201);
    expect(ok.body.announcement.title).toBe("Scheduled maintenance");
  });

  it("exposes GET /api/v1/announcements gated on X-Internal-Secret", async () => {
    const fakePrisma = {
      announcement: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => [],
        count: async () => 0,
        update: async () => { throw new Error("not used in this test"); },
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      prisma: fakePrisma,
      eventPublisher,
      modules: { dashboard: false, tenants: false, featureFlags: false, analytics: false },
    });

    const unauthorized = await request(app).get("/api/v1/announcements");
    expect(unauthorized.status).toBe(401);

    const ok = await request(app).get("/api/v1/announcements").set("X-Internal-Secret", "s3cret");
    expect(ok.status).toBe(200);
    expect(ok.body.announcements).toEqual([]);
  });

  it("returns 400 VALIDATION_ERROR (not a 500) when POST /api/v1/announcements has a body shorter than 10 chars", async () => {
    const fakePrisma = {
      announcement: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => { throw new Error("not used in this test"); },
        count: async () => { throw new Error("not used in this test"); },
        update: async () => { throw new Error("not used in this test"); },
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const eventPublisher = { publish: async () => undefined };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      prisma: fakePrisma,
      eventPublisher,
      modules: { dashboard: false, tenants: false, featureFlags: false, analytics: false },
    });

    const res = await request(app)
      .post("/api/v1/announcements")
      .set("X-Internal-Secret", "s3cret")
      .send({ title: "Hi", body: "too short", channels: ["EMAIL"], createdBy: "11111111-1111-1111-1111-111111111111" });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });

  it("exposes GET /api/v1/analytics/revenue gated on X-Internal-Secret", async () => {
    const fakePrisma = {
      revenueSnapshot: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => { throw new Error("not used in this test"); },
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const usgClient = { getSummary: async () => { throw new Error("not used in this test"); } };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      valkeyUrl: "redis://localhost:1", // unreachable on purpose — service falls back to live compute
      prisma: fakePrisma,
      usgClient,
      modules: { dashboard: false, tenants: false, featureFlags: false, announcements: false },
    });

    const unauthorized = await request(app).get("/api/v1/analytics/revenue");
    expect(unauthorized.status).toBe(401);

    const ok = await request(app).get("/api/v1/analytics/revenue").set("X-Internal-Secret", "s3cret");
    expect(ok.status).toBe(200);
    expect(ok.body.totalMrr).toBe(0);
  });

  it("exposes GET /api/v1/analytics/revenue/history gated on X-Internal-Secret", async () => {
    const fakePrisma = {
      revenueSnapshot: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => [],
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const usgClient = { getSummary: async () => { throw new Error("not used in this test"); } };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      valkeyUrl: "redis://localhost:1",
      prisma: fakePrisma,
      usgClient,
      modules: { dashboard: false, tenants: false, featureFlags: false, announcements: false },
    });

    const res = await request(app)
      .get("/api/v1/analytics/revenue/history?period=daily")
      .set("X-Internal-Secret", "s3cret");
    expect(res.status).toBe(200);
    expect(res.body.history).toEqual([]);
  });

  it("exposes GET /api/v1/analytics/usage gated on X-Internal-Secret", async () => {
    const fakePrisma = {
      revenueSnapshot: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => { throw new Error("not used in this test"); },
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const usgClient = {
      getSummary: async (tenantId: string) => ({ tenantId, meters: [{ metric: "api_calls", unit: "count", current: 1, limit: 100, pct: 1 }], trend: [] }),
    };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      valkeyUrl: "redis://localhost:1",
      prisma: fakePrisma,
      usgClient,
      modules: { dashboard: false, tenants: false, featureFlags: false, announcements: false },
    });

    const unauthorized = await request(app).get("/api/v1/analytics/usage?tenantIds=11111111-1111-1111-1111-111111111111");
    expect(unauthorized.status).toBe(401);

    const ok = await request(app)
      .get("/api/v1/analytics/usage?tenantIds=11111111-1111-1111-1111-111111111111")
      .set("X-Internal-Secret", "s3cret");
    expect(ok.status).toBe(200);
    expect(ok.body.totals).toEqual({ api_calls: 1 });
  });

  it("returns 400 VALIDATION_ERROR (not a 500) when GET /api/v1/analytics/usage has a malformed tenantId", async () => {
    const fakePrisma = {
      revenueSnapshot: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => { throw new Error("not used in this test"); },
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    const usgClient = { getSummary: async () => { throw new Error("not used in this test"); } };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      valkeyUrl: "redis://localhost:1",
      prisma: fakePrisma,
      usgClient,
      modules: { dashboard: false, tenants: false, featureFlags: false, announcements: false },
    });

    const res = await request(app)
      .get("/api/v1/analytics/usage?tenantIds=not-a-uuid")
      .set("X-Internal-Secret", "s3cret");
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });

  it("shares one prisma instance (announcements+analytics) and one valkey instance (dashboard+analytics) via overrides — no second/different client is silently constructed for either module", async () => {
    const fakePrisma = {
      announcement: {
        create: async ({ data }: { data: Record<string, unknown> }) => ({
          id: "a1", ...data, scheduledFor: null, sentAt: new Date(), createdAt: new Date(),
        }),
        findMany: async () => { throw new Error("not used in this test"); },
        count: async () => { throw new Error("not used in this test"); },
        update: async () => { throw new Error("not used in this test"); },
      },
      revenueSnapshot: {
        create: async () => { throw new Error("not used in this test"); },
        findMany: async () => [],
      },
    } as unknown as import("./infra/persistence/prisma-client.ts").PrismaClient;
    // A fake {get, set} object stands in for a real Valkey connection — cheap,
    // synchronous-feeling, and no ioredis client left dangling for the test
    // suite to clean up. Both dashboard and analytics read/write through it.
    // get() returns a distinct sentinel per cache key so the assertions below
    // can tell "both modules share this fake" apart from "each module built
    // its own disconnected real client and silently fell back to live
    // compute" — both scenarios would otherwise 200, making the test vacuous.
    const fakeValkey = {
      get: vi.fn(async (key: string) => {
        if (key === "sup:dashboard:kpi") return JSON.stringify({ activeTenants: 777 });
        if (key === "sup:analytics:revenue") return JSON.stringify({ totalMrr: 888 });
        return null;
      }),
      set: async () => "OK",
    } as unknown as Redis;
    const eventPublisher = { publish: async () => undefined };
    const usgClient = { getSummary: async () => { throw new Error("not used in this test"); } };

    const { app } = createGenSup({
      tenantMetricsPort: noopTenantMetricsPort,
      internalSecret: "s3cret",
      prisma: fakePrisma,
      valkey: fakeValkey,
      eventPublisher,
      usgClient,
      modules: { tenants: false, featureFlags: false }, // dashboard, announcements, analytics stay at their true default
    });

    // announcements and analytics both resolve prisma — if either had silently
    // built its own client instead of using the override, one of these two
    // calls would fail against the fake's stubbed methods.
    const announcementRes = await request(app)
      .post("/api/v1/announcements")
      .set("X-Internal-Secret", "s3cret")
      .send({
        title: "Scheduled maintenance", body: "We will be performing maintenance this weekend.",
        channels: ["EMAIL"], createdBy: "11111111-1111-1111-1111-111111111111",
      });
    expect(announcementRes.status).toBe(201);

    const historyRes = await request(app)
      .get("/api/v1/analytics/revenue/history?period=daily")
      .set("X-Internal-Secret", "s3cret");
    expect(historyRes.status).toBe(200);
    expect(historyRes.body.history).toEqual([]);

    // dashboard and analytics both resolve valkey — same reasoning as above,
    // via the cache read/write path instead of a direct prisma call. Each
    // assertion checks the sentinel value tied to that module's cache key,
    // not just a 200 — a silently-separate disconnected client would also
    // 200 (both services fail soft to live compute) but would never surface
    // these specific values.
    const dashboardRes = await request(app).get("/api/v1/dashboard/kpis").set("X-Internal-Secret", "s3cret");
    expect(dashboardRes.status).toBe(200);
    expect(dashboardRes.body.activeTenants).toBe(777);

    const revenueRes = await request(app).get("/api/v1/analytics/revenue").set("X-Internal-Secret", "s3cret");
    expect(revenueRes.status).toBe(200);
    expect(revenueRes.body.totalMrr).toBe(888);
  });
});

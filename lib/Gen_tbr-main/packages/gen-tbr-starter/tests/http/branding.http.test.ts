import { describe, it, expect, vi, beforeEach } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { createBrandingRouter } from "../../src/modules/branding/v1/branding.router.ts";
import { BrandingService } from "../../src/modules/branding/v1/branding.service.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import type { ITenantBrandingRepo, BrandingRecord } from "../../src/domain/ports/tenant-branding.repository.port.ts";
import type { ITenantDomainRepo } from "../../src/domain/ports/tenant-domain.repository.port.ts";

function buildApp(opts: { manifestEnabled?: boolean } = {}) {
  const record: BrandingRecord = {
    tenantId: "11111111-1111-1111-1111-111111111111", displayName: "Acme", tagline: null,
    logoUrl: null, logoDarkUrl: null, faviconUrl: null, primaryColor: null, secondaryColor: null,
    accentColor: null, fontFamily: null, theme: "SYSTEM", rawMeta: null, updatedAt: new Date(),
  };
  const brandingRepo: ITenantBrandingRepo = {
    findByTenantId: vi.fn(async (id) => (id === record.tenantId ? record : null)),
    upsert: vi.fn(async (input) => ({ ...record, ...input })),
    patch: vi.fn(async (id, input) => (id === record.tenantId ? { ...record, ...input } : null)),
  };
  const domainRepo: ITenantDomainRepo = {
    create: vi.fn(), findById: vi.fn(), findByDomain: vi.fn(async () => null),
    listByTenant: vi.fn(), updateStatus: vi.fn(), setPrimary: vi.fn(), touchLastChecked: vi.fn(),
  } as ITenantDomainRepo;
  const service = new BrandingService(brandingRepo, domainRepo);

  const app = express();
  app.use(express.json());
  app.use(
    "/api/v1/branding",
    createBrandingRouter({
      brandingService: service,
      internalSecretValue: "s3cret",
      assetsEnabled: false,
      manifestEnabled: opts.manifestEnabled,
    }),
  );
  app.use(errorHandler);
  return { app, record };
}

describe("branding router", () => {
  it("GET /:tenantId requires the internal secret", async () => {
    const { app, record } = buildApp();
    const res = await request(app).get(`/api/v1/branding/${record.tenantId}`);
    expect(res.status).toBe(401);
  });

  it("GET /:tenantId returns the brand with the secret header", async () => {
    const { app, record } = buildApp();
    const res = await request(app)
      .get(`/api/v1/branding/${record.tenantId}`)
      .set("X-Internal-Secret", "s3cret");
    expect(res.status).toBe(200);
    expect(res.body.displayName).toBe("Acme");
  });

  it("GET /:tenantId/manifest is genuinely unauthenticated", async () => {
    const { app, record } = buildApp();
    const res = await request(app).get(`/api/v1/branding/${record.tenantId}/manifest`);
    expect(res.status).toBe(200);
    expect(res.body.displayName).toBe("Acme");
  });

  it("PUT /:tenantId upserts with the secret header", async () => {
    const { app, record } = buildApp();
    const res = await request(app)
      .put(`/api/v1/branding/${record.tenantId}`)
      .set("X-Internal-Secret", "s3cret")
      .send({ displayName: "Acme Updated" });
    expect(res.status).toBe(200);
    expect(res.body.displayName).toBe("Acme Updated");
  });

  it("GET /:tenantId/manifest 404s for an unknown tenant", async () => {
    const { app } = buildApp();
    const res = await request(app).get("/api/v1/branding/22222222-2222-2222-2222-222222222222/manifest");
    expect(res.status).toBe(404);
  });

  it("GET /:tenantId/manifest 404s when manifestEnabled is false", async () => {
    const { app, record } = buildApp({ manifestEnabled: false });
    const res = await request(app).get(`/api/v1/branding/${record.tenantId}/manifest`);
    expect(res.status).toBe(404);
  });

  it("GET /manifest?domain=... 404s when manifestEnabled is false (does not fall through to /:tenantId)", async () => {
    const { app } = buildApp({ manifestEnabled: false });
    const res = await request(app).get("/api/v1/branding/manifest?domain=acme.example.com");
    expect(res.status).toBe(404);
  });
});

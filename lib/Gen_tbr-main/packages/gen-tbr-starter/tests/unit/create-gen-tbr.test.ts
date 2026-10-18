import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import request from "supertest";
import { createGenTbr } from "../../src/create-gen-tbr.ts";
import { GenTbrConfigError } from "../../src/common/errors.ts";
import type { ITenantBrandingRepo } from "../../src/domain/ports/tenant-branding.repository.port.ts";
import type { ITenantDomainRepo } from "../../src/domain/ports/tenant-domain.repository.port.ts";

const fakeBrandingRepo: ITenantBrandingRepo = {
  findByTenantId: vi.fn(async () => null),
  upsert: vi.fn(async (input) => ({ ...input, updatedAt: new Date() }) as any),
  patch: vi.fn(async () => null),
};
const fakeDomainRepo: ITenantDomainRepo = {
  create: vi.fn(), findById: vi.fn(), findByDomain: vi.fn(async () => null),
  listByTenant: vi.fn(async () => []), updateStatus: vi.fn(), setPrimary: vi.fn(), touchLastChecked: vi.fn(),
} as ITenantDomainRepo;

describe("createGenTbr", () => {
  const ORIGINAL_ENV = { ...process.env };

  beforeEach(() => {
    process.env = { ...ORIGINAL_ENV };
  });
  afterEach(() => {
    process.env = { ...ORIGINAL_ENV };
  });

  it("boots cleanly with injected repos and no env required", () => {
    const instance = createGenTbr({
      brandingRepo: fakeBrandingRepo,
      domainRepo: fakeDomainRepo,
      internalSecret: "s3cret",
      modules: { assets: false },
    });
    expect(instance.app).toBeDefined();
  });

  it("throws GenTbrConfigError when no DATABASE_URL and no repo override is given", () => {
    delete process.env.DATABASE_URL;
    expect(() => createGenTbr({ internalSecret: "s3cret" })).toThrow(GenTbrConfigError);
  });

  it("a disabled module's routes 404 instead of merely being undocumented", async () => {
    const instance = createGenTbr({
      brandingRepo: fakeBrandingRepo,
      domainRepo: fakeDomainRepo,
      internalSecret: "s3cret",
      modules: { domains: false, assets: false },
    });
    const res = await request(instance.app)
      .get("/api/v1/domains")
      .set("X-Internal-Secret", "s3cret");
    expect(res.status).toBe(404);
  });

  it("a disabled manifest module 404s its routes rather than leaving them mounted", async () => {
    const instance = createGenTbr({
      brandingRepo: fakeBrandingRepo,
      domainRepo: fakeDomainRepo,
      internalSecret: "s3cret",
      modules: { manifest: false, assets: false },
    });
    const res = await request(instance.app).get(
      "/api/v1/branding/11111111-1111-1111-1111-111111111111/manifest",
    );
    expect(res.status).toBe(404);
  });

  it("GET /health returns ok unauthenticated", async () => {
    const instance = createGenTbr({
      brandingRepo: fakeBrandingRepo,
      domainRepo: fakeDomainRepo,
      internalSecret: "s3cret",
      modules: { assets: false },
    });
    const res = await request(instance.app).get("/health");
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: "ok" });
  });
});

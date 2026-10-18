import { describe, it, expect, vi, beforeEach } from "vitest";
import { BrandingService } from "../../../src/modules/branding/v1/branding.service.ts";
import { BrandingNotFoundError, BrandingValidationError } from "../../../src/common/errors.ts";
import type { ITenantBrandingRepo, BrandingRecord } from "../../../src/domain/ports/tenant-branding.repository.port.ts";
import type { ITenantDomainRepo, TenantDomainRecord } from "../../../src/domain/ports/tenant-domain.repository.port.ts";

function makeBrandingRepo(overrides: Partial<ITenantBrandingRepo> = {}): ITenantBrandingRepo {
  return {
    findByTenantId: vi.fn(async () => null),
    upsert: vi.fn(async (input) => ({ ...input, updatedAt: new Date() }) as BrandingRecord),
    patch: vi.fn(async () => null),
    ...overrides,
  };
}

function makeDomainRepo(overrides: Partial<ITenantDomainRepo> = {}): ITenantDomainRepo {
  return {
    create: vi.fn(),
    findById: vi.fn(),
    findByDomain: vi.fn(async () => null),
    listByTenant: vi.fn(),
    updateStatus: vi.fn(),
    setPrimary: vi.fn(),
    touchLastChecked: vi.fn(),
    ...overrides,
  } as ITenantDomainRepo;
}

describe("BrandingService", () => {
  it("get() throws BrandingNotFoundError when nothing exists", async () => {
    const service = new BrandingService(makeBrandingRepo(), makeDomainRepo());
    await expect(service.get("tenant-1")).rejects.toThrow(BrandingNotFoundError);
  });

  it("upsert() rejects an invalid hex color", async () => {
    const service = new BrandingService(makeBrandingRepo(), makeDomainRepo());
    await expect(
      service.upsert({ tenantId: "tenant-1", displayName: "Acme", primaryColor: "not-a-hex" }),
    ).rejects.toThrow(BrandingValidationError);
  });

  it("upsert() accepts a valid hex color and delegates to the repo", async () => {
    const upsertSpy = vi.fn(async (input) => ({ ...input, updatedAt: new Date() }) as BrandingRecord);
    const service = new BrandingService(makeBrandingRepo({ upsert: upsertSpy }), makeDomainRepo());
    const result = await service.upsert({ tenantId: "tenant-1", displayName: "Acme", primaryColor: "#1f6feb" });
    expect(upsertSpy).toHaveBeenCalled();
    expect(result.primaryColor).toBe("#1f6feb");
  });

  it("getManifest({tenantId}) returns the brand directly", async () => {
    const record: BrandingRecord = {
      tenantId: "tenant-1", displayName: "Acme", tagline: null, logoUrl: null, logoDarkUrl: null,
      faviconUrl: null, primaryColor: null, secondaryColor: null, accentColor: null, fontFamily: null,
      theme: "SYSTEM", rawMeta: null, updatedAt: new Date(),
    };
    const service = new BrandingService(
      makeBrandingRepo({ findByTenantId: vi.fn(async () => record) }),
      makeDomainRepo(),
    );
    const manifest = await service.getManifest({ tenantId: "tenant-1" });
    expect(manifest.displayName).toBe("Acme");
  });

  it("getManifest({domain}) resolves tenantId via a VERIFIED/ACTIVE domain lookup", async () => {
    const record: BrandingRecord = {
      tenantId: "tenant-9", displayName: "Domain Brand", tagline: null, logoUrl: null, logoDarkUrl: null,
      faviconUrl: null, primaryColor: null, secondaryColor: null, accentColor: null, fontFamily: null,
      theme: "SYSTEM", rawMeta: null, updatedAt: new Date(),
    };
    const domainRecord = { tenantId: "tenant-9", status: "ACTIVE" } as TenantDomainRecord;
    const service = new BrandingService(
      makeBrandingRepo({ findByTenantId: vi.fn(async () => record) }),
      makeDomainRepo({ findByDomain: vi.fn(async () => domainRecord) }),
    );
    const manifest = await service.getManifest({ domain: "acme.example.com" });
    expect(manifest.tenantId).toBe("tenant-9");
  });

  it("getManifest() throws BrandingNotFoundError when the domain has no VERIFIED/ACTIVE match", async () => {
    const service = new BrandingService(
      makeBrandingRepo(),
      makeDomainRepo({ findByDomain: vi.fn(async () => null) }),
    );
    await expect(service.getManifest({ domain: "nowhere.example.com" })).rejects.toThrow(BrandingNotFoundError);
  });
});

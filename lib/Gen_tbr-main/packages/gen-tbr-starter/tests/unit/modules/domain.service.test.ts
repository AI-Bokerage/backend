import { describe, it, expect, vi } from "vitest";
import { DomainService } from "../../../src/modules/domains/v1/domain.service.ts";
import { DomainNotFoundError, DomainVerificationFailedError, InvalidDomainStatusTransitionError } from "../../../src/common/errors.ts";
import type { ITenantDomainRepo, TenantDomainRecord } from "../../../src/domain/ports/tenant-domain.repository.port.ts";
import type { IDnsVerifier } from "../../../src/domain/ports/dns-verifier.port.ts";

function record(overrides: Partial<TenantDomainRecord> = {}): TenantDomainRecord {
  return {
    id: "domain-1", tenantId: "tenant-1", domain: "example.com", verificationToken: "a".repeat(64),
    status: "PENDING", verificationMethod: "TXT", verifiedAt: null, lastCheckedAt: null,
    isPrimary: false, createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

function makeDomainRepo(overrides: Partial<ITenantDomainRepo> = {}): ITenantDomainRepo {
  return {
    create: vi.fn(async (input) => record(input)),
    findById: vi.fn(async () => record()),
    findByDomain: vi.fn(async () => null),
    listByTenant: vi.fn(async () => []),
    updateStatus: vi.fn(async (id, status) => record({ id, status })),
    setPrimary: vi.fn(async (tenantId, id) => record({ id, tenantId, isPrimary: true })),
    touchLastChecked: vi.fn(async () => {}),
    ...overrides,
  } as ITenantDomainRepo;
}

function makeDnsVerifier(overrides: Partial<IDnsVerifier> = {}): IDnsVerifier {
  return {
    resolveTxt: vi.fn(async () => []),
    resolveCname: vi.fn(async () => []),
    ...overrides,
  };
}

describe("DomainService", () => {
  it("claim() normalizes the domain (lowercase, trim, strip trailing dot)", async () => {
    const createSpy = vi.fn(async (input) => record(input));
    const service = new DomainService(makeDomainRepo({ create: createSpy }), makeDnsVerifier());
    await service.claim({ tenantId: "tenant-1", domain: "  Example.COM. " });
    expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({ domain: "example.com" }));
  });

  it("verify() flips PENDING to VERIFIED on a DNS TXT match", async () => {
    const pending = record({ status: "PENDING", verificationMethod: "TXT", verificationToken: "abc123" });
    const updateSpy = vi.fn(async (id, status) => record({ id, status }));
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => pending), updateStatus: updateSpy }),
      makeDnsVerifier({ resolveTxt: vi.fn(async () => [["gen-tbr-verify=abc123"]]) }),
    );
    const result = await service.verify("tenant-1", "domain-1");
    expect(result.status).toBe("VERIFIED");
    expect(updateSpy).toHaveBeenCalledWith("domain-1", "VERIFIED", expect.any(Object));
  });

  it("verify() throws DomainVerificationFailedError on no match, and still touches lastCheckedAt", async () => {
    const pending = record({ status: "PENDING", verificationMethod: "TXT", verificationToken: "abc123" });
    const touchSpy = vi.fn(async () => {});
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => pending), touchLastChecked: touchSpy }),
      makeDnsVerifier({ resolveTxt: vi.fn(async () => [["gen-tbr-verify=WRONG"]]) }),
    );
    await expect(service.verify("tenant-1", "domain-1")).rejects.toThrow(DomainVerificationFailedError);
    expect(touchSpy).toHaveBeenCalled();
  });

  it("verify() throws DomainNotFoundError for an unknown id", async () => {
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => null) }),
      makeDnsVerifier(),
    );
    await expect(service.verify("tenant-1", "missing")).rejects.toThrow(DomainNotFoundError);
  });

  it("verify() rejects a domain that is not PENDING", async () => {
    const active = record({ status: "ACTIVE" });
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => active) }),
      makeDnsVerifier(),
    );
    await expect(service.verify("tenant-1", "domain-1")).rejects.toThrow(InvalidDomainStatusTransitionError);
  });

  it("verify() throws DomainNotFoundError when tenantId doesn't match the domain's own tenant", async () => {
    const pending = record({ status: "PENDING", tenantId: "tenant-1" });
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => pending) }),
      makeDnsVerifier(),
    );
    await expect(service.verify("some-other-tenant", "domain-1")).rejects.toThrow(DomainNotFoundError);
  });

  it("activate() rejects a non-VERIFIED domain", async () => {
    const pending = record({ status: "PENDING" });
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => pending) }),
      makeDnsVerifier(),
    );
    await expect(service.activate("tenant-1", "domain-1")).rejects.toThrow(InvalidDomainStatusTransitionError);
  });

  it("activate() allows VERIFIED to ACTIVE", async () => {
    const verified = record({ status: "VERIFIED" });
    const updateSpy = vi.fn(async (id, status) => record({ id, status }));
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => verified), updateStatus: updateSpy }),
      makeDnsVerifier(),
    );
    const result = await service.activate("tenant-1", "domain-1");
    expect(result.status).toBe("ACTIVE");
  });

  it("activate() throws DomainNotFoundError when tenantId doesn't match the domain's own tenant", async () => {
    const verified = record({ status: "VERIFIED", tenantId: "tenant-1" });
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => verified) }),
      makeDnsVerifier(),
    );
    await expect(service.activate("some-other-tenant", "domain-1")).rejects.toThrow(DomainNotFoundError);
  });

  it("detach() rejects an already-DETACHED domain", async () => {
    const detached = record({ status: "DETACHED" });
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => detached) }),
      makeDnsVerifier(),
    );
    await expect(service.detach("tenant-1", "domain-1")).rejects.toThrow(InvalidDomainStatusTransitionError);
  });

  it("detach() throws DomainNotFoundError when tenantId doesn't match the domain's own tenant", async () => {
    const active = record({ status: "ACTIVE", tenantId: "tenant-1" });
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => active) }),
      makeDnsVerifier(),
    );
    await expect(service.detach("some-other-tenant", "domain-1")).rejects.toThrow(DomainNotFoundError);
  });

  it("setPrimary() delegates to the repo's atomic setPrimary", async () => {
    const active = record({ status: "ACTIVE" });
    const setPrimarySpy = vi.fn(async (tenantId, id) => record({ id, tenantId, isPrimary: true }));
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => active), setPrimary: setPrimarySpy }),
      makeDnsVerifier(),
    );
    const result = await service.setPrimary("tenant-1", "domain-1");
    expect(result.isPrimary).toBe(true);
    expect(setPrimarySpy).toHaveBeenCalledWith("tenant-1", "domain-1");
  });

  it("setPrimary() rejects a non-ACTIVE domain", async () => {
    const pending = record({ status: "PENDING" });
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => pending) }),
      makeDnsVerifier(),
    );
    await expect(service.setPrimary("tenant-1", "domain-1")).rejects.toThrow(InvalidDomainStatusTransitionError);
  });

  it("setPrimary() throws DomainNotFoundError when tenantId doesn't match the domain's own tenant", async () => {
    const active = record({ status: "ACTIVE", tenantId: "tenant-1" });
    const service = new DomainService(
      makeDomainRepo({ findById: vi.fn(async () => active) }),
      makeDnsVerifier(),
    );
    await expect(service.setPrimary("some-other-tenant", "domain-1")).rejects.toThrow(DomainNotFoundError);
  });
});

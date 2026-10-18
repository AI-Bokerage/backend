import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { PrismaClient } from "@prisma/client";
import { startTestPostgres, stopTestPostgres } from "../support/postgres-container.ts";
import { PrismaTenantDomainRepo } from "../../src/modules/domains/v1/repo.ts";
import { DomainAlreadyClaimedError } from "../../src/common/errors.ts";

describe("PrismaTenantDomainRepo", () => {
  let prisma: PrismaClient;
  let repo: PrismaTenantDomainRepo;
  const tenantId = "44444444-4444-4444-4444-444444444444";

  beforeAll(async () => {
    const url = await startTestPostgres();
    prisma = new PrismaClient({ datasources: { db: { url } } });
    repo = new PrismaTenantDomainRepo(prisma);
  }, 60_000);

  afterAll(async () => {
    await prisma.$disconnect();
    await stopTestPostgres();
  });

  it("creates a domain in PENDING status", async () => {
    const created = await repo.create({
      tenantId,
      domain: "example.com",
      verificationToken: "a".repeat(64),
      verificationMethod: "TXT",
    });
    expect(created.status).toBe("PENDING");
  });

  it("rejects a duplicate domain via the unique constraint", async () => {
    await expect(
      repo.create({
        tenantId,
        domain: "example.com",
        verificationToken: "b".repeat(64),
        verificationMethod: "TXT",
      }),
    ).rejects.toThrow(DomainAlreadyClaimedError);
  });

  it("setPrimary revokes the prior primary atomically", async () => {
    const d1 = await repo.create({
      tenantId,
      domain: "one.example.org",
      verificationToken: "c".repeat(64),
      verificationMethod: "TXT",
    });
    const d2 = await repo.create({
      tenantId,
      domain: "two.example.org",
      verificationToken: "d".repeat(64),
      verificationMethod: "TXT",
    });
    await repo.setPrimary(tenantId, d1.id);
    let refreshed1 = await repo.findById(d1.id);
    expect(refreshed1?.isPrimary).toBe(true);

    await repo.setPrimary(tenantId, d2.id);
    refreshed1 = await repo.findById(d1.id);
    const refreshed2 = await repo.findById(d2.id);
    expect(refreshed1?.isPrimary).toBe(false);
    expect(refreshed2?.isPrimary).toBe(true);
  });

  it("listByTenant excludes DETACHED by default", async () => {
    const d = await repo.create({
      tenantId,
      domain: "detach-me.example.net",
      verificationToken: "e".repeat(64),
      verificationMethod: "TXT",
    });
    await repo.updateStatus(d.id, "DETACHED");
    const list = await repo.listByTenant(tenantId);
    expect(list.find((x) => x.id === d.id)).toBeUndefined();
    const listAll = await repo.listByTenant(tenantId, { includeDetached: true });
    expect(listAll.find((x) => x.id === d.id)).toBeDefined();
  });
});

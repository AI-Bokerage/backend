import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { PrismaClient } from "@prisma/client";
import { startTestPostgres, stopTestPostgres } from "../support/postgres-container.ts";
import { PrismaTenantBrandingRepo } from "../../src/modules/branding/v1/repo.ts";

describe("PrismaTenantBrandingRepo", () => {
  let prisma: PrismaClient;
  let repo: PrismaTenantBrandingRepo;

  beforeAll(async () => {
    const url = await startTestPostgres();
    prisma = new PrismaClient({ datasources: { db: { url } } });
    repo = new PrismaTenantBrandingRepo(prisma);
  }, 60_000);

  afterAll(async () => {
    await prisma.$disconnect();
    await stopTestPostgres();
  });

  it("upserts then reads back a branding row", async () => {
    const tenantId = "11111111-1111-1111-1111-111111111111";
    await repo.upsert({ tenantId, displayName: "Acme Co" });
    const found = await repo.findByTenantId(tenantId);
    expect(found?.displayName).toBe("Acme Co");
    expect(found?.theme).toBe("SYSTEM");
  });

  it("returns null for an unknown tenant", async () => {
    const found = await repo.findByTenantId("22222222-2222-2222-2222-222222222222");
    expect(found).toBeNull();
  });

  it("patch partially updates an existing row", async () => {
    const tenantId = "33333333-3333-3333-3333-333333333333";
    await repo.upsert({ tenantId, displayName: "Beta Inc" });
    const patched = await repo.patch(tenantId, { tagline: "Beta rocks" });
    expect(patched?.tagline).toBe("Beta rocks");
    expect(patched?.displayName).toBe("Beta Inc");
  });
});

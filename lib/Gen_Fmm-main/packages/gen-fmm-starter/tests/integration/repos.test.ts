import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { startPostgresContainer, type TestPostgres } from "../support/postgres-container.ts";
import { PrismaCatalogRepo } from "../../src/modules/catalog/v1/repo.ts";
import { PrismaTenantOverrideRepo } from "../../src/modules/overrides/v1/repo.ts";
import { getPrismaClient } from "../../src/infra/persistence/prisma-client.ts";

const TENANT_A = "11111111-1111-1111-1111-111111111111";
const TENANT_B = "22222222-2222-2222-2222-222222222222";

describe("Prisma repos against real Postgres (RLS enforcement)", () => {
  let pg: TestPostgres;
  let catalogRepo: PrismaCatalogRepo;
  let overrideRepo: PrismaTenantOverrideRepo;

  beforeAll(async () => {
    pg = await startPostgresContainer();
    catalogRepo = new PrismaCatalogRepo();
    overrideRepo = new PrismaTenantOverrideRepo();
    await getPrismaClient().module.create({ data: { code: "reporting", name: "Reporting" } });
    await getPrismaClient().featureFlag.create({ data: { key: "new_dashboard", moduleCode: "reporting" } });
  }, 120_000);

  afterAll(async () => { await pg.stop(); });

  it("catalog CRUD works against the real global (non-RLS) tables", async () => {
    const module = await catalogRepo.findModuleByCode("reporting");
    expect(module?.name).toBe("Reporting");
    const flag = await catalogRepo.findFlagByKey("new_dashboard");
    expect(flag?.moduleCode).toBe("reporting");
  });

  it("an override written for tenant A is invisible when queried as tenant B (RLS isolation)", async () => {
    await overrideRepo.upsert({ tenantId: TENANT_A, flagKey: "new_dashboard", enabled: true });
    const asB = await overrideRepo.findOne(TENANT_B, "new_dashboard");
    expect(asB).toBeNull();
    const asA = await overrideRepo.findOne(TENANT_A, "new_dashboard");
    expect(asA?.enabled).toBe(true);
  });

  it("a second upsert for the same tenant+flag succeeds (proves RLS isn't silently blocking legitimate writes)", async () => {
    await overrideRepo.upsert({ tenantId: TENANT_A, flagKey: "new_dashboard", enabled: true });
    const updated = await overrideRepo.upsert({ tenantId: TENANT_A, flagKey: "new_dashboard", enabled: false });
    expect(updated.enabled).toBe(false);
  });

  it("expiresAt round-trips correctly and is not silently discarded (the source bug this library fixes)", async () => {
    const future = new Date(Date.now() + 3_600_000);
    await overrideRepo.upsert({ tenantId: TENANT_A, flagKey: "new_dashboard", enabled: true, expiresAt: future });
    const found = await overrideRepo.findOne(TENANT_A, "new_dashboard");
    expect(found?.expiresAt?.getTime()).toBe(future.getTime());
  });

  it("delete only removes the row for the given tenant, not other tenants' rows", async () => {
    await overrideRepo.upsert({ tenantId: TENANT_A, flagKey: "new_dashboard", enabled: true });
    await overrideRepo.upsert({ tenantId: TENANT_B, flagKey: "new_dashboard", enabled: true });
    await overrideRepo.delete(TENANT_A, "new_dashboard");
    expect(await overrideRepo.findOne(TENANT_A, "new_dashboard")).toBeNull();
    expect(await overrideRepo.findOne(TENANT_B, "new_dashboard")).not.toBeNull();
  });

  it("listAll uses a real RLS-bypassing admin connection, seeing rows across tenants", async () => {
    await overrideRepo.upsert({ tenantId: TENANT_A, flagKey: "new_dashboard", enabled: true });
    await overrideRepo.upsert({ tenantId: TENANT_B, flagKey: "new_dashboard", enabled: false });
    const all = await overrideRepo.listAll({ flagKey: "new_dashboard" });
    expect(all.some((r) => r.tenantId === TENANT_A)).toBe(true);
    expect(all.some((r) => r.tenantId === TENANT_B)).toBe(true);
  });
});

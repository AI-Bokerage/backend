import { describe, it, expect, vi } from "vitest";
import { Prisma } from "@prisma/client";
import { CatalogService } from "./service.ts";
import { ConflictError, NotFoundError } from "../../../common/errors.ts";
import type { ICatalogRepo, FeatureFlagRecord, ModuleRecord } from "../../../domain/ports/catalog.repository.port.ts";
import { EntitlementCache } from "../../entitlement/v1/cache.ts";
import type { ICacheStore } from "../../../domain/ports/cache-store.port.ts";

function fakeCacheStore(): ICacheStore {
  const data = new Map<string, string>();
  return {
    async get(k) { return data.get(k) ?? null; },
    async set(k, v) { data.set(k, v); },
    async del(k) { data.delete(k); },
    async setNx(k, v) { if (data.has(k)) return false; data.set(k, v); return true; },
  };
}

function fakeRepo(overrides: Partial<ICatalogRepo> = {}): ICatalogRepo {
  return {
    createModule: vi.fn(),
    findModuleByCode: vi.fn(async () => null),
    listModules: vi.fn(async () => []),
    updateModule: vi.fn(async () => null),
    createFlag: vi.fn(),
    findFlagByKey: vi.fn(async () => null),
    listFlags: vi.fn(async () => []),
    updateFlag: vi.fn(async () => null),
    deleteFlag: vi.fn(async () => false),
    upsertPlanModule: vi.fn(),
    listPlanModules: vi.fn(async () => []),
    deletePlanModule: vi.fn(async () => false),
    ...overrides,
  };
}

describe("CatalogService", () => {
  it("createModule throws ConflictError when the code already exists", async () => {
    const existing: ModuleRecord = { code: "billing", name: "Billing", description: null, category: null, isActive: true, displayOrder: 0, iconKey: null };
    const repo = fakeRepo({ findModuleByCode: vi.fn(async () => existing) });
    const service = new CatalogService(repo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    await expect(service.createModule({ code: "billing", name: "Billing" })).rejects.toThrow(ConflictError);
  });

  it("createModule remaps a real Prisma P2002 unique-violation to ConflictError (race past the pre-check)", async () => {
    const p2002 = new Prisma.PrismaClientKnownRequestError("Unique constraint failed on the fields: (`code`)", {
      code: "P2002",
      clientVersion: "5.20.0",
    });
    const repo = fakeRepo({ createModule: vi.fn(async () => { throw p2002; }) });
    const service = new CatalogService(repo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    await expect(service.createModule({ code: "billing", name: "Billing" })).rejects.toThrow(ConflictError);
  });

  it("createFlag remaps a real Prisma P2002 unique-violation to ConflictError", async () => {
    const p2002 = new Prisma.PrismaClientKnownRequestError("Unique constraint failed on the fields: (`key`)", {
      code: "P2002",
      clientVersion: "5.20.0",
    });
    const repo = fakeRepo({ createFlag: vi.fn(async () => { throw p2002; }) });
    const service = new CatalogService(repo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    await expect(service.createFlag({ key: "f1" })).rejects.toThrow(ConflictError);
  });

  it("createModule rethrows non-P2002 errors unchanged", async () => {
    const dbDown = new Error("connection terminated");
    const repo = fakeRepo({ createModule: vi.fn(async () => { throw dbDown; }) });
    const service = new CatalogService(repo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    await expect(service.createModule({ code: "billing", name: "Billing" })).rejects.toThrow("connection terminated");
  });

  it("updateModule throws NotFoundError when the repo returns null", async () => {
    const repo = fakeRepo();
    const service = new CatalogService(repo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    await expect(service.updateModule("missing", { name: "x" })).rejects.toThrow(NotFoundError);
  });

  it("updateFlag invalidates the cache and fires onFlagChanged", async () => {
    const flag: FeatureFlagRecord = { key: "f1", moduleCode: null, defaultEnabled: false, isGradualRollout: false, rolloutPercentage: 0 };
    const repo = fakeRepo({ updateFlag: vi.fn(async () => flag) });
    const cache = new EntitlementCache(fakeCacheStore(), 10, 1000, 60);
    const invalidateSpy = vi.spyOn(cache, "invalidateFlag");
    const onFlagChanged = vi.fn();
    const service = new CatalogService(repo, cache, onFlagChanged);
    await service.updateFlag("f1", { defaultEnabled: true });
    expect(invalidateSpy).toHaveBeenCalledWith("f1");
    expect(onFlagChanged).toHaveBeenCalledWith("f1");
  });

  it("deleteFlag throws NotFoundError when nothing was deleted", async () => {
    const repo = fakeRepo({ deleteFlag: vi.fn(async () => false) });
    const service = new CatalogService(repo, new EntitlementCache(fakeCacheStore(), 10, 1000, 60), vi.fn());
    await expect(service.deleteFlag("missing")).rejects.toThrow(NotFoundError);
  });
});

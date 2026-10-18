import { Prisma } from "@prisma/client";
import type {
  ICatalogRepo,
  CreateModuleInput,
  UpdateModuleInput,
  CreateFlagInput,
  UpdateFlagInput,
} from "../../../domain/ports/catalog.repository.port.ts";
import { ConflictError, NotFoundError } from "../../../common/errors.ts";
import type { EntitlementCache } from "../../entitlement/v1/cache.ts";

function isUniqueViolation(err: unknown): boolean {
  return err instanceof Prisma.PrismaClientKnownRequestError && err.code === "P2002";
}

export class CatalogService {
  constructor(
    private readonly repo: ICatalogRepo,
    private readonly cache: EntitlementCache,
    private readonly onFlagChanged: (flagKey: string, tenantId?: string) => void,
  ) {}

  async createModule(input: CreateModuleInput) {
    const existing = await this.repo.findModuleByCode(input.code);
    if (existing) throw new ConflictError("MODULE_ALREADY_EXISTS", `Module "${input.code}" already exists`);
    try {
      return await this.repo.createModule(input);
    } catch (err) {
      if (isUniqueViolation(err)) throw new ConflictError("MODULE_ALREADY_EXISTS", `Module "${input.code}" already exists`);
      throw err;
    }
  }

  listModules(filter: { category?: string; isActive?: boolean }) {
    return this.repo.listModules(filter);
  }

  async updateModule(code: string, input: UpdateModuleInput) {
    const updated = await this.repo.updateModule(code, input);
    if (!updated) throw new NotFoundError("MODULE_NOT_FOUND", `Module "${code}" not found`);
    return updated;
  }

  async createFlag(input: CreateFlagInput) {
    const existing = await this.repo.findFlagByKey(input.key);
    if (existing) throw new ConflictError("FLAG_ALREADY_EXISTS", `Flag "${input.key}" already exists`);
    try {
      return await this.repo.createFlag(input);
    } catch (err) {
      if (isUniqueViolation(err)) throw new ConflictError("FLAG_ALREADY_EXISTS", `Flag "${input.key}" already exists`);
      throw err;
    }
  }

  listFlags(filter: { moduleCode?: string }) {
    return this.repo.listFlags(filter);
  }

  async updateFlag(key: string, input: UpdateFlagInput) {
    const updated = await this.repo.updateFlag(key, input);
    if (!updated) throw new NotFoundError("FLAG_NOT_FOUND", `Flag "${key}" not found`);
    this.cache.invalidateFlag(key);
    this.onFlagChanged(key);
    return updated;
  }

  async deleteFlag(key: string): Promise<void> {
    const deleted = await this.repo.deleteFlag(key);
    if (!deleted) throw new NotFoundError("FLAG_NOT_FOUND", `Flag "${key}" not found`);
    this.cache.invalidateFlag(key);
    this.onFlagChanged(key);
  }

  // ponytail: no EntitlementCache invalidation needed here. Since the
  // planCode/plan-entitlement tier fix (entitlement/v1/service.ts), the
  // cache never stores a plan-dependent result at all — check()/bulk() only
  // cache the plan-independent tiers (override, default, rollout) and
  // re-resolve the plan tier live via listPlanModules() on every call. A
  // plan's module set changing therefore has no stale-cache entries to purge.
  // If a plan-module cache is ever added (e.g. to avoid the extra listPlanModules
  // round-trip on every plan-bearing check/bulk call), invalidation must come
  // back here — call it on both upsertPlanModule and deletePlanModule.
  async upsertPlanModule(planCode: string, moduleCode: string, entitlement: Record<string, unknown>) {
    const result = await this.repo.upsertPlanModule(planCode, moduleCode, entitlement);
    this.onFlagChanged(`plan:${planCode}`);
    return result;
  }

  listPlanModules(planCode: string) {
    return this.repo.listPlanModules(planCode);
  }

  async deletePlanModule(planCode: string, moduleCode: string): Promise<void> {
    const deleted = await this.repo.deletePlanModule(planCode, moduleCode);
    if (!deleted) throw new NotFoundError("PLAN_MODULE_NOT_FOUND", `Plan module mapping "${planCode}"/"${moduleCode}" not found`);
    this.onFlagChanged(`plan:${planCode}`);
  }
}

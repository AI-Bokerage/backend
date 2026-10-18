import { Prisma } from "@prisma/client";
import { getPrismaClient } from "../../../infra/persistence/prisma-client.ts";
import type {
  ICatalogRepo,
  ModuleRecord,
  PlanModuleRecord,
  FeatureFlagRecord,
  CreateModuleInput,
  UpdateModuleInput,
  CreateFlagInput,
  UpdateFlagInput,
} from "../../../domain/ports/catalog.repository.port.ts";

function isNotFound(err: unknown): boolean {
  return err instanceof Prisma.PrismaClientKnownRequestError && err.code === "P2025";
}

function toPlanModuleRecord(row: { planCode: string; moduleCode: string; entitlement: unknown }): PlanModuleRecord {
  return { planCode: row.planCode, moduleCode: row.moduleCode, entitlement: row.entitlement as Record<string, unknown> };
}

export class PrismaCatalogRepo implements ICatalogRepo {
  async createModule(input: CreateModuleInput): Promise<ModuleRecord> {
    return getPrismaClient().module.create({ data: input });
  }

  async findModuleByCode(code: string): Promise<ModuleRecord | null> {
    return getPrismaClient().module.findUnique({ where: { code } });
  }

  async listModules(filter: { category?: string; isActive?: boolean }): Promise<ModuleRecord[]> {
    return getPrismaClient().module.findMany({
      where: {
        ...(filter.category ? { category: filter.category } : {}),
        ...(filter.isActive !== undefined ? { isActive: filter.isActive } : {}),
      },
      orderBy: { displayOrder: "asc" },
    });
  }

  async updateModule(code: string, input: UpdateModuleInput): Promise<ModuleRecord | null> {
    try {
      return await getPrismaClient().module.update({ where: { code }, data: input });
    } catch (err) {
      if (isNotFound(err)) return null;
      throw err;
    }
  }

  async createFlag(input: CreateFlagInput): Promise<FeatureFlagRecord> {
    return getPrismaClient().featureFlag.create({ data: input });
  }

  async findFlagByKey(key: string): Promise<FeatureFlagRecord | null> {
    return getPrismaClient().featureFlag.findUnique({ where: { key } });
  }

  async listFlags(filter: { moduleCode?: string }): Promise<FeatureFlagRecord[]> {
    return getPrismaClient().featureFlag.findMany({
      where: filter.moduleCode ? { moduleCode: filter.moduleCode } : {},
    });
  }

  async updateFlag(key: string, input: UpdateFlagInput): Promise<FeatureFlagRecord | null> {
    try {
      return await getPrismaClient().featureFlag.update({ where: { key }, data: input });
    } catch (err) {
      if (isNotFound(err)) return null;
      throw err;
    }
  }

  async deleteFlag(key: string): Promise<boolean> {
    try {
      await getPrismaClient().featureFlag.delete({ where: { key } });
      return true;
    } catch (err) {
      if (isNotFound(err)) return false;
      throw err;
    }
  }

  async upsertPlanModule(planCode: string, moduleCode: string, entitlement: Record<string, unknown>): Promise<PlanModuleRecord> {
    const row = await getPrismaClient().planModule.upsert({
      where: { planCode_moduleCode: { planCode, moduleCode } },
      create: { planCode, moduleCode, entitlement: entitlement as Prisma.InputJsonValue },
      update: { entitlement: entitlement as Prisma.InputJsonValue },
    });
    return toPlanModuleRecord(row);
  }

  async listPlanModules(planCode: string): Promise<PlanModuleRecord[]> {
    const rows = await getPrismaClient().planModule.findMany({ where: { planCode } });
    return rows.map(toPlanModuleRecord);
  }

  async deletePlanModule(planCode: string, moduleCode: string): Promise<boolean> {
    try {
      await getPrismaClient().planModule.delete({ where: { planCode_moduleCode: { planCode, moduleCode } } });
      return true;
    } catch (err) {
      if (isNotFound(err)) return false;
      throw err;
    }
  }
}

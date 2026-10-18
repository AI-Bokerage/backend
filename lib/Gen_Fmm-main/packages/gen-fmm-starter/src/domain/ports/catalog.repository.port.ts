export interface ModuleRecord {
  code: string;
  name: string;
  description: string | null;
  category: string | null;
  isActive: boolean;
  displayOrder: number;
  iconKey: string | null;
}

export interface PlanModuleRecord {
  planCode: string;
  moduleCode: string;
  entitlement: Record<string, unknown>;
}

export interface FeatureFlagRecord {
  key: string;
  moduleCode: string | null;
  defaultEnabled: boolean;
  isGradualRollout: boolean;
  rolloutPercentage: number;
}

export interface CreateModuleInput {
  code: string;
  name: string;
  description?: string;
  category?: string;
  isActive?: boolean;
  displayOrder?: number;
  iconKey?: string;
}

export interface UpdateModuleInput {
  name?: string;
  description?: string;
  category?: string;
  isActive?: boolean;
  displayOrder?: number;
  iconKey?: string;
}

export interface CreateFlagInput {
  key: string;
  moduleCode?: string;
  defaultEnabled?: boolean;
  isGradualRollout?: boolean;
  rolloutPercentage?: number;
}

export interface UpdateFlagInput {
  moduleCode?: string | null;
  defaultEnabled?: boolean;
  isGradualRollout?: boolean;
  rolloutPercentage?: number;
}

export interface ICatalogRepo {
  createModule(input: CreateModuleInput): Promise<ModuleRecord>;
  findModuleByCode(code: string): Promise<ModuleRecord | null>;
  listModules(filter: { category?: string; isActive?: boolean }): Promise<ModuleRecord[]>;
  updateModule(code: string, input: UpdateModuleInput): Promise<ModuleRecord | null>;

  createFlag(input: CreateFlagInput): Promise<FeatureFlagRecord>;
  findFlagByKey(key: string): Promise<FeatureFlagRecord | null>;
  listFlags(filter: { moduleCode?: string }): Promise<FeatureFlagRecord[]>;
  updateFlag(key: string, input: UpdateFlagInput): Promise<FeatureFlagRecord | null>;
  deleteFlag(key: string): Promise<boolean>;

  upsertPlanModule(planCode: string, moduleCode: string, entitlement: Record<string, unknown>): Promise<PlanModuleRecord>;
  listPlanModules(planCode: string): Promise<PlanModuleRecord[]>;
  deletePlanModule(planCode: string, moduleCode: string): Promise<boolean>;
}

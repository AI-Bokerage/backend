export { createGenFmm } from "./create-gen-fmm.ts";
export type { GenFmmConfig, GenFmmModulesConfig, GenFmmInstance } from "./create-gen-fmm.ts";

export type { EntitlementCheckResult, BulkEntitlementResult, EntitlementReason } from "./modules/entitlement/v1/types.ts";
export { resolveEntitlement, computeRolloutBucket } from "./modules/entitlement/v1/resolve.ts";

export type {
  ICatalogRepo, ModuleRecord, PlanModuleRecord, FeatureFlagRecord,
  CreateModuleInput, UpdateModuleInput, CreateFlagInput, UpdateFlagInput,
} from "./domain/ports/catalog.repository.port.ts";
export type { ITenantOverrideRepo, TenantOverrideRecord, UpsertOverrideInput } from "./domain/ports/tenant-override.repository.port.ts";
export type { ITelemetryRepo, UsageEventInput, UsageEventRecord, TelemetryQueryFilter } from "./domain/ports/telemetry.repository.port.ts";
export type { ICacheStore } from "./domain/ports/cache-store.port.ts";

export { PrismaCatalogRepo } from "./modules/catalog/v1/repo.ts";
export { PrismaTenantOverrideRepo } from "./modules/overrides/v1/repo.ts";
export { PrismaTelemetryRepo } from "./modules/telemetry/v1/repo.ts";
export { RedisCacheStore } from "./infra/cache/redis-cache-store.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";

export { AppError, GenFmmConfigError, ConflictError, NotFoundError, BusinessRuleError, InvalidTenantIdError } from "./common/errors.ts";

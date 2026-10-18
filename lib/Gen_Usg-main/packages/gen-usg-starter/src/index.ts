export { createGenUsg } from "./create-gen-usg.ts";
export type { GenUsgConfig, GenUsgModulesConfig, GenUsgInstance } from "./create-gen-usg.ts";
export { registerMeter, getMeterDefinition, listRegisteredMeterCodes } from "./modules/meters/v1/registry.ts";
export type { MeterDefinition, RegisterMeterInput, MeterMode } from "./modules/meters/v1/registry.ts";

export type { ICounterStore, SetBatchEntry } from "./domain/ports/counter-store.port.ts";
export type { ILimitProvider } from "./domain/ports/limit-provider.port.ts";
export type { IMeterRepo, RollupPeriod, UsageSnapshotRecord } from "./domain/ports/meter.repository.port.ts";
export type { IGraceOverageRepo, GraceOverageRecord, GraceOverageStatus } from "./domain/ports/grace-overage.repository.port.ts";
export type { IReconciliationRepo, InsertReconciliationLogInput } from "./domain/ports/reconciliation.repository.port.ts";
export type { IIdempotencyRepo } from "./domain/ports/idempotency.repository.port.ts";

export { RedisCounterStore } from "./infra/cache/redis-counter-store.ts";
export { PrismaMeterRepo } from "./modules/rollup/v1/repo.ts";
export { PrismaGraceOverageRepo } from "./modules/grace-overage/v1/repo.ts";
export { PrismaReconciliationRepo } from "./modules/reconciliation/v1/repo.ts";
export { PrismaIdempotencyRepo } from "./infra/persistence/prisma-idempotency-repo.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";

export type { IncrementInput, IncrementResult } from "./modules/increment/v1/service.ts";
export type { CheckResult, CheckOutcome } from "./modules/check/v1/service.ts";
export type { RollupResult } from "./modules/rollup/v1/service.ts";
export type { ReconciliationSweepResult } from "./modules/reconciliation/v1/service.ts";
export type { ClosedGraceWindow, GraceWindow } from "./modules/grace-overage/v1/service.ts";
export type { UsageSummary, MeterSummary } from "./modules/summary/v1/service.ts";

export { AppError, GenUsgConfigError, MeterNotRegisteredError, IncrementDeltaInvalidError, UsageEventOutOfWindowError, ResourceIdRequiredError, InvalidTenantIdError } from "./common/errors.ts";

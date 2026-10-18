import express, { type Express } from "express";
import "express-async-errors";
import helmet from "helmet";
import cors from "cors";

import { requireEnv, optionalEnv } from "./config/env.ts";
import { GenUsgConfigError } from "./common/errors.ts";

import type { ICounterStore } from "./domain/ports/counter-store.port.ts";
import type { ILimitProvider } from "./domain/ports/limit-provider.port.ts";
import type { IMeterRepo } from "./domain/ports/meter.repository.port.ts";
import type { IGraceOverageRepo } from "./domain/ports/grace-overage.repository.port.ts";
import type { IReconciliationRepo } from "./domain/ports/reconciliation.repository.port.ts";
import type { IIdempotencyRepo } from "./domain/ports/idempotency.repository.port.ts";

import { RedisCounterStore } from "./infra/cache/redis-counter-store.ts";
import { PrismaMeterRepo } from "./modules/rollup/v1/repo.ts";
import { PrismaGraceOverageRepo } from "./modules/grace-overage/v1/repo.ts";
import { PrismaReconciliationRepo } from "./modules/reconciliation/v1/repo.ts";
import { PrismaIdempotencyRepo } from "./infra/persistence/prisma-idempotency-repo.ts";

import { GraceOverageService, type ClosedGraceWindow } from "./modules/grace-overage/v1/service.ts";
import { IncrementService, type IncrementInput, type IncrementResult } from "./modules/increment/v1/service.ts";
import { IncrementController } from "./modules/increment/v1/controller.ts";
import { incrementRoutes } from "./modules/increment/v1/routes.ts";
import { CheckService, type CheckResult } from "./modules/check/v1/service.ts";
import { CheckController } from "./modules/check/v1/controller.ts";
import { checkRoutes } from "./modules/check/v1/routes.ts";
import { RollupService, type RollupResult } from "./modules/rollup/v1/service.ts";
import { ReconciliationService, type ReconciliationSweepResult } from "./modules/reconciliation/v1/service.ts";
import { SummaryService } from "./modules/summary/v1/service.ts";
import { SummaryController } from "./modules/summary/v1/controller.ts";
import { summaryRoutes } from "./modules/summary/v1/routes.ts";

import { errorHandler } from "./middleware/error-handler.ts";
import { internalSecretMiddleware } from "./middleware/internal-secret.ts";

export interface GenUsgModulesConfig {
  check?: boolean;
  increment?: boolean;
  summary?: boolean;
}

export interface GenUsgConfig {
  counterStore?: ICounterStore;
  meterRepo?: IMeterRepo;
  graceOverageRepo?: IGraceOverageRepo;
  reconciliationRepo?: IReconciliationRepo;
  idempotencyRepo?: IIdempotencyRepo;
  limitProvider?: ILimitProvider;
  modules?: GenUsgModulesConfig;
  internalSecret?: string;
  backdateDays?: number;
  dedupTtlSec?: number;
  limitCacheTtlSec?: number;
  limitLockTtlSec?: number;
  softWarnPct80?: number;
  softWarnPct95?: number;
  graceWindowDays?: number;
  driftThresholdPct?: number;
  trendSinceDays?: number;
}

export interface GenUsgInstance {
  app: Express;
  increment(input: IncrementInput): Promise<IncrementResult>;
  check(tenantId: string, metric: string, delta?: number): Promise<CheckResult>;
  runDailyRollup(tenantIds: string[]): Promise<RollupResult>;
  runMonthlyRollup(tenantIds: string[]): Promise<RollupResult>;
  runReconciliationSweep(tenantIds: string[]): Promise<ReconciliationSweepResult>;
  closeExpiredGraceWindows(tenantIds: string[]): Promise<ClosedGraceWindow[]>;
}

export { registerMeter, getMeterDefinition, listRegisteredMeterCodes } from "./modules/meters/v1/registry.ts";
export type { MeterDefinition, RegisterMeterInput, MeterMode } from "./modules/meters/v1/registry.ts";

function resolveCounterStore(override: ICounterStore | undefined): ICounterStore {
  if (override) return override;
  return new RedisCounterStore(requireEnv("REDIS_URL"));
}

function resolveMeterRepo(override: IMeterRepo | undefined): IMeterRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaMeterRepo();
}

function resolveGraceOverageRepo(override: IGraceOverageRepo | undefined): IGraceOverageRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaGraceOverageRepo();
}

function resolveReconciliationRepo(override: IReconciliationRepo | undefined): IReconciliationRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaReconciliationRepo();
}

function resolveIdempotencyRepo(override: IIdempotencyRepo | undefined): IIdempotencyRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaIdempotencyRepo();
}

export function createGenUsg(config: GenUsgConfig): GenUsgInstance {
  const modules: Required<GenUsgModulesConfig> = {
    check: config.modules?.check ?? true,
    increment: config.modules?.increment ?? true,
    summary: config.modules?.summary ?? true,
  };

  const counterStore = resolveCounterStore(config.counterStore);
  const meterRepo = resolveMeterRepo(config.meterRepo);
  const graceOverageRepo = resolveGraceOverageRepo(config.graceOverageRepo);
  const reconciliationRepo = resolveReconciliationRepo(config.reconciliationRepo);
  const idempotencyRepo = resolveIdempotencyRepo(config.idempotencyRepo);

  // limitProvider has no default adapter — resolved lazily only when check()
  // or the summary module actually needs it, matching every sibling's
  // resolveXxx idiom of never validating a module-specific dependency for a
  // feature the host never exercises. Constructing CheckService still
  // requires SOME ILimitProvider reference, so an unset limitProvider throws
  // GenUsgConfigError here at createGenUsg() time whenever check or summary
  // is enabled — never as a mid-request 500.
  if ((modules.check || modules.summary) && !config.limitProvider) {
    throw new GenUsgConfigError(
      "limitProvider is required when modules.check or modules.summary is enabled — supply an ILimitProvider implementation",
    );
  }
  const limitProvider = config.limitProvider as ILimitProvider;

  const internalSecret = config.internalSecret ?? optionalEnv("GEN_USG_INTERNAL_SECRET", "");
  if ((modules.increment || modules.check) && internalSecret === "") {
    throw new GenUsgConfigError(
      "internalSecret is required (set GEN_USG_INTERNAL_SECRET or pass config.internalSecret) when modules.increment or modules.check is enabled",
    );
  }

  const graceOverageService = new GraceOverageService(graceOverageRepo);
  const incrementService = new IncrementService(counterStore, idempotencyRepo, {
    backdateDays: config.backdateDays ?? 30,
    dedupTtlSec: config.dedupTtlSec ?? 86400,
  });
  const checkService = new CheckService(counterStore, limitProvider, graceOverageService, {
    limitCacheTtlSec: config.limitCacheTtlSec ?? 300,
    limitLockTtlSec: config.limitLockTtlSec ?? 5,
    softWarnPct80: config.softWarnPct80 ?? 0.8,
    softWarnPct95: config.softWarnPct95 ?? 0.95,
    graceWindowDays: config.graceWindowDays ?? 7,
  });
  const rollupService = new RollupService(counterStore, meterRepo);
  const reconciliationService = new ReconciliationService(counterStore, meterRepo, reconciliationRepo, config.driftThresholdPct ?? 2);
  const summaryService = new SummaryService(counterStore, meterRepo, graceOverageRepo, checkService, config.trendSinceDays ?? 7);

  const app = express();
  app.use(helmet());
  const allowedOrigins = optionalEnv("ALLOWED_ORIGINS", "*");
  app.use(cors({ origin: allowedOrigins === "*" ? true : allowedOrigins.split(",") }));
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  const gate = internalSecretMiddleware(internalSecret);

  if (modules.increment) {
    app.use("/internal/usage/increment", gate, incrementRoutes(new IncrementController(incrementService)));
  }
  if (modules.check) {
    app.use("/internal/usage/check", gate, checkRoutes(new CheckController(checkService)));
  }
  if (modules.summary) {
    app.use("/api/v1/usage/summary", summaryRoutes(new SummaryController(summaryService)));
  }

  app.use(errorHandler);

  return {
    app,
    increment: (input) => incrementService.increment(input),
    check: (tenantId, metric, delta) => checkService.check(tenantId, metric, delta),
    runDailyRollup: (tenantIds) => rollupService.runRollup(tenantIds, "daily"),
    runMonthlyRollup: (tenantIds) => rollupService.runRollup(tenantIds, "monthly"),
    runReconciliationSweep: (tenantIds) => reconciliationService.runSweep(tenantIds),
    closeExpiredGraceWindows: (tenantIds) => graceOverageService.closeExpiredGraceWindows(tenantIds, new Date()),
  };
}

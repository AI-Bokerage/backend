import "express-async-errors";
import express, { type Express } from "express";
import helmet from "helmet";
import cors from "cors";
import { requireEnv, env } from "./config/env.ts";
import { GenSlaConfigError } from "./common/errors.ts";
import { logger } from "./common/logger.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { getPrismaClient } from "./infra/persistence/prisma-client.ts";
import { ValkeyLock } from "./infra/cache/valkey-lock.ts";
import { RabbitMqBus } from "./infra/messaging/rabbitmq-bus.ts";
import { PrismaOutboxWriter } from "./infra/messaging/outbox-writer.ts";
import { startOutboxDispatcher } from "./infra/messaging/outbox-dispatcher.ts";
import { PrismaPolicyRepo } from "./modules/policies/v1/repo.ts";
import { PolicyService } from "./modules/policies/v1/service.ts";
import { createPoliciesRouter } from "./modules/policies/v1/router.ts";
import { PrismaInstanceRepo } from "./modules/instances/v1/repo.ts";
import { InstanceService } from "./modules/instances/v1/service.ts";
import { createInstancesRouter } from "./modules/instances/v1/router.ts";
import { PrismaMetricsRepo } from "./modules/metrics/v1/repo.ts";
import { MetricsService } from "./modules/metrics/v1/service.ts";
import { createMetricsRouter } from "./modules/metrics/v1/router.ts";
import { startSlaTimerWorker } from "./workers/sla-timer.worker.ts";
import type { ISlaPolicyRepo } from "./domain/ports/policy-repo.port.ts";
import type { ISlaInstanceRepo } from "./domain/ports/instance-repo.port.ts";
import type { IMetricsRepo } from "./domain/ports/metrics-repo.port.ts";
import type { IDistributedLock } from "./domain/ports/distributed-lock.port.ts";
import type { IEventPublisher } from "./domain/ports/event-publisher.port.ts";
import type { IOutboxWriter } from "./domain/ports/outbox-writer.port.ts";

export interface GenSlaModulesConfig {
  policies?: boolean;
  instances?: boolean;
  metrics?: boolean;
  worker?: boolean;
}

export interface GenSlaConfig {
  policyRepo?: ISlaPolicyRepo;
  instanceRepo?: ISlaInstanceRepo;
  metricsRepo?: IMetricsRepo;
  lock?: IDistributedLock;
  eventPublisher?: IEventPublisher;
  outboxWriter?: IOutboxWriter;
  internalSecret?: string;
  modules?: GenSlaModulesConfig;
}

export interface GenSlaWorker {
  close(): void;
}

export interface GenSlaInstance {
  app: Express;
  worker?: GenSlaWorker;
}

function resolvePolicyRepo(override: ISlaPolicyRepo | undefined): ISlaPolicyRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaPolicyRepo(getPrismaClient());
}

function resolveInstanceRepo(override: ISlaInstanceRepo | undefined): ISlaInstanceRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaInstanceRepo(getPrismaClient());
}

function resolveMetricsRepo(override: IMetricsRepo | undefined): IMetricsRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaMetricsRepo(getPrismaClient());
}

function resolveOutboxWriter(override: IOutboxWriter | undefined): IOutboxWriter {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaOutboxWriter(getPrismaClient());
}

function resolveLock(override: IDistributedLock | undefined): IDistributedLock {
  if (override) return override;
  const url = requireEnv("VALKEY_URL");
  return new ValkeyLock(url);
}

function resolveEventPublisher(override: IEventPublisher | undefined): IEventPublisher {
  if (override) return override;
  const url = requireEnv("RABBITMQ_URL");
  return new RabbitMqBus(url);
}

function resolveInternalSecret(override: string | undefined): string {
  return override ?? requireEnv("GEN_SLA_INTERNAL_SECRET");
}

export function createGenSla(config: GenSlaConfig): GenSlaInstance {
  const modules: Required<GenSlaModulesConfig> = {
    policies: config.modules?.policies ?? true,
    instances: config.modules?.instances ?? true,
    metrics: config.modules?.metrics ?? true,
    worker: config.modules?.worker ?? true,
  };

  if (!modules.policies && !modules.instances && !modules.metrics) {
    throw new GenSlaConfigError("At least one of modules.policies, modules.instances, or modules.metrics must be enabled");
  }

  const internalSecretValue = resolveInternalSecret(config.internalSecret);
  const policyRepo = resolvePolicyRepo(config.policyRepo);
  const instanceRepo = resolveInstanceRepo(config.instanceRepo);
  const outboxWriter = resolveOutboxWriter(config.outboxWriter);

  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  const instanceService = new InstanceService(instanceRepo, policyRepo, outboxWriter);

  if (modules.policies) {
    const policyService = new PolicyService(policyRepo);
    app.use("/api/v1/sla/:tenantId/policies", createPoliciesRouter({ policyService, internalSecretValue }));
  }

  if (modules.instances) {
    app.use("/api/v1/sla/:tenantId/instances", createInstancesRouter({ instanceService, internalSecretValue }));
  }

  if (modules.metrics) {
    const metricsRepo = resolveMetricsRepo(config.metricsRepo);
    const metricsService = new MetricsService(metricsRepo);
    app.use("/api/v1/sla/:tenantId/metrics", createMetricsRouter({ metricsService, internalSecretValue }));
  }

  app.use(errorHandler);

  let worker: GenSlaWorker | undefined;
  if (modules.worker) {
    const lock = resolveLock(config.lock);
    const publisher = resolveEventPublisher(config.eventPublisher);
    if (publisher instanceof RabbitMqBus) {
      publisher.connect().catch((err) => logger.error({ err }, "[create-gen-sla] initial RabbitMQ connection failed — will retry on next publish"));
    }

    const timer = startSlaTimerWorker({
      instanceRepo,
      instanceService,
      lock,
      pollIntervalMs: env.TIMER_POLL_INTERVAL_MS,
      batchSize: env.TIMER_BATCH_SIZE,
      lockTtlS: env.TIMER_LOCK_TTL_S,
    });
    const outboxDispatcher = startOutboxDispatcher(getPrismaClient(), publisher, lock, {
      pollIntervalMs: env.OUTBOX_POLL_INTERVAL_MS,
      batchSize: env.OUTBOX_BATCH_SIZE,
      maxRetries: env.OUTBOX_MAX_RETRIES,
    });

    worker = {
      close: () => {
        timer.close();
        outboxDispatcher.close();
      },
    };
  }

  return { app, worker };
}

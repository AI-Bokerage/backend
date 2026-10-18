export { createGenSla } from "./create-gen-sla.ts";
export type { GenSlaConfig, GenSlaModulesConfig, GenSlaWorker, GenSlaInstance } from "./create-gen-sla.ts";

export type { ISlaPolicyRepo, SlaPolicyRecord, CreatePolicyParams, UpdatePolicyParams } from "./domain/ports/policy-repo.port.ts";
export type { ISlaInstanceRepo, SlaInstanceRecord, CreateInstanceParams } from "./domain/ports/instance-repo.port.ts";
export type { IMetricsRepo } from "./domain/ports/metrics-repo.port.ts";
export type { IDistributedLock } from "./domain/ports/distributed-lock.port.ts";
export type { IEventPublisher, EventEnvelope } from "./domain/ports/event-publisher.port.ts";
export type { IOutboxWriter, OutboxEnqueueParams } from "./domain/ports/outbox-writer.port.ts";

export { PrismaPolicyRepo } from "./modules/policies/v1/repo.ts";
export { PolicyService } from "./modules/policies/v1/service.ts";
export { PrismaInstanceRepo } from "./modules/instances/v1/repo.ts";
export { InstanceService } from "./modules/instances/v1/service.ts";
export { PrismaMetricsRepo } from "./modules/metrics/v1/repo.ts";
export { MetricsService } from "./modules/metrics/v1/service.ts";

export { ValkeyLock } from "./infra/cache/valkey-lock.ts";
export { RabbitMqBus } from "./infra/messaging/rabbitmq-bus.ts";
export { PrismaOutboxWriter } from "./infra/messaging/outbox-writer.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";

export { errorHandler } from "./middleware/error-handler.ts";
export {
  AppError,
  GenSlaConfigError,
  TenantIdInvalidError,
  SlaPolicyNotFoundError,
  SlaPolicyConflictError,
  SlaPolicyHasActiveInstancesError,
  SlaInvalidTimingError,
  SlaInstanceNotFoundError,
} from "./common/errors.ts";

export { SLA_EXCHANGE, SLA_ROUTING_KEYS, SLA_STATUSES } from "./config/constants.ts";

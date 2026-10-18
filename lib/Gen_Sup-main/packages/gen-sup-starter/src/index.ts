export { createGenSup } from "./create-gen-sup.ts";
export type { GenSupConfig, GenSupModulesConfig, GenSupInstance } from "./create-gen-sup.ts";

export type { TenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
export { noopTenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";

export type { DashboardKpis } from "./modules/dashboard/v1/types.ts";
export { DashboardService } from "./modules/dashboard/v1/service.ts";

export type { TntClientPort, TntTenantResponse, CreateTenantParams } from "./domain/ports/tnt-client.port.ts";
export { HttpTntClient, TntHttpError } from "./infra/external/http-tnt-client.ts";
export type { EventPublisher, EventEnvelope } from "./domain/ports/event-publisher.port.ts";
export { RabbitMqBus } from "./infra/messaging/rabbitmq-bus.ts";
export { PLATFORM_AUDIT_EXCHANGE, TENANT_ROUTING_KEYS } from "./config/constants.ts";
export type { TenantDto, CreateTenantResultDto, CreateTenantInput } from "./modules/tenants/v1/types.ts";
export { TenantsService } from "./modules/tenants/v1/service.ts";

export type { FmmClientPort, FmmFlagResponse, FmmFlagPatch, FmmOverrideResponse, FmmOverrideUpsertParams } from "./domain/ports/fmm-client.port.ts";
export { HttpFmmClient, FmmHttpError } from "./infra/external/http-fmm-client.ts";
export { FLAG_ROUTING_KEYS } from "./config/constants.ts";
export type { FeatureFlagDto, FeatureFlagOverrideDto, UpdateFlagInput, SetOverrideInput } from "./modules/feature-flags/v1/types.ts";
export { FeatureFlagsService } from "./modules/feature-flags/v1/service.ts";

export type { PrismaClient, Announcement, RevenueSnapshot } from "./infra/persistence/prisma-client.ts";
export { createPrismaClient } from "./infra/persistence/prisma-client.ts";
export { ANNOUNCEMENT_ROUTING_KEYS } from "./config/constants.ts";
export type { AnnouncementDto, CreateAnnouncementInput, ListAnnouncementsQuery, ListAnnouncementsResult, DispatchScheduledResult } from "./modules/announcements/v1/types.ts";
export { AnnouncementsService } from "./modules/announcements/v1/service.ts";

export type { UsgClientPort, UsgUsageSummary, UsgMeterSummary, UsgTrendPoint } from "./domain/ports/usg-client.port.ts";
export { HttpUsgClient, UsgHttpError } from "./infra/external/http-usg-client.ts";
export type {
  RevenueSnapshotDto,
  RevenueFilter,
  RevenueHistoryQuery,
  RevenueHistoryEntry,
  SnapshotPeriod,
  RevenueBreakdownByPlan,
  RevenueBreakdownByRegion,
  PlanDistributionEntry,
  TrialConversionStats,
  UsageAcrossTenantsResult,
} from "./modules/analytics/v1/types.ts";
export { AnalyticsService } from "./modules/analytics/v1/service.ts";

export { createValkeyClient } from "./infra/cache/valkey-client.ts";
export { errorHandler } from "./middleware/error-handler.ts";
export { internalSecret } from "./middleware/internal-secret.ts";
export {
  AppError,
  GenSupConfigError,
  TenantMetricsUnavailableError,
  TenantSlugTakenError,
  TenantNotFoundError,
  TenantTransitionConflictError,
  TntClientError,
  FlagNotFoundError,
  OverrideNotFoundError,
  FmmClientError,
  UsgClientError,
} from "./common/errors.ts";

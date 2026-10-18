import "express-async-errors";
import express, { type Express } from "express";
import helmet from "helmet";
import cors from "cors";
import type { Redis } from "ioredis";
import { env, requireEnv } from "./config/env.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { createValkeyClient } from "./infra/cache/valkey-client.ts";
import { noopTenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
import type { TenantMetricsPort } from "./domain/ports/tenant-metrics.port.ts";
import { DashboardService } from "./modules/dashboard/v1/service.ts";
import { createDashboardRouter } from "./modules/dashboard/v1/router.ts";
import type { TntClientPort } from "./domain/ports/tnt-client.port.ts";
import { HttpTntClient } from "./infra/external/http-tnt-client.ts";
import type { EventPublisher } from "./domain/ports/event-publisher.port.ts";
import { RabbitMqBus } from "./infra/messaging/rabbitmq-bus.ts";
import { TenantsService } from "./modules/tenants/v1/service.ts";
import { createTenantsRouter } from "./modules/tenants/v1/router.ts";
import type { FmmClientPort } from "./domain/ports/fmm-client.port.ts";
import { HttpFmmClient } from "./infra/external/http-fmm-client.ts";
import { FeatureFlagsService } from "./modules/feature-flags/v1/service.ts";
import { createFeatureFlagsRouter } from "./modules/feature-flags/v1/router.ts";
import { createPrismaClient, type PrismaClient } from "./infra/persistence/prisma-client.ts";
import { AnnouncementsService } from "./modules/announcements/v1/service.ts";
import { createAnnouncementsRouter } from "./modules/announcements/v1/router.ts";
import type { UsgClientPort } from "./domain/ports/usg-client.port.ts";
import { HttpUsgClient } from "./infra/external/http-usg-client.ts";
import { AnalyticsService } from "./modules/analytics/v1/service.ts";
import { createAnalyticsRouter } from "./modules/analytics/v1/router.ts";

export interface GenSupModulesConfig {
  dashboard?: boolean;
  tenants?: boolean;
  featureFlags?: boolean;
  announcements?: boolean;
  analytics?: boolean;
}

export interface GenSupConfig {
  tenantMetricsPort?: TenantMetricsPort;
  valkey?: Redis;
  valkeyUrl?: string;
  internalSecret?: string;
  tntClient?: TntClientPort;
  tntBaseUrl?: string;
  tntInternalSecret?: string;
  fmmClient?: FmmClientPort;
  fmmBaseUrl?: string;
  prisma?: PrismaClient;
  databaseUrl?: string;
  usgClient?: UsgClientPort;
  usgBaseUrl?: string;
  eventPublisher?: EventPublisher;
  rabbitMqUrl?: string;
  modules?: GenSupModulesConfig;
}

export interface GenSupInstance {
  app: Express;
  // Present only when dashboard or analytics is enabled (the two modules
  // that need a Valkey client) and no `valkey` override was supplied (an
  // override is the caller's own resource to manage). Exposed so a host can
  // close the connection on shutdown, e.g. `instance.valkey?.quit()`.
  valkey?: Redis;
  // Present only when at least one of tenants/featureFlags/announcements is
  // enabled and no eventPublisher override was supplied (an override is the
  // caller's own resource to manage). Exposed so a host can close the
  // RabbitMQ connection on shutdown, e.g. `instance.eventPublisher?.close()`.
  eventPublisher?: RabbitMqBus;
  // Present only when announcements or analytics is enabled and no prisma
  // override was supplied. Exposed so a host can close the connection on
  // shutdown, e.g. `instance.prisma?.$disconnect()`.
  prisma?: PrismaClient;
  // Present only when the announcements module is enabled. Exposed so a host
  // can call `instance.announcementsService?.dispatchScheduled()` on its own
  // interval/cron — Gen_SUP adds no internal scheduler.
  announcementsService?: AnnouncementsService;
  // Present only when the analytics module is enabled. Exposed so a host can
  // call `instance.analyticsService?.captureRevenueSnapshot(period)` on its
  // own interval/cron — Gen_SUP adds no internal scheduler for this either.
  analyticsService?: AnalyticsService;
}

function resolveInternalSecret(override: string | undefined): string {
  return override || requireEnv("GEN_SUP_INTERNAL_SECRET");
}

function resolveValkeyUrl(override: string | undefined): string {
  return override ?? requireEnv("VALKEY_URL");
}

function resolveTntClient(override: TntClientPort | undefined, baseUrlOverride: string | undefined, secretOverride: string | undefined): TntClientPort {
  if (override) return override;
  const baseUrl = baseUrlOverride ?? requireEnv("GEN_TNT_BASE_URL");
  const secret = secretOverride ?? requireEnv("GEN_TNT_INTERNAL_SECRET");
  return new HttpTntClient(baseUrl, secret);
}

function resolveFmmClient(override: FmmClientPort | undefined, baseUrlOverride: string | undefined): FmmClientPort {
  if (override) return override;
  const baseUrl = baseUrlOverride ?? requireEnv("GEN_FMM_BASE_URL");
  return new HttpFmmClient(baseUrl);
}

function resolveUsgClient(override: UsgClientPort | undefined, baseUrlOverride: string | undefined): UsgClientPort {
  if (override) return override;
  const baseUrl = baseUrlOverride ?? requireEnv("GEN_USG_BASE_URL");
  return new HttpUsgClient(baseUrl);
}

export function createGenSup(config: GenSupConfig): GenSupInstance {
  const modules: Required<GenSupModulesConfig> = {
    dashboard: config.modules?.dashboard ?? true,
    tenants: config.modules?.tenants ?? true,
    featureFlags: config.modules?.featureFlags ?? true,
    announcements: config.modules?.announcements ?? true,
    analytics: config.modules?.analytics ?? true,
  };

  const internalSecretValue = resolveInternalSecret(config.internalSecret);
  const tenantMetricsPort = config.tenantMetricsPort ?? noopTenantMetricsPort;

  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  let valkey: Redis | undefined;
  function resolveValkeyInstance(): Redis {
    if (config.valkey) return config.valkey;
    if (valkey) return valkey;
    const client = createValkeyClient(resolveValkeyUrl(config.valkeyUrl));
    valkey = client;
    return client;
  }

  if (modules.dashboard) {
    const valkeyClient = resolveValkeyInstance();
    const dashboardService = new DashboardService(tenantMetricsPort, valkeyClient, env.DASHBOARD_CACHE_TTL_SEC);
    app.use("/api/v1/dashboard", createDashboardRouter({ dashboardService, internalSecretValue }));
  }

  let eventPublisher: RabbitMqBus | undefined;
  let prismaInstance: PrismaClient | undefined;
  let announcementsService: AnnouncementsService | undefined;
  let analyticsService: AnalyticsService | undefined;

  function resolvePublisher(): EventPublisher {
    if (config.eventPublisher) return config.eventPublisher;
    if (eventPublisher) return eventPublisher;
    const bus = new RabbitMqBus(config.rabbitMqUrl ?? requireEnv("RABBITMQ_URL"));
    eventPublisher = bus;
    return bus;
  }

  function resolvePrisma(): PrismaClient {
    if (config.prisma) return config.prisma;
    if (prismaInstance) return prismaInstance;
    const databaseUrl = config.databaseUrl ?? requireEnv("DATABASE_URL");
    const client = createPrismaClient(databaseUrl);
    prismaInstance = client;
    return client;
  }

  if (modules.tenants) {
    const tntClient = resolveTntClient(config.tntClient, config.tntBaseUrl, config.tntInternalSecret);
    const publisher = resolvePublisher();
    const tenantsService = new TenantsService(tntClient, publisher);
    app.use("/api/v1/tenants", createTenantsRouter({ tenantsService, internalSecretValue }));
  }

  if (modules.featureFlags) {
    const fmmClient = resolveFmmClient(config.fmmClient, config.fmmBaseUrl);
    const publisher = resolvePublisher();
    const featureFlagsService = new FeatureFlagsService(fmmClient, publisher);
    app.use("/api/v1/feature-flags", createFeatureFlagsRouter({ featureFlagsService, internalSecretValue }));
  }

  if (modules.announcements) {
    const prisma = resolvePrisma();
    const publisher = resolvePublisher();
    announcementsService = new AnnouncementsService(prisma, publisher);
    app.use("/api/v1/announcements", createAnnouncementsRouter({ announcementsService, internalSecretValue }));
  }

  if (modules.analytics) {
    const valkeyClient = resolveValkeyInstance();
    const prisma = resolvePrisma();
    const usgClient = resolveUsgClient(config.usgClient, config.usgBaseUrl);
    analyticsService = new AnalyticsService(tenantMetricsPort, valkeyClient, env.ANALYTICS_CACHE_TTL_SEC, prisma, usgClient);
    app.use("/api/v1/analytics", createAnalyticsRouter({ analyticsService, internalSecretValue }));
  }

  app.use(errorHandler);

  return { app, valkey, eventPublisher, prisma: prismaInstance, announcementsService, analyticsService };
}

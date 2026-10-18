import express, { type Express } from "express";
import "express-async-errors";
import helmet from "helmet";
import cors from "cors";
import swaggerUi from "swagger-ui-express";
import type { NextFunction, Request, Response, RequestHandler } from "express";

import { requireEnv, optionalEnv, intEnv } from "./config/env.ts";
import { GenFmmConfigError } from "./common/errors.ts";
import { openApiSpec } from "./docs/openapi.ts";

import type { ICatalogRepo } from "./domain/ports/catalog.repository.port.ts";
import type { ITenantOverrideRepo } from "./domain/ports/tenant-override.repository.port.ts";
import type { ITelemetryRepo } from "./domain/ports/telemetry.repository.port.ts";
import type { ICacheStore } from "./domain/ports/cache-store.port.ts";

import { PrismaCatalogRepo } from "./modules/catalog/v1/repo.ts";
import { PrismaTenantOverrideRepo } from "./modules/overrides/v1/repo.ts";
import { PrismaTelemetryRepo } from "./modules/telemetry/v1/repo.ts";
import { RedisCacheStore } from "./infra/cache/redis-cache-store.ts";

import { CatalogService } from "./modules/catalog/v1/service.ts";
import { CatalogController } from "./modules/catalog/v1/controller.ts";
import { catalogRoutes } from "./modules/catalog/v1/routes.ts";
import { OverrideService } from "./modules/overrides/v1/service.ts";
import { OverrideController } from "./modules/overrides/v1/controller.ts";
import { overridesRoutes } from "./modules/overrides/v1/routes.ts";
import { TelemetryService } from "./modules/telemetry/v1/service.ts";
import { TelemetryController } from "./modules/telemetry/v1/controller.ts";
import { telemetryRoutes } from "./modules/telemetry/v1/routes.ts";
import { EntitlementCache } from "./modules/entitlement/v1/cache.ts";
import { EntitlementService } from "./modules/entitlement/v1/service.ts";
import { EntitlementController } from "./modules/entitlement/v1/controller.ts";
import { internalEntitlementRoutes, publicEntitlementRoutes } from "./modules/entitlement/v1/routes.ts";
import type { EntitlementCheckResult, BulkEntitlementResult } from "./modules/entitlement/v1/types.ts";

import { errorHandler } from "./middleware/error-handler.ts";
import { internalSecretMiddleware } from "./middleware/internal-secret.ts";
import { requireFeatureMiddleware } from "./middleware/require-feature.ts";

export interface GenFmmModulesConfig {
  catalog?: boolean;
  overrides?: boolean;
  telemetry?: boolean;
  entitlement?: boolean;
  check?: boolean;
}

export interface GenFmmConfig {
  catalogRepo?: ICatalogRepo;
  overrideRepo?: ITenantOverrideRepo;
  telemetryRepo?: ITelemetryRepo;
  cacheStore?: ICacheStore;
  onFlagChanged?: (flagKey: string, tenantId?: string) => void;
  modules?: GenFmmModulesConfig;
  internalSecret?: string;
}

export interface GenFmmInstance {
  app: Express;
  check(tenantId: string, flagKey: string, planCode?: string): Promise<EntitlementCheckResult>;
  bulk(tenantId: string, planCode?: string): Promise<BulkEntitlementResult>;
  flushTelemetryBuffer(): Promise<number>;
  requireFeature(featureKey: string): RequestHandler;
}

function resolveCatalogRepo(override: ICatalogRepo | undefined): ICatalogRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaCatalogRepo();
}

function resolveOverrideRepo(override: ITenantOverrideRepo | undefined): ITenantOverrideRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaTenantOverrideRepo();
}

function resolveTelemetryRepo(override: ITelemetryRepo | undefined): ITelemetryRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaTelemetryRepo();
}

function resolveCacheStore(override: ICacheStore | undefined): ICacheStore {
  if (override) return override;
  return new RedisCacheStore(requireEnv("REDIS_URL"));
}

export function createGenFmm(config: GenFmmConfig): GenFmmInstance {
  const modules: Required<GenFmmModulesConfig> = {
    catalog: config.modules?.catalog ?? true,
    overrides: config.modules?.overrides ?? true,
    telemetry: config.modules?.telemetry ?? true,
    entitlement: config.modules?.entitlement ?? true,
    check: config.modules?.check ?? true,
  };

  // Task 11 follow-up: OverrideController.listAll() is mounted whenever the
  // overrides module is enabled, and — unless the host supplies its own
  // ITenantOverrideRepo — that route is backed by the default
  // PrismaTenantOverrideRepo, whose listAll() dials the admin/RLS-bypass
  // connection gated on GEN_FMM_ADMIN_DATABASE_URL (see
  // infra/persistence/prisma-client.ts's getAdminPrismaClient()). That check
  // is intentionally lazy (fires on first listAll() call, not at boot) so it
  // can't be validated here for a host-supplied repo — a custom repo may not
  // use that env var at all. But for the default-resolved repo, validate
  // eagerly here so a missing var fails at createGenFmm() call time, never
  // mid-request, matching every other GenFmmConfigError in this factory.
  if (modules.overrides && !config.overrideRepo) {
    requireEnv("GEN_FMM_ADMIN_DATABASE_URL");
  }

  const catalogRepo = resolveCatalogRepo(config.catalogRepo);
  const overrideRepo = resolveOverrideRepo(config.overrideRepo);
  const telemetryRepo = resolveTelemetryRepo(config.telemetryRepo);
  const cacheStore = resolveCacheStore(config.cacheStore);
  const onFlagChanged = config.onFlagChanged ?? (() => {});

  const internalSecret = config.internalSecret ?? optionalEnv("GEN_FMM_INTERNAL_SECRET", "");
  if (modules.check && internalSecret === "") {
    throw new GenFmmConfigError(
      "modules.check is enabled but no internal secret was configured — set GEN_FMM_INTERNAL_SECRET or pass config.internalSecret",
    );
  }

  const checkTtlSeconds = intEnv("CHECK_CACHE_TTL_SECS", 60);
  const cache = new EntitlementCache(
    cacheStore,
    intEnv("L1_CACHE_MAX_ENTRIES", 10000),
    checkTtlSeconds * 1000,
    checkTtlSeconds,
  );

  const telemetryService = new TelemetryService(telemetryRepo, intEnv("TELEMETRY_BUFFER_MAX", 500));
  const catalogService = new CatalogService(catalogRepo, cache, onFlagChanged);
  const overrideService = new OverrideService(overrideRepo, cache, onFlagChanged);
  const entitlementService = new EntitlementService(
    catalogRepo,
    overrideRepo,
    cache,
    (tenantId, flagKey, enabled, reason, planCode) => telemetryService.record(tenantId, flagKey, enabled, reason, planCode),
  );

  const app = express();
  app.use(helmet());
  const allowedOrigins = optionalEnv("ALLOWED_ORIGINS", "*");
  app.use(cors({ origin: allowedOrigins === "*" ? true : allowedOrigins.split(",") }));
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));
  app.get("/docs.json", (_req, res) => res.json(openApiSpec));
  app.use(
    "/docs",
    (_req: Request, res: Response, next: NextFunction) => { res.removeHeader("Content-Security-Policy"); next(); },
    swaggerUi.serve,
    swaggerUi.setup(openApiSpec),
  );

  if (modules.catalog) {
    app.use("/api/v1/catalog", catalogRoutes(new CatalogController(catalogService)));
  }
  if (modules.overrides) {
    app.use("/api/v1/overrides", overridesRoutes(new OverrideController(overrideService)));
  }
  if (modules.telemetry) {
    app.use("/api/v1/telemetry", telemetryRoutes(new TelemetryController(telemetryService)));
  }
  if (modules.entitlement) {
    app.use("/api/v1", publicEntitlementRoutes(new EntitlementController(entitlementService)));
  }
  if (modules.check) {
    const gate = internalSecretMiddleware(internalSecret);
    app.use("/internal/v1/fmm", gate, internalEntitlementRoutes(new EntitlementController(entitlementService)));
  }

  app.use(errorHandler);

  // ponytail: Express's error-middleware dispatch walks the route stack by a
  // monotonically increasing index — `app.use(errorHandler)` above only
  // catches errors from routes registered BEFORE it. requireFeature() is
  // designed to be dropped into routes the HOST adds to `genFmm.app` after
  // createGenFmm() returns (see the factory test), which register after
  // errorHandler and so would otherwise fall through to Express's default
  // HTML error page. Overriding app.handle intercepts the final
  // done(err)/done() callback express.js always calls once nothing else in
  // the stack matched, regardless of registration order, so late-added
  // routes still get the same JSON error shape.
  type ExpressHandle = (req: Request, res: Response, callback?: (err?: unknown) => void) => void;
  const appWithHandle = app as unknown as { handle: ExpressHandle };
  const originalHandle = appWithHandle.handle.bind(app);
  appWithHandle.handle = (req, res, callback) => {
    originalHandle(req, res, (err?: unknown) => {
      if (err) {
        if (callback) {
          // A real callback here means this `app` is mounted as a sub-app
          // (e.g. parentApp.use('/fmm', genFmm.app)) — Express calls
          // genFmmApp.handle(req, res, next) with the PARENT's next as this
          // callback. Forward the error unchanged so the parent's own error
          // middleware/logging sees it, instead of self-handling it here.
          callback(err);
          return;
        }
        errorHandler(err, req, res, () => {});
        return;
      }
      if (callback) {
        callback();
        return;
      }
      // No callback means this `app` is the root dispatcher (host added
      // routes directly onto genFmm.app, no wrapping parent). Note this
      // branch also replaces Express's default 404 behavior for the ENTIRE
      // app, not just host-added routes: any unmatched path anywhere gets
      // this JSON 404 instead of Express's default HTML page — intentional
      // for a JSON API library, called out explicitly here.
      res.status(404).json({ error: "NOT_FOUND", message: "Not found" });
    });
  };

  return {
    app,
    check: (tenantId, flagKey, planCode) => entitlementService.check(tenantId, flagKey, planCode),
    bulk: (tenantId, planCode) => entitlementService.bulk(tenantId, planCode),
    flushTelemetryBuffer: () => telemetryService.flush(),
    requireFeature: (featureKey: string) => requireFeatureMiddleware(featureKey, entitlementService),
  };
}

import { Router } from "express";
import { makeMetricsController } from "./controller.ts";
import type { MetricsService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";
import { validateTenantId } from "../../../middleware/validate-tenant-id.ts";

export interface MetricsRouterDeps {
  metricsService: MetricsService;
  internalSecretValue: string;
}

export function createMetricsRouter(deps: MetricsRouterDeps): Router {
  const router = Router({ mergeParams: true });
  const controller = makeMetricsController(deps.metricsService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret, validateTenantId);

  router.get("/summary", (req, res, next) => controller.getSummary(req, res).catch(next));
  router.get("/compliance", (req, res, next) => controller.getComplianceRates(req, res).catch(next));
  router.get("/breaches", (req, res, next) => controller.getBreaches(req, res).catch(next));
  router.get("/trends", (req, res, next) => controller.getTrends(req, res).catch(next));

  return router;
}

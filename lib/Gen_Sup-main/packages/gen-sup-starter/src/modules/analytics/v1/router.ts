import { Router } from "express";
import { makeAnalyticsController } from "./controller.ts";
import type { AnalyticsService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";

export interface AnalyticsRouterDeps {
  analyticsService: AnalyticsService;
  internalSecretValue: string;
}

export function createAnalyticsRouter(deps: AnalyticsRouterDeps): Router {
  const router = Router();
  const controller = makeAnalyticsController(deps.analyticsService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret);

  router.get("/revenue", (req, res, next) => controller.getRevenue(req, res).catch(next));
  router.get("/revenue/history", (req, res, next) => controller.getRevenueHistory(req, res).catch(next));
  router.get("/usage", (req, res, next) => controller.getUsage(req, res).catch(next));

  return router;
}

import { Router } from "express";
import { makeDashboardController } from "./controller.ts";
import type { DashboardService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";

export interface DashboardRouterDeps {
  dashboardService: DashboardService;
  internalSecretValue: string;
}

export function createDashboardRouter(deps: DashboardRouterDeps): Router {
  const router = Router();
  const controller = makeDashboardController(deps.dashboardService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret);
  router.get("/kpis", (req, res, next) => controller.getKpis(req, res).catch(next));

  return router;
}

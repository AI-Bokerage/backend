import { Router } from "express";
import { makeFeatureFlagsController } from "./controller.ts";
import type { FeatureFlagsService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";

export interface FeatureFlagsRouterDeps {
  featureFlagsService: FeatureFlagsService;
  internalSecretValue: string;
}

export function createFeatureFlagsRouter(deps: FeatureFlagsRouterDeps): Router {
  const router = Router();
  const controller = makeFeatureFlagsController(deps.featureFlagsService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret);

  router.get("/", (req, res, next) => controller.listFlags(req, res).catch(next));
  router.patch("/:key", (req, res, next) => controller.updateFlag(req, res).catch(next));
  router.put("/overrides/:tenantId/:flagKey", (req, res, next) => controller.setOverride(req, res).catch(next));
  router.get("/overrides/:tenantId", (req, res, next) => controller.listOverridesForTenant(req, res).catch(next));
  router.delete("/overrides/:tenantId/:flagKey", (req, res, next) => controller.clearOverride(req, res).catch(next));

  return router;
}

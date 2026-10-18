import { Router } from "express";
import { makeInstanceController } from "./controller.ts";
import type { InstanceService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";
import { validateTenantId } from "../../../middleware/validate-tenant-id.ts";

export interface InstancesRouterDeps {
  instanceService: InstanceService;
  internalSecretValue: string;
}

export function createInstancesRouter(deps: InstancesRouterDeps): Router {
  const router = Router({ mergeParams: true });
  const controller = makeInstanceController(deps.instanceService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret, validateTenantId);

  router.get("/", (req, res, next) => controller.listInstances(req, res).catch(next));
  router.get("/:id", (req, res, next) => controller.getInstance(req, res).catch(next));

  return router;
}

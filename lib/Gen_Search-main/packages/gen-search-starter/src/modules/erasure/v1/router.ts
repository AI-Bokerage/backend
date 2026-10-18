import { Router } from "express";
import { makeErasureController } from "./controller.ts";
import type { ErasureService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";
import { validateTenantId } from "../../../middleware/validate-tenant-id.ts";

export interface ErasureRouterDeps {
  erasureService: ErasureService;
  internalSecretValue: string;
}

export function createErasureRouter(deps: ErasureRouterDeps): Router {
  const router = Router({ mergeParams: true });
  const controller = makeErasureController(deps.erasureService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret, validateTenantId);
  router.post("/", (req, res, next) => controller.removeUserDocuments(req, res).catch(next));

  return router;
}

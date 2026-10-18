import { Router } from "express";
import { makePolicyController } from "./controller.ts";
import type { PolicyService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";
import { validateTenantId } from "../../../middleware/validate-tenant-id.ts";

export interface PoliciesRouterDeps {
  policyService: PolicyService;
  internalSecretValue: string;
}

export function createPoliciesRouter(deps: PoliciesRouterDeps): Router {
  const router = Router({ mergeParams: true });
  const controller = makePolicyController(deps.policyService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret, validateTenantId);

  router.post("/", (req, res, next) => controller.createPolicy(req, res).catch(next));
  router.get("/", (req, res, next) => controller.listPolicies(req, res).catch(next));
  router.get("/:id", (req, res, next) => controller.getPolicy(req, res).catch(next));
  router.patch("/:id", (req, res, next) => controller.updatePolicy(req, res).catch(next));
  router.delete("/:id", (req, res, next) => controller.deletePolicy(req, res).catch(next));

  return router;
}

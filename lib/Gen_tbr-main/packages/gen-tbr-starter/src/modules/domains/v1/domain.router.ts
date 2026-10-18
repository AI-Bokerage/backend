import { Router } from "express";
import { makeDomainController } from "./domain.controller.ts";
import type { DomainService } from "./domain.service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";

export interface DomainsRouterDeps {
  domainService: DomainService;
  internalSecretValue: string;
}

export function createDomainsRouter(deps: DomainsRouterDeps): Router {
  const router = Router();
  const controller = makeDomainController(deps.domainService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.post("/", requireSecret, (req, res, next) => controller.create(req, res).catch(next));
  router.get("/", requireSecret, (req, res, next) => controller.list(req, res).catch(next));
  router.post("/:id/verify", requireSecret, (req, res, next) => controller.verify(req, res).catch(next));
  router.post("/:id/activate", requireSecret, (req, res, next) => controller.activate(req, res).catch(next));
  router.post("/:id/detach", requireSecret, (req, res, next) => controller.detach(req, res).catch(next));
  router.post("/:id/primary", requireSecret, (req, res, next) => controller.primary(req, res).catch(next));

  return router;
}

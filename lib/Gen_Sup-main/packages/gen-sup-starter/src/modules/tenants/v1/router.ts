import { Router } from "express";
import { makeTenantsController } from "./controller.ts";
import type { TenantsService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";

export interface TenantsRouterDeps {
  tenantsService: TenantsService;
  internalSecretValue: string;
}

export function createTenantsRouter(deps: TenantsRouterDeps): Router {
  const router = Router();
  const controller = makeTenantsController(deps.tenantsService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret);

  router.post("/", (req, res, next) => controller.create(req, res).catch(next));
  router.get("/by-slug/:slug", (req, res, next) => controller.getBySlug(req, res).catch(next));
  router.get("/:id", (req, res, next) => controller.getById(req, res).catch(next));
  router.patch("/:id/suspend", (req, res, next) => controller.suspend(req, res).catch(next));
  router.patch("/:id/reactivate", (req, res, next) => controller.reactivate(req, res).catch(next));

  return router;
}

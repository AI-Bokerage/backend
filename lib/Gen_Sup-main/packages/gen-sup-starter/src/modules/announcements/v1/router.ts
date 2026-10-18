import { Router } from "express";
import { makeAnnouncementsController } from "./controller.ts";
import type { AnnouncementsService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";

export interface AnnouncementsRouterDeps {
  announcementsService: AnnouncementsService;
  internalSecretValue: string;
}

export function createAnnouncementsRouter(deps: AnnouncementsRouterDeps): Router {
  const router = Router();
  const controller = makeAnnouncementsController(deps.announcementsService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret);

  router.post("/", (req, res, next) => controller.create(req, res).catch(next));
  router.get("/", (req, res, next) => controller.list(req, res).catch(next));

  return router;
}

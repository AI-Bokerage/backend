import { Router } from "express";
import type { WebhookEndpointController } from "./controller.ts";

export function webhooksRoutes(controller: WebhookEndpointController): Router {
  const router = Router();
  router.get("/", controller.list);
  router.post("/", controller.create);
  router.patch("/:id", controller.update);
  router.delete("/:id", controller.remove);
  return router;
}

// src/modules/webhooks/v1/routes.ts
import { Router } from "express";
import type { WebhooksController } from "./controller.ts";

export function webhooksRoutes(controller: WebhooksController): Router {
  const router = Router();
  router.post("/signup/webhooks/:provider", controller.handleWebhook);
  return router;
}

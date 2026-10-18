import { Router } from "express";
import type { TelemetryController } from "./controller.ts";

export function telemetryRoutes(controller: TelemetryController): Router {
  const router = Router();
  router.get("/:tenantId", controller.query);
  return router;
}

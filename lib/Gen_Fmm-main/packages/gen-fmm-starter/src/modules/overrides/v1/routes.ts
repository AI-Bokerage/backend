import { Router } from "express";
import type { OverrideController } from "./controller.ts";

export function overridesRoutes(controller: OverrideController): Router {
  const router = Router();
  router.post("/", controller.upsert);
  router.get("/", controller.listAll);
  router.get("/:tenantId", controller.listForTenant);
  router.get("/:tenantId/:flagKey", controller.findOne);
  router.delete("/:tenantId/:flagKey", controller.delete);
  return router;
}

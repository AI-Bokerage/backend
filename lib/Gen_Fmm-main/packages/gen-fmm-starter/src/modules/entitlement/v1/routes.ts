import { Router } from "express";
import type { EntitlementController } from "./controller.ts";

export function internalEntitlementRoutes(controller: EntitlementController): Router {
  const router = Router();
  router.get("/check/:tenantId/:flagKey", controller.check);
  router.get("/bulk/:tenantId", controller.bulk);
  return router;
}

export function publicEntitlementRoutes(controller: EntitlementController): Router {
  const router = Router();
  router.get("/entitlement/:tenantId/:flagKey", controller.check);
  router.get("/entitlements/:tenantId", controller.bulk);
  return router;
}

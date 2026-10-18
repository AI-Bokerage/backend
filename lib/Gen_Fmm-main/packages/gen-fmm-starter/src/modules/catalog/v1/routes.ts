import { Router } from "express";
import type { CatalogController } from "./controller.ts";

export function catalogRoutes(controller: CatalogController): Router {
  const router = Router();
  router.post("/modules", controller.createModule);
  router.get("/modules", controller.listModules);
  router.patch("/modules/:code", controller.updateModule);

  router.post("/flags", controller.createFlag);
  router.get("/flags", controller.listFlags);
  router.patch("/flags/:key", controller.updateFlag);
  router.delete("/flags/:key", controller.deleteFlag);

  router.put("/plan-modules", controller.upsertPlanModule);
  router.get("/plans/:planCode/modules", controller.listPlanModules);
  router.delete("/plans/:planCode/modules/:moduleCode", controller.deletePlanModule);

  return router;
}

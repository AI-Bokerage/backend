// src/modules/select-plan/v1/routes.ts
import { Router } from "express";
import type { SelectPlanController } from "./controller.ts";

export function selectPlanRoutes(controller: SelectPlanController): Router {
  const router = Router();
  router.post("/signup/select-plan", controller.selectPlan);
  return router;
}

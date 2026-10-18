import { Router } from "express";
import { PreferenceController } from "./controller.ts";

export function preferencesRoutes(controller: PreferenceController): Router {
  const router = Router();
  router.get("/", controller.list);
  router.patch("/", controller.upsert);
  router.get("/event-types", controller.eventTypes);
  return router;
}

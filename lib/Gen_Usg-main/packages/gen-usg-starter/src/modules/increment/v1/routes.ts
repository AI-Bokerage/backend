import { Router } from "express";
import type { IncrementController } from "./controller.ts";

export function incrementRoutes(controller: IncrementController): Router {
  const router = Router();
  router.post("/", controller.increment);
  return router;
}

import { Router } from "express";
import type { CheckController } from "./controller.ts";

export function checkRoutes(controller: CheckController): Router {
  const router = Router();
  router.post("/", controller.check);
  return router;
}

import { Router } from "express";
import type { DigestController } from "./controller.ts";

export function digestRoutes(controller: DigestController): Router {
  const router = Router();
  router.post("/sweep", controller.sweep);
  return router;
}

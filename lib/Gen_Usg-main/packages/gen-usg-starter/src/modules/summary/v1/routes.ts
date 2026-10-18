import { Router } from "express";
import type { SummaryController } from "./controller.ts";

export function summaryRoutes(controller: SummaryController): Router {
  const router = Router();
  router.get("/", controller.getSummary);
  return router;
}

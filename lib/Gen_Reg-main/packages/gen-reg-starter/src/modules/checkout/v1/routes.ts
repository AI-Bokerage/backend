import { Router } from "express";
import type { CheckoutController } from "./controller.ts";

export function checkoutRoutes(controller: CheckoutController): Router {
  const router = Router();
  router.post("/signup/checkout", controller.createSession);
  return router;
}

// src/modules/verify-email/v1/routes.ts
import { Router } from "express";
import type { VerifyEmailController } from "./controller.ts";

export function verifyEmailRoutes(controller: VerifyEmailController): Router {
  const router = Router();
  router.get("/signup/verify-email", controller.verifyEmail);
  return router;
}

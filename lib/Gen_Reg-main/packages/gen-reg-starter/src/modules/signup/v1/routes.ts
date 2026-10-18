// src/modules/signup/v1/routes.ts
import { Router, type RequestHandler } from "express";
import type { SignupController } from "./controller.ts";

export function signupRoutes(controller: SignupController, captchaMiddleware?: RequestHandler): Router {
  const router = Router();
  router.post("/signup", ...(captchaMiddleware ? [captchaMiddleware] : []), controller.startSignup);
  router.get("/signup/check-subdomain", controller.checkSubdomain);
  router.get("/signup/resume", controller.resumeSignup);
  return router;
}

import type { NextFunction, Request, Response } from "express";
import { AppError } from "../common/errors.ts";
import type { EntitlementService } from "../modules/entitlement/v1/service.ts";

declare module "express-serve-static-core" {
  interface Request {
    tenantId?: string;
  }
}

export function requireFeatureMiddleware(featureKey: string, entitlementService: EntitlementService) {
  return async (req: Request, _res: Response, next: NextFunction): Promise<void> => {
    const tenantId = req.tenantId;
    if (!tenantId) {
      next(new AppError(401, "UNAUTHORIZED", "req.tenantId must be set by the host before requireFeature runs"));
      return;
    }
    // ponytail: no planCode plumbed through here (smaller diff than adding a
    // req.planCode convention symmetric with req.tenantId) — requireFeature()
    // resolves override/default/rollout tiers only; plan-entitlement never
    // applies for this call path. Use genFmm.check(tenantId, key, planCode)
    // directly if a route needs the plan tier.
    const result = await entitlementService.check(tenantId, featureKey);
    if (!result.enabled) {
      next(new AppError(403, "FEATURE_DISABLED", `Feature "${featureKey}" is not enabled for this tenant`));
      return;
    }
    next();
  };
}

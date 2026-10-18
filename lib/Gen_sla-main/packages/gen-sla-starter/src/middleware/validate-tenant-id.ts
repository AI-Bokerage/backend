import type { Request, Response, NextFunction } from "express";
import { z } from "zod";
import { TenantIdInvalidError } from "../common/errors.ts";

const uuidSchema = z.string().uuid();

export function validateTenantId(req: Request, _res: Response, next: NextFunction): void {
  const result = uuidSchema.safeParse(req.params.tenantId);
  if (!result.success) {
    next(new TenantIdInvalidError(req.params.tenantId));
    return;
  }
  next();
}

import type { NextFunction, Request, Response } from "express";
import { AppError } from "../common/errors.ts";

export function internalSecretMiddleware(secret: string) {
  return (req: Request, _res: Response, next: NextFunction): void => {
    if (req.header("x-internal-secret") !== secret) {
      throw new AppError(401, "UNAUTHORIZED", "Missing or invalid X-Internal-Secret header");
    }
    next();
  };
}

import type { Request, Response, NextFunction } from "express";
import { AppError } from "../common/errors.ts";

class UnauthorizedError extends AppError {
  constructor() {
    super(401, "UNAUTHORIZED", "Missing or invalid X-Internal-Secret header");
  }
}

export function internalSecret(secret: string) {
  return (req: Request, _res: Response, next: NextFunction): void => {
    if (req.header("X-Internal-Secret") !== secret) {
      next(new UnauthorizedError());
      return;
    }
    next();
  };
}

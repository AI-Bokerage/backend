import type { Request, Response, NextFunction } from "express";
import { timingSafeEqual } from "node:crypto";
import { AppError } from "../common/errors.ts";

class UnauthorizedError extends AppError {
  constructor() {
    super(401, "UNAUTHORIZED", "Missing or invalid X-Internal-Secret header");
  }
}

export function internalSecret(secret: string) {
  const expected = Buffer.from(secret);

  return (req: Request, _res: Response, next: NextFunction): void => {
    const header = req.header("X-Internal-Secret");
    const provided = header ? Buffer.from(header) : undefined;

    // timingSafeEqual throws on mismatched lengths, so a missing header or a
    // length mismatch is rejected up front rather than passed to it.
    if (!provided || provided.length !== expected.length || !timingSafeEqual(provided, expected)) {
      next(new UnauthorizedError());
      return;
    }
    next();
  };
}

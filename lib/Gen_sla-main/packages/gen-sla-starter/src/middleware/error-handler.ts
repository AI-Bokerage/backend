import type { NextFunction, Request, Response } from "express";
import { ZodError } from "zod";
import { AppError } from "../common/errors.ts";
import { logger } from "../common/logger.ts";

export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction): void {
  if (err instanceof AppError) {
    res.status(err.statusCode).json({ error: err.code, message: err.message });
    return;
  }

  // Prisma's PrismaClientKnownRequestError always carries a string `.code`; duck-type
  // rather than importing the class to keep this middleware decoupled from Prisma.
  if (err && typeof err === "object" && (err as { code?: unknown }).code === "P2002") {
    res.status(409).json({ error: "CONFLICT", message: "A record with the same unique key already exists" });
    return;
  }

  if (err instanceof ZodError) {
    res.status(400).json({ error: "VALIDATION_ERROR", message: err.issues.map((i) => i.message).join("; ") });
    return;
  }

  logger.error({ err }, "Unhandled error");
  res.status(500).json({ error: "internal_error", message: "An unexpected error occurred" });
}

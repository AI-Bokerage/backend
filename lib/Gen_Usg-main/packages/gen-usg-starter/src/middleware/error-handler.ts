import type { NextFunction, Request, Response } from "express";
import { ZodError } from "zod";
import { AppError } from "../common/errors.ts";
import { logger } from "../common/logger.ts";

export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction): void {
  if (err instanceof AppError) {
    res.status(err.statusCode).json({ error: err.code, message: err.message });
    return;
  }
  if (err instanceof ZodError) {
    const message = err.errors.map((e) => `${e.path.join(".")}: ${e.message}`).join("; ");
    res.status(400).json({ error: "VALIDATION_ERROR", message });
    return;
  }
  logger.error({ err }, "[gen-usg] unhandled error");
  res.status(500).json({ error: "INTERNAL_ERROR", message: "An unexpected error occurred" });
}

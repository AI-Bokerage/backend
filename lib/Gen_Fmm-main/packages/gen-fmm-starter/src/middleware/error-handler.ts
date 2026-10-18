import type { NextFunction, Request, Response } from "express";
import { ZodError } from "zod";
import { Prisma } from "@prisma/client";
import { AppError } from "../common/errors.ts";
import { logger } from "../common/logger.ts";

function isFkViolation(err: unknown): err is Prisma.PrismaClientKnownRequestError {
  return err instanceof Prisma.PrismaClientKnownRequestError && err.code === "P2003";
}

// express.json() throws this SyntaxError (with a non-standard `type` property)
// when the request body isn't valid JSON — a client error, not a server bug.
function isMalformedJsonBody(err: unknown): boolean {
  return err instanceof SyntaxError && (err as SyntaxError & { type?: string }).type === "entity.parse.failed";
}

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
  // Same remapping pattern catalog/v1/service.ts already applies for P2002
  // (unique violations), moved here so every write path benefits, not just
  // catalog's own pre-checked ones — e.g. POST /api/v1/overrides with a
  // flagKey that doesn't exist in feature_flag.
  if (isFkViolation(err)) {
    res.status(422).json({ error: "REFERENCED_RECORD_NOT_FOUND", message: "One or more referenced records do not exist" });
    return;
  }
  if (isMalformedJsonBody(err)) {
    res.status(400).json({ error: "INVALID_JSON", message: "Request body is not valid JSON" });
    return;
  }
  logger.error({ err }, "[gen-fmm] unhandled error");
  res.status(500).json({ error: "INTERNAL_ERROR", message: "An unexpected error occurred" });
}

import type { NextFunction, Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import { env } from '../config/env';
import { logger } from '../config/logger';
import { ApiError } from '../utils/api-error';

/**
 * Express's own body parsers (JSON, raw uploads) reject bad input with an
 * http-errors 4xx marked `expose` — e.g. malformed JSON (400) or an upload
 * over the size limit (413). Those are the caller's mistake, not a 500.
 */
function clientError(error: unknown): ApiError | null {
  const e = error as { expose?: boolean; status?: number; type?: string; message?: string };
  if (!e?.expose || !e.status || e.status < 400 || e.status >= 500) return null;
  const message =
    e.type === 'entity.parse.failed'
      ? 'Request body is not valid JSON'
      : e.type === 'entity.too.large'
        ? 'Request body is too large'
        : (e.message ?? 'Bad request');
  return new ApiError(message, e.status);
}

export const errorHandlerMiddleware = (
  error: unknown,
  req: Request,
  res: Response,
  next: NextFunction,
): void => {
  void next;
  const normalizedError =
    error instanceof ApiError
      ? error
      : (clientError(error) ??
        new ApiError('Internal server error', StatusCodes.INTERNAL_SERVER_ERROR, false));

  logger.error(
    {
      requestId: req.requestId,
      error,
      normalizedError,
    },
    'Unhandled error captured',
  );

  res.status(normalizedError.statusCode).json({
    success: false,
    message: normalizedError.message,
    ...normalizedError.details,
    requestId: req.requestId,
    ...(env.nodeEnv !== 'production' && { stack: (error as Error)?.stack }),
  });
};

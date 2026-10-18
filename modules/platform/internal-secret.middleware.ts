import type { NextFunction, Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import { env } from '../../config/env';
import { ApiError } from '../../utils/api-error';

/** Same shared-secret convention every Gen_MS service in this stack uses for its own /internal/** routes. */
export function requireInternalSecret(req: Request, _res: Response, next: NextFunction): void {
  const provided = req.headers['x-internal-secret'];
  if (provided !== env.platform.internalSecret) {
    throw new ApiError('Unauthorized', StatusCodes.UNAUTHORIZED);
  }
  next();
}

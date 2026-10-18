import type { NextFunction, Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import { ApiError } from '../utils/api-error';

export const notFoundMiddleware = (req: Request, _res: Response, next: NextFunction): void => {
  next(new ApiError(`Route not found: ${req.method} ${req.originalUrl}`, StatusCodes.NOT_FOUND));
};

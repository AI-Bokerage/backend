import type { NextFunction, Request, Response } from 'express';
import crypto from 'node:crypto';

export const requestContextMiddleware = (
  req: Request,
  _res: Response,
  next: NextFunction,
): void => {
  req.requestId = req.headers['x-request-id']?.toString() ?? crypto.randomUUID();
  next();
};

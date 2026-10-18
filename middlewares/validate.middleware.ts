import type { NextFunction, Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import type { AnyZodObject, ZodError } from 'zod';

import { ApiError } from '../utils/api-error';

export const validate =
  (schema: AnyZodObject) =>
  (req: Request, _res: Response, next: NextFunction): void => {
    try {
      const parsed = schema.parse({
        body: req.body,
        query: req.query,
        params: req.params,
      });

      // Without this, a schema's defaults/coercions (e.g. listBrokeragesSchema's
      // page=0/size=20) are computed and then thrown away — confirmed by an
      // actual test: GET /brokerages with no query string called
      // listBrokerages(undefined, undefined) instead of (0, 20).
      if (parsed.body !== undefined) req.body = parsed.body;
      if (parsed.query !== undefined) req.query = parsed.query;
      if (parsed.params !== undefined) req.params = parsed.params;

      next();
    } catch (error) {
      const zodError = error as ZodError;
      const issueMessage = zodError.issues
        .map((issue) => `${issue.path.join('.')}: ${issue.message}`)
        .join('; ');
      next(new ApiError(`Validation failed - ${issueMessage}`, StatusCodes.BAD_REQUEST));
    }
  };

import type { Request } from 'express';
import pinoHttp from 'pino-http';

import { logger } from '../config/logger';

export const requestLoggerMiddleware = pinoHttp({
  logger,
  customProps: (req) => ({ requestId: (req as Request).requestId }),
  customLogLevel: (_req, res, err) => {
    if (err || res.statusCode >= 500) return 'error';
    if (res.statusCode >= 400) return 'warn';
    return 'info';
  },
});

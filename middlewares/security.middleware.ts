import cors from 'cors';
import express, { type Express } from 'express';
import mongoSanitize from 'express-mongo-sanitize';
import expressRateLimit from 'express-rate-limit';
import helmet from 'helmet';
import hpp from 'hpp';

import { env } from '../config/env';

export const applySecurityMiddlewares = (app: Express): void => {
  app.disable('x-powered-by');

  app.use(
    cors({
      origin: env.allowedOrigins,
      credentials: true,
    }),
  );

  app.use(helmet());
  app.use(hpp());

  // Defends against NoSQL injection payloads.
  app.use(mongoSanitize());

  // Built-in body parser with strict payload limits.
  app.use(
    express.json({
      limit: '1mb',
      verify: (req, _res, buf) => {
        (req as express.Request).rawBody = buf;
      },
    }),
  );
  app.use(express.urlencoded({ extended: true, limit: '1mb' }));

  app.use(
    expressRateLimit({
      windowMs: 15 * 60 * 1000,
      max: 300,
      standardHeaders: true,
      legacyHeaders: false,
      message: {
        success: false,
        message: 'Too many requests, please try again later.',
      },
    }),
  );
};

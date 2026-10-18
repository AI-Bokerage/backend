import cookieParser from 'cookie-parser';
import express from 'express';
import swaggerUi from 'swagger-ui-express';

import { env } from './config/env';
import { buildSwaggerSpec } from './config/swagger';
import { errorHandlerMiddleware } from './middlewares/error-handler.middleware';
import { notFoundMiddleware } from './middlewares/not-found.middleware';
import { requestContextMiddleware } from './middlewares/request-context.middleware';
import { requestLoggerMiddleware } from './middlewares/request-logger.middleware';
import { applySecurityMiddlewares } from './middlewares/security.middleware';
import { rootRouter } from './routes';

const app = express();
const swaggerSpec = buildSwaggerSpec();

applySecurityMiddlewares(app);
app.use(cookieParser());
app.use(requestContextMiddleware);
app.use(requestLoggerMiddleware);

app.get('/health', (_req, res) => {
  res.status(200).json({ status: 'ok', app: env.appName, env: env.nodeEnv });
});

app.use('/docs', swaggerUi.serve, swaggerUi.setup(swaggerSpec));
app.use(env.apiPrefix, rootRouter);

app.use(notFoundMiddleware);
app.use(errorHandlerMiddleware);

export { app };

import pino from 'pino';

import { env } from './env';

export const logger = pino({
  level: env.logLevel,
  // Code logs errors as `{ error }`; pino only serializes `err` by default, so these printed `{}`.
  serializers: { error: pino.stdSerializers.err },
  transport:
    env.nodeEnv === 'development'
      ? {
          target: 'pino-pretty',
          options: {
            colorize: true,
            translateTime: 'SYS:standard',
          },
        }
      : undefined,
});

import type { CrmUser } from '../../modules/crm/crm-auth.middleware';
import type { PlatformUser } from '../../modules/platform/platform.types';
import type { AuthUser } from '../common';

declare global {
  namespace Express {
    interface Request {
      user?: AuthUser;
      requestId?: string;
      /** Set by modules/platform/auth-jwt.middleware.ts after verifying a SUPER_ADMIN JWT. */
      platformUser?: PlatformUser;
      /** Set by modules/crm/crm-auth.middleware.ts after resolving the caller's modauth Role. */
      crmUser?: CrmUser;
      /** The exact JSON bytes received — AI service callbacks are signed over them. */
      rawBody?: Buffer;
    }
  }
}

export {};

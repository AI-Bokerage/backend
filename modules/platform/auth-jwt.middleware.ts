import type { NextFunction, Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';
import jwt from 'jsonwebtoken';

import type { PlatformUser } from './platform.types';
import { env } from '../../config/env';
import { ApiError } from '../../utils/api-error';

/**
 * Verifies the RS256 JWT issued by modules/auth (gen-auth-starter) directly
 * against its public key — no network call back to the auth service per
 * request. Claim names match JwtUtils.java exactly (sub, user_type, ...).
 *
 * Shared with modules/crm's requireModAuthRole, which needs the same
 * signature check before it can go on to resolve a modauth Role (Broker /
 * Team Lead / Tenant Admin), which — unlike SUPER_ADMIN — isn't a JWT claim
 * gen-auth-starter itself knows about.
 */
export function extractBearerToken(req: Request): string {
  const header = req.headers.authorization;
  const token = header?.startsWith('Bearer ')
    ? header.slice('Bearer '.length)
    : req.cookies?.access_token;
  if (!token) {
    throw new ApiError('Missing bearer token', StatusCodes.UNAUTHORIZED);
  }
  return token;
}

export function verifyAuthToken(req: Request): jwt.JwtPayload {
  const token = extractBearerToken(req);
  try {
    return jwt.verify(token, env.platform.authJwtPublicKey, {
      algorithms: ['RS256'],
      issuer: env.platform.authJwtIssuer,
      audience: env.platform.authJwtAudience,
    }) as jwt.JwtPayload;
  } catch {
    throw new ApiError('Invalid or expired token', StatusCodes.UNAUTHORIZED);
  }
}

/**
 * Only `user_type=SUPER_ADMIN` may pass: that's gen-auth-starter's own
 * first-class concept (a platform_super_admin row, a separate login path),
 * independent of this repo's own modauth_user_roles table — the Super Admin
 * flow diagrams are scoped to that one role.
 */
export function requireSuperAdmin(req: Request, _res: Response, next: NextFunction): void {
  const payload = verifyAuthToken(req);

  if (payload.user_type !== 'SUPER_ADMIN') {
    throw new ApiError('Super admin access required', StatusCodes.FORBIDDEN);
  }

  const platformUser: PlatformUser = {
    userId: String(payload.sub),
    email: typeof payload.user_email === 'string' ? payload.user_email : null,
  };
  req.platformUser = platformUser;
  next();
}

import type { NextFunction, Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import { getModAuthPerson, resolveTeam, type ModAuthRole } from './modauth.client';
import { isProfileComplete } from './tenant.service';
import { ApiError } from '../../utils/api-error';
import { asyncHandler } from '../../utils/async-handler';
import { extractBearerToken, verifyAuthToken } from '../platform/auth-jwt.middleware';

export interface CrmUser {
  userId: string;
  tenantId: string;
  role: ModAuthRole;
  teamId: string | null;
  name: string | null;
  bearerToken: string;
}

/**
 * The Broker/Team-Lead/Tenant-Admin counterpart of requireSuperAdmin: verify
 * the JWT signature locally (same as requireSuperAdmin), then resolve the
 * caller's modauth Role via modules/auth's internal lookup — necessary
 * because, unlike SUPER_ADMIN, this role is not a JWT claim (see
 * modauth.client.ts's getModAuthPerson doc comment).
 */
export function requireModAuthRole(...allowedRoles: ModAuthRole[]) {
  // asyncHandler, not a bare async function: Express 4 doesn't forward a
  // rejected promise from middleware to the error handler on its own — an
  // unmocked getModAuthPerson() failure would otherwise hang the request
  // instead of returning a 502/403.
  return asyncHandler(async (req: Request, _res: Response, next: NextFunction): Promise<void> => {
    const bearerToken = extractBearerToken(req);
    const payload = verifyAuthToken(req);
    const userId = String(payload.sub);

    const person = await getModAuthPerson(userId);
    if (!person) {
      throw new ApiError('No role assigned — cannot use the CRM', StatusCodes.FORBIDDEN);
    }
    if (!person.active) {
      throw new ApiError('Account deactivated. Contact your admin.', StatusCodes.FORBIDDEN);
    }
    if (person.tenantSuspended) {
      throw new ApiError(
        "Your brokerage's access is suspended. Contact your administrator.",
        StatusCodes.FORBIDDEN,
      );
    }
    if (!allowedRoles.includes(person.role)) {
      throw new ApiError(`${person.role} cannot access this`, StatusCodes.FORBIDDEN);
    }

    req.crmUser = { ...person, bearerToken };
    next();
  });
}

/**
 * Which leads a single-person route (open, edit, delete, documents, AI…) may
 * touch. Flow 3: a Team Lead has "the same access as the owning broker" to
 * every person on their team; everyone else is scoped like their book.
 */
export async function leadAccessIds(user: CrmUser): Promise<string[]> {
  if (user.role === 'TEAM_LEAD' && user.teamId) {
    return (await resolveTeam(user.teamId, user.bearerToken)).brokerIds;
  }
  return bookBrokerIds(user);
}

/**
 * Whose leads an own-book route works on: the caller's own for a Broker/Team
 * Lead; for an Assistant (SOW 2.5 — no book of their own) every lead on
 * their team. Routes decide what an Assistant may do (view/edit, never
 * create/delete/reveal); this only decides which records.
 */
export async function bookBrokerIds(user: CrmUser): Promise<string[]> {
  if (user.role !== 'ASSISTANT') return [user.userId];
  if (!user.teamId) {
    throw new ApiError('You are not assigned to a team', StatusCodes.FORBIDDEN);
  }
  return (await resolveTeam(user.teamId, user.bearerToken)).brokerIds;
}

/**
 * Flow 0 "Tenant Admin → Complete the brokerage profile, required before
 * anything else". Goes after requireModAuthRole on every Tenant Admin route
 * except the profile itself; other roles pass straight through.
 */
export const requireCompleteBrokerageProfile = asyncHandler(
  async (req: Request, _res: Response, next: NextFunction): Promise<void> => {
    const user = req.crmUser!;
    if (user.role === 'TENANT_ADMIN' && !(await isProfileComplete(user.tenantId))) {
      throw new ApiError('Complete the brokerage profile first', StatusCodes.FORBIDDEN);
    }
    next();
  },
);

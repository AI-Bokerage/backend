import { env } from '../../config/env';
import { ApiError } from '../../utils/api-error';

const baseUrl = env.platform.authServiceBaseUrl;
const internalHeaders = {
  'Content-Type': 'application/json',
  'X-Internal-Secret': env.platform.internalSecret,
};

export type ModAuthRole = 'SUPER_ADMIN' | 'TENANT_ADMIN' | 'TEAM_LEAD' | 'BROKER' | 'ASSISTANT';

export interface ModAuthPerson {
  userId: string;
  tenantId: string;
  role: ModAuthRole;
  teamId: string | null;
  name: string | null;
  active: boolean;
  /** Their brokerage is suspended or cancelled (Flow 1). */
  tenantSuspended?: boolean;
}

/** Calls modules/auth's internal role lookup — the only way to resolve a modauth Role from a user id. */
export async function getModAuthPerson(userId: string): Promise<ModAuthPerson | null> {
  const res = await fetch(`${baseUrl}/internal/modauth/users/${userId}`, {
    headers: internalHeaders,
  });
  if (res.status === 404) return null;
  if (!res.ok) {
    throw new ApiError(`Auth service role lookup failed: ${res.status}`, 502);
  }
  return (await res.json()) as ModAuthPerson;
}

export interface TeamRoster {
  id: string;
  name: string;
  teamLead: { userId: string; name: string | null } | null;
  members: { userId: string; name: string | null }[];
}

/**
 * Forwards the caller's own JWT to modules/auth's JWT-authenticated
 * GET /api/v1/modauth/teams/{teamId} — that endpoint (not an internal one)
 * is the source of truth for "who's on my team" (see TeamServiceImpl.get,
 * which was relaxed to allow the team's own Team Lead, not just Tenant
 * Admin, specifically for this call).
 */
export async function getTeamRoster(
  teamId: string,
  callerBearerToken: string,
): Promise<TeamRoster> {
  const res = await fetch(`${baseUrl}/api/v1/modauth/teams/${teamId}`, {
    headers: { Authorization: `Bearer ${callerBearerToken}` },
  });
  if (!res.ok) {
    throw new ApiError(`Auth service team lookup failed: ${res.status}`, 502);
  }
  return (await res.json()) as TeamRoster;
}

export interface ResolvedTeam {
  members: { userId: string; name: string | null }[];
  brokerIds: string[];
}

/** One roster fetch per request — every team-scoped controller handler calls this once, not once per service function. */
export async function resolveTeam(
  teamId: string,
  callerBearerToken: string,
): Promise<ResolvedTeam> {
  const roster = await getTeamRoster(teamId, callerBearerToken);
  const members = [
    ...(roster.teamLead ? [{ userId: roster.teamLead.userId, name: roster.teamLead.name }] : []),
    ...roster.members.map((m) => ({ userId: m.userId, name: m.name })),
  ];
  return { members, brokerIds: members.map((m) => m.userId) };
}

export interface TenantPerson {
  userId: string;
  name: string | null;
  role: ModAuthRole;
  teamId: string | null;
}

/**
 * Forwards the caller's own JWT to modules/auth's JWT-authenticated
 * GET /api/v1/modauth/people (Tenant Admin only there) — the tenant-wide
 * counterpart of resolveTeam, for Reports and the "every lead/client
 * across the brokerage" view.
 */
export async function getTenantPeople(callerBearerToken: string): Promise<TenantPerson[]> {
  const res = await fetch(`${baseUrl}/api/v1/modauth/people`, {
    headers: { Authorization: `Bearer ${callerBearerToken}` },
  });
  if (!res.ok) {
    throw new ApiError(`Auth service people lookup failed: ${res.status}`, 502);
  }
  return (await res.json()) as TenantPerson[];
}

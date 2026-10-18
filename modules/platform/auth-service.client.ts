import { env } from '../../config/env';
import { ApiError } from '../../utils/api-error';

const baseUrl = env.platform.authServiceBaseUrl;
const headers = {
  'Content-Type': 'application/json',
  'X-Internal-Secret': env.platform.internalSecret,
};

/** Calls modules/auth's own /internal/auth/users/exists (gen-auth-starter's InternalUserController). */
export async function emailAlreadyHasAccount(email: string): Promise<boolean> {
  const res = await fetch(
    `${baseUrl}/internal/auth/users/exists?email=${encodeURIComponent(email)}`,
    { headers },
  );
  if (!res.ok) {
    throw new ApiError(`Auth service exists-check failed: ${res.status}`, 502);
  }
  return (await res.json()) as boolean;
}

export interface CreateBrokerageOwnerInvitationInput {
  name: string;
  email: string;
  tenantId: string;
  preAllocatedUserId: string;
}

export interface InvitationDto {
  id: string;
  name: string;
  email: string;
  role: string;
  teamName: string | null;
  status: 'PENDING' | 'ACCEPTED';
  expiresAt: string;
}

/** Calls modules/auth's POST /internal/modauth/invitations (this project's own addition). */
export async function createBrokerageOwnerInvitation(
  input: CreateBrokerageOwnerInvitationInput,
): Promise<InvitationDto> {
  const res = await fetch(`${baseUrl}/internal/modauth/invitations`, {
    method: 'POST',
    headers,
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new ApiError(`Auth service invitation create failed: ${res.status} ${body}`, 502);
  }
  return (await res.json()) as InvitationDto;
}

/**
 * Flow 1 "Suspend → everyone in that brokerage is blocked at login;
 * Reactivate → access restored". modules/auth blocks login, token refresh
 * and the CRM's role lookup, and signs everyone out when blocking.
 */
export async function setTenantAccess(
  tenantId: string,
  blocked: boolean,
  status: 'ACTIVE' | 'SUSPENDED' | 'CANCELLED',
): Promise<void> {
  const res = await fetch(`${baseUrl}/internal/modauth/tenants/${tenantId}/access`, {
    method: 'PUT',
    headers,
    body: JSON.stringify({ blocked, status }),
  });
  if (!res.ok) {
    throw new ApiError(`Auth service tenant access update failed: ${res.status}`, 502);
  }
}

import { StatusCodes } from 'http-status-codes';

import type { ModAuthRole } from './modauth.client';
import { env } from '../../config/env';
import { ApiError } from '../../utils/api-error';

/**
 * The one way this backend calls the AI services (ai service/docs/api/conventions.md):
 * service key, identity headers, JSON or multipart body, and the AI error
 * envelope {error: {code, message, retryable, ...}} turned into an ApiError
 * whose details carry `code` and friends through to the caller.
 */
export type AiService = 'gateway' | 'voice' | 'docread' | 'notes' | 'email' | 'assistant' | 'search';

export interface AiCaller {
  tenantId: string;
  userId?: string;
  role?: ModAuthRole;
}

/** CRM role → the services' x-user-role. The Tenant Admin is their ADMIN. */
const AI_ROLES: Partial<Record<ModAuthRole, string>> = {
  TENANT_ADMIN: 'ADMIN',
  TEAM_LEAD: 'TEAM_LEAD',
  BROKER: 'BROKER',
  ASSISTANT: 'ASSISTANT',
};

export const aiRole = (role: ModAuthRole) => AI_ROLES[role];

interface AiCallOptions {
  /** Identity headers. Omit for the gateway's /admin routes. */
  caller?: AiCaller;
  json?: unknown;
  form?: FormData;
  query?: Record<string, string | number | undefined>;
  idempotencyKey?: string;
  /** Sent as x-request-id; assistant-svc echoes it on its /ai-data/v1 reads. */
  requestId?: string;
  /** Use the gateway admin key (routes under /admin/v1). */
  admin?: boolean;
}

export async function aiCall<T>(
  service: AiService,
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  opts: AiCallOptions = {},
): Promise<T> {
  const config = env.ai[service];
  const key = opts.admin ? env.ai.gateway.adminKey : config.key;
  if (!key) {
    throw new ApiError(`The AI ${service} service is not configured`, StatusCodes.SERVICE_UNAVAILABLE, true, {
      code: 'AI_NOT_CONFIGURED',
      retryable: false,
    });
  }

  const headers: Record<string, string> = { Authorization: `Bearer ${key}` };
  if (opts.caller) {
    headers['x-brokerage-id'] = opts.caller.tenantId;
    if (opts.caller.userId) headers['x-user-id'] = opts.caller.userId;
    const role = opts.caller.role && aiRole(opts.caller.role);
    if (role) headers['x-user-role'] = role;
  }
  if (opts.idempotencyKey) headers['Idempotency-Key'] = opts.idempotencyKey;
  if (opts.requestId) headers['x-request-id'] = opts.requestId;
  let body: string | FormData | undefined = opts.form;
  if (opts.json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.json);
  }

  const url = new URL(path, config.url);
  for (const [k, v] of Object.entries(opts.query ?? {})) {
    if (v !== undefined) url.searchParams.set(k, String(v));
  }

  let res: Response;
  try {
    res = await fetch(url, { method, headers, body, signal: AbortSignal.timeout(60_000) });
  } catch {
    throw new ApiError(`The AI ${service} service can't be reached right now`, StatusCodes.SERVICE_UNAVAILABLE, true, {
      code: 'AI_UNREACHABLE',
      retryable: true,
    });
  }
  if (res.status === StatusCodes.NO_CONTENT) return undefined as T;

  const data = (await res.json().catch(() => null)) as { error?: Record<string, unknown> } | null;
  if (res.ok) return data as T;

  const { message, ...details } = data?.error ?? {};
  // A 401 means this backend's own key is wrong — our misconfiguration, not the caller's.
  const status = res.status === StatusCodes.UNAUTHORIZED ? StatusCodes.BAD_GATEWAY : res.status;
  throw new ApiError(
    typeof message === 'string' ? message : `The AI ${service} service failed`,
    status,
    true,
    details,
  );
}

import { buildAuthUrl, clientFor, exchangeCodeForTokens } from './google-oauth.client';
import { getPrismaClient } from '../../../config/database';
import { ApiError } from '../../../utils/api-error';
import { decryptToken, encryptToken } from '../encryption';

export interface ConnectionStatus {
  connected: boolean;
  googleEmail: string | null;
}

export function getConnectUrl(userId: string): string {
  // state carries the userId through Google's redirect — the callback has no
  // other way to know which broker this consent screen was for.
  return buildAuthUrl(userId);
}

export async function completeConnection(
  userId: string,
  tenantId: string,
  code: string,
): Promise<ConnectionStatus> {
  const tokens = await exchangeCodeForTokens(code);
  await getPrismaClient().googleConnection.upsert({
    where: { userId },
    create: {
      userId,
      tenantId,
      googleEmail: tokens.googleEmail,
      accessToken: encryptToken(tokens.accessToken),
      refreshToken: encryptToken(tokens.refreshToken),
      scope: tokens.scope,
      expiresAt: tokens.expiresAt,
    },
    update: {
      googleEmail: tokens.googleEmail,
      accessToken: encryptToken(tokens.accessToken),
      refreshToken: encryptToken(tokens.refreshToken),
      scope: tokens.scope,
      expiresAt: tokens.expiresAt,
    },
  });
  return { connected: true, googleEmail: tokens.googleEmail };
}

export async function disconnect(userId: string): Promise<void> {
  await getPrismaClient().googleConnection.deleteMany({ where: { userId } });
}

export async function getStatus(userId: string): Promise<ConnectionStatus> {
  const connection = await getPrismaClient().googleConnection.findUnique({ where: { userId } });
  return { connected: connection !== null, googleEmail: connection?.googleEmail ?? null };
}

export interface AuthenticatedConnection {
  client: ReturnType<typeof clientFor>;
  googleEmail: string | null;
}

/** Null (not throwing) when not connected — callers decide between "CRM-only" and "connection required" per the flow diagrams. */
export async function getAuthenticatedClientOrNull(
  userId: string,
): Promise<AuthenticatedConnection | null> {
  const connection = await getPrismaClient().googleConnection.findUnique({ where: { userId } });
  if (!connection) return null;

  const client = clientFor(
    decryptToken(connection.accessToken),
    decryptToken(connection.refreshToken),
    (accessToken, expiresAt) => {
      // Fire-and-forget: a failure to persist a refreshed token just means the
      // next call refreshes again — it does not fail the request in progress.
      void getPrismaClient()
        .googleConnection.update({
          where: { userId },
          data: { accessToken: encryptToken(accessToken), expiresAt },
        })
        .catch(() => undefined);
    },
  );
  return { client, googleEmail: connection.googleEmail };
}

/** For flows that require a connection (sending email) rather than tolerating its absence (scheduling a meeting). */
export async function requireAuthenticatedClient(userId: string): Promise<AuthenticatedConnection> {
  const connection = await getAuthenticatedClientOrNull(userId);
  if (!connection) {
    throw new ApiError('Connect Gmail to send', 400);
  }
  return connection;
}

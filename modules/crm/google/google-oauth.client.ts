import { google } from 'googleapis';

import { env } from '../../../config/env';
import { ApiError } from '../../../utils/api-error';

export const GOOGLE_SCOPES = [
  'https://www.googleapis.com/auth/calendar.events',
  'https://www.googleapis.com/auth/gmail.send',
  'https://www.googleapis.com/auth/userinfo.email',
];

function requireConfigured(): void {
  if (!env.google.clientId || !env.google.clientSecret) {
    // ponytail: no fake defaults for these two — a silently-misconfigured
    // OAuth client would fail confusingly deep inside Google's own redirect,
    // not here. Loud and early instead.
    throw new ApiError('Google OAuth is not configured on this server', 500);
  }
}

export function newOAuthClient() {
  requireConfigured();
  return new google.auth.OAuth2(
    env.google.clientId,
    env.google.clientSecret,
    env.google.redirectUri,
  );
}

/** "Connect email - Gmail or Outlook" / "Connect calendar" (Flow 4.12) — the consent-screen redirect URL. */
export function buildAuthUrl(state: string): string {
  const client = newOAuthClient();
  return client.generateAuthUrl({
    access_type: 'offline', // required to get a refresh_token back
    prompt: 'consent', // forces a refresh_token on every connect, not just the first
    scope: GOOGLE_SCOPES,
    state,
  });
}

export interface ExchangedTokens {
  accessToken: string;
  refreshToken: string;
  expiresAt: Date;
  scope: string;
  googleEmail: string | null;
}

export async function exchangeCodeForTokens(code: string): Promise<ExchangedTokens> {
  const client = newOAuthClient();
  const { tokens } = await client.getToken(code);
  if (!tokens.access_token || !tokens.refresh_token) {
    // Happens if the user had already granted consent before and Google
    // skips issuing a fresh refresh_token — prompt: 'consent' above is what
    // prevents this in practice, but a clear error beats a silently broken connection.
    throw new ApiError(
      'Google did not return a refresh token — try disconnecting and reconnecting',
      502,
    );
  }

  client.setCredentials(tokens);
  const oauth2 = google.oauth2({ auth: client, version: 'v2' });
  const { data } = await oauth2.userinfo.get();

  return {
    accessToken: tokens.access_token,
    refreshToken: tokens.refresh_token,
    expiresAt: new Date(tokens.expiry_date ?? Date.now() + 3600_000),
    scope: tokens.scope ?? GOOGLE_SCOPES.join(' '),
    googleEmail: data.email ?? null,
  };
}

/**
 * An OAuth2Client pre-loaded with a connection's tokens. googleapis
 * auto-refreshes the access token against Google when it's expired —
 * onTokensRefreshed is how the caller persists the new one (it is NOT
 * written back here, to keep this module free of any Prisma dependency).
 */
export function clientFor(
  accessToken: string,
  refreshToken: string,
  onTokensRefreshed: (accessToken: string, expiresAt: Date) => void,
) {
  const client = newOAuthClient();
  client.setCredentials({ access_token: accessToken, refresh_token: refreshToken });
  client.on('tokens', (tokens) => {
    if (tokens.access_token) {
      onTokensRefreshed(tokens.access_token, new Date(tokens.expiry_date ?? Date.now() + 3600_000));
    }
  });
  return client;
}

import { OIDC_AUTHORITY, OIDC_CLIENT_ID, TEST_USER } from './config';

/**
 * Programmatic sign-in.
 *
 * Driving the Keycloak login form once per test would cost two full page loads and a
 * redirect each time. Instead the token is fetched over the direct access grant the
 * `librarius-web` client already allows, and written into `localStorage` in the exact
 * shape `oidc-client-ts` would have written after a redirect login — so the application
 * boots signed in, through its own session store, with a token the API really issued.
 */

type TokenResponse = {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  token_type?: string;
  scope?: string;
  expires_in: number;
  session_state?: string;
};

export type Session = {
  accessToken: string;
  /** localStorage key read by `oidc-client-ts` on boot. */
  storageKey: string;
  storageValue: string;
};

function decodeJwtPayload(jwt: string): Record<string, unknown> {
  const payload = jwt.split('.')[1] ?? '';
  return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8')) as Record<string, unknown>;
}

export async function signIn(user = TEST_USER): Promise<Session> {
  const response = await fetch(`${OIDC_AUTHORITY}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'password',
      client_id: OIDC_CLIENT_ID,
      username: user.username,
      password: user.password,
      scope: 'openid profile email',
    }),
  });

  if (!response.ok) {
    throw new Error(
      `Sign-in failed for ${user.username}: HTTP ${response.status} ${await response.text()}`,
    );
  }

  const token = (await response.json()) as TokenResponse;

  return {
    accessToken: token.access_token,
    storageKey: `oidc.user:${OIDC_AUTHORITY}:${OIDC_CLIENT_ID}`,
    storageValue: JSON.stringify({
      id_token: token.id_token,
      session_state: token.session_state ?? null,
      access_token: token.access_token,
      refresh_token: token.refresh_token,
      token_type: token.token_type ?? 'Bearer',
      scope: token.scope,
      profile: token.id_token ? decodeJwtPayload(token.id_token) : {},
      expires_at: Math.floor(Date.now() / 1000) + token.expires_in,
    }),
  };
}

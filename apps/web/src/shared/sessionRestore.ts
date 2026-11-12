/**
 * Whether a stored session can be brought back without asking the reader to sign in (#381).
 *
 * `react-oidc-context` counts a user as signed in only while their access token is valid,
 * and Keycloak's last five minutes. Reopen the app later and the stored user is `expired`,
 * yet it still holds the offline refresh token #371 asked for, valid for weeks. That user
 * is not signed out: they are one refresh-token grant away from being signed in again.
 */
export function canRestore(user: { expired?: boolean; refresh_token?: string } | null | undefined): boolean {
  return Boolean(user?.expired && user.refresh_token);
}

import { useAuth } from 'react-oidc-context';

/**
 * Authentication state for the screens that gate on it.
 *
 * It no longer hands out fetch options: the bearer token is attached by `apiClient`,
 * which the generated hooks call. Screens only need to know whether a session exists.
 */
export function useApiAuth() {
  const auth = useAuth();
  return {
    authed: auth.isAuthenticated,
    loading: auth.isLoading,
    login: () => void auth.signinRedirect(),
    // Drops the local session. Used after an account deletion, where the Keycloak account
    // is already gone: the token in memory stays cryptographically valid until it expires,
    // so it has to be thrown away rather than waited out.
    signOut: () => void auth.removeUser(),
    // A full RP-initiated sign-out: it ends the Keycloak session too, not just the local
    // token, so the persistent SSO session does not sign the user straight back in.
    signOutFully: () => void auth.signoutRedirect(),
  };
}

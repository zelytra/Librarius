import { useState } from 'react';
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

/**
 * The sign-in button's action, and what became of it.
 *
 * `signinRedirect` first fetches the identity provider's configuration, then leaves the page.
 * When the provider does not answer — Keycloak down, `/auth` answering 503 — the promise
 * rejects and nothing else happens: the button used to do exactly nothing, with no word to
 * the reader. `failed` lets the screen say the sign-in service is unavailable, `pending`
 * that something is under way while the configuration is fetched.
 */
export function useSignIn() {
  const auth = useAuth();
  const [pending, setPending] = useState(false);
  const [failed, setFailed] = useState(false);
  const signIn = () => {
    setFailed(false);
    setPending(true);
    Promise.resolve()
      .then(() => auth.signinRedirect())
      .catch(() => setFailed(true))
      .finally(() => setPending(false));
  };
  return { signIn, pending, failed };
}

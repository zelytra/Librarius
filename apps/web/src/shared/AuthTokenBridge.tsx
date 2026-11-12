import { useEffect } from 'react';
import { useAuth } from 'react-oidc-context';
import { setAccessToken, setSessionHandlers } from './authToken';
import { canRestore } from './sessionRestore';

/**
 * Mirrors the OIDC session into `authToken`, so the generated API client can read the
 * bearer token without being a React component. Renders nothing.
 */
export function AuthTokenBridge() {
  const auth = useAuth();

  useEffect(() => {
    setAccessToken(auth.user?.access_token);
  }, [auth.user]);

  // Back on the app after the access token expired (#381): renew it with the stored refresh
  // token instead of treating the reader as signed out. Only a refused renewal — the offline
  // session revoked or idle too long — drops the stored user, and the sign-in screen follows.
  useEffect(() => {
    if (!auth.isLoading && canRestore(auth.user)) {
      auth.signinSilent().catch(() => auth.removeUser());
    }
    // Once per stored user: `auth` itself changes identity on every state change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.user, auth.isLoading]);

  useEffect(() => {
    setSessionHandlers({
      renew: async () => {
        const user = await auth.signinSilent();
        setAccessToken(user?.access_token ?? undefined);
      },
      signIn: () => void auth.signinRedirect(),
    });
  }, [auth]);

  return null;
}

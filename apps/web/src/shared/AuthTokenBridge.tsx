import { useEffect } from 'react';
import { useAuth } from 'react-oidc-context';
import { setAccessToken, setSessionHandlers } from './authToken';

/**
 * Mirrors the OIDC session into `authToken`, so the generated API client can read the
 * bearer token without being a React component. Renders nothing.
 */
export function AuthTokenBridge() {
  const auth = useAuth();

  useEffect(() => {
    setAccessToken(auth.user?.access_token);
  }, [auth.user]);

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

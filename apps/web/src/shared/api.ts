import { useAuth } from 'react-oidc-context';

/** Accès pratique à l'état d'auth + aux options fetch (jeton) pour l'API. */
export function useApiAuth() {
  const auth = useAuth();
  const token = auth.user?.access_token;
  return {
    authed: auth.isAuthenticated,
    loading: auth.isLoading,
    login: () => void auth.signinRedirect(),
    opts: token ? ({ headers: { Authorization: `Bearer ${token}` } } as RequestInit) : undefined,
  };
}

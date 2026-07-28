import { useAuth } from 'react-oidc-context';

/** Convenient access to the auth state plus the fetch options (token) for the API. */
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

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
  };
}

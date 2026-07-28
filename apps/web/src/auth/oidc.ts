import { WebStorageStateStore } from 'oidc-client-ts';
import type { AuthProviderProps } from 'react-oidc-context';

/**
 * OIDC configuration (Authorization Code + PKCE) against Keycloak.
 * In dev, the Keycloak from docker-compose (port 8081, realm "librarius").
 * Can be overridden through the VITE_OIDC_* variables.
 */
export const oidcConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY || 'http://localhost:8081/realms/librarius',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID || 'librarius-web',
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  scope: 'openid profile email',
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  // Strips the callback parameters from the URL after sign-in.
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};

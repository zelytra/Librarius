import { WebStorageStateStore } from 'oidc-client-ts';
import type { AuthProviderProps } from 'react-oidc-context';

/**
 * Configuration OIDC (Authorization Code + PKCE) contre Keycloak.
 * En dev, le Keycloak du docker-compose (port 8081, realm « librarius »).
 * Surchargeable via les variables VITE_OIDC_*.
 */
export const oidcConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8081/realms/librarius',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'librarius-web',
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  scope: 'openid profile email',
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  // Nettoie les paramètres de callback de l'URL après connexion.
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};

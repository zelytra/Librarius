import { Capacitor } from '@capacitor/core';
import { UserManager, WebStorageStateStore } from 'oidc-client-ts';
import type { UserManagerSettings } from 'oidc-client-ts';
import type { AuthProviderNoUserManagerProps, AuthProviderProps } from 'react-oidc-context';
import { NATIVE_REDIRECT_URI, systemBrowserNavigator } from './nativeAuth';

/**
 * A browser is its own redirect target. The native container is not: its origin is
 * `https://localhost` on Android and `capacitor://localhost` on iOS — values every
 * installation shares and that Keycloak has no reason to trust — so the callback comes
 * back through the custom scheme instead.
 *
 * The rest of the native flow — the system browser, and the callback link the OS routes back
 * to the app — is `nativeAuth.ts`, reached through `authProviderProps` below (#528).
 */
const redirectUri = Capacitor.isNativePlatform()
  ? NATIVE_REDIRECT_URI
  : window.location.origin;

/**
 * OIDC configuration (Authorization Code + PKCE) against Keycloak.
 * In dev, the Keycloak from docker-compose (port 8081, realm "librarius").
 * Can be overridden through the VITE_OIDC_* variables.
 *
 * Typed as the settings-carrying half of `AuthProviderProps`: the other half takes a
 * `UserManager` instance instead, and the union hides the settings behind it.
 */
export const oidcConfig: AuthProviderNoUserManagerProps = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY || 'http://localhost:8081/realms/librarius',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID || 'librarius-web',
  redirect_uri: redirectUri,
  post_logout_redirect_uri: redirectUri,
  // `offline_access`: Keycloak hands out an offline token — kept in its database, so a
  // Keycloak restart or redeploy leaves it valid, and with no maximum lifetime, only an idle
  // limit (30 days by default). A reader who opens the app once a month stays signed in for
  // good, where an ordinary session ended after 30 minutes away. Verified against the
  // staging realm: the default roles grant it, the token comes back typed "Offline".
  scope: 'openid profile email offline_access',
  // Signing out has to end the offline session too, not only the browser's: otherwise the
  // token would stay valid in Keycloak for weeks after the reader asked to leave.
  revokeTokensOnSignout: true,
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  // Strips the callback parameters from the URL after sign-in. A no-op in the container,
  // where the callback never touches the page's URL.
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};

/**
 * The container's own `UserManager`, or `null` in a browser.
 *
 * It is the same configuration, with the redirect navigator replaced: the authorization page
 * has to open in the system browser rather than in the WebView (#528, `nativeAuth.ts`). The
 * token renewal path is untouched — the offline refresh token #371 asked for is exchanged
 * directly, with no iframe, which is what makes `AuthTokenBridge`'s `signinSilent()` work on
 * a device as well as in a tab.
 */
export const nativeUserManager: UserManager | null = Capacitor.isNativePlatform()
  ? new UserManager(oidcConfig as UserManagerSettings, systemBrowserNavigator)
  : null;

/**
 * What `<AuthProvider>` is given: the settings in a browser, the container's `UserManager`
 * on a device. `AuthProviderProps` is a union of those two shapes, and the provider reads
 * only one of them.
 */
export const authProviderProps: AuthProviderProps = nativeUserManager
  ? { userManager: nativeUserManager }
  : oidcConfig;

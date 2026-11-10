import { Capacitor } from '@capacitor/core';
import { WebStorageStateStore } from 'oidc-client-ts';
import type { AuthProviderNoUserManagerProps } from 'react-oidc-context';

/**
 * Custom scheme the native container is reachable at, mirroring the Capacitor `appId`
 * declared in `apps/mobile/capacitor.config.ts`. Reverse-DNS rather than a bare
 * `librarius://`: any application installed on the device can claim a short scheme.
 *
 * Nothing registers it yet: the Android intent filter and the iOS URL type come with the
 * native projects — https://github.com/zelytra/Librarius/issues/70 and /issues/71.
 */
export const NATIVE_REDIRECT_URI = 'fr.zelytra.librarius://auth';

/**
 * A browser is its own redirect target. The native container is not: its origin is
 * `https://localhost` on Android and `capacitor://localhost` on iOS — values every
 * installation shares and that Keycloak has no reason to trust — so the callback comes
 * back through the custom scheme instead.
 *
 * Declaring the URI is not the whole native flow. Sign-in still navigates the WebView to
 * Keycloak, which RFC 8252 rules out and which cannot follow a custom scheme back; the
 * remaining pieces are listed in `.claude/docs/MOBILE.md` § 5.
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
  // Strips the callback parameters from the URL after sign-in.
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};

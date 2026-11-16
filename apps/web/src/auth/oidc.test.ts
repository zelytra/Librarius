import { afterEach, describe, expect, test, vi } from 'vitest';
import { oidcConfig } from './oidc';
import { NATIVE_REDIRECT_URI } from './nativeAuth';

describe('oidcConfig', () => {
  /** Long sessions: an offline token survives Keycloak restarts and has no maximum lifetime. */
  test('asks for an offline token, and revokes it on sign-out', () => {
    expect(oidcConfig.scope?.split(' ')).toContain('offline_access');
    expect(oidcConfig.revokeTokensOnSignout).toBe(true);
  });

  test('redirects to the page itself in a browser', () => {
    expect(oidcConfig.redirect_uri).toBe(window.location.origin);
    expect(oidcConfig.post_logout_redirect_uri).toBe(window.location.origin);
  });
});

/**
 * #528: on a device the provider is given a `UserManager` of our own, because the
 * authorization page has to open in the system browser rather than in the WebView. The
 * platform is read when the module is first imported, hence the re-import in each case.
 */
describe('authProviderProps', () => {
  afterEach(() => {
    vi.resetModules();
    vi.doUnmock('@capacitor/core');
  });

  async function load(native: boolean) {
    vi.resetModules();
    vi.doMock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => native } }));
    return import('./oidc');
  }

  test('hands the settings straight to the provider in a browser', async () => {
    const { authProviderProps, nativeUserManager } = await load(false);

    expect(nativeUserManager).toBeNull();
    expect('userManager' in authProviderProps).toBe(false);
    expect(authProviderProps).toHaveProperty('authority');
  });

  test('hands the provider a user manager built on the custom scheme on a device', async () => {
    const { authProviderProps, nativeUserManager } = await load(true);

    expect(nativeUserManager).not.toBeNull();
    expect(authProviderProps).toEqual({ userManager: nativeUserManager });
    expect(nativeUserManager!.settings.redirect_uri).toBe(NATIVE_REDIRECT_URI);
    expect(nativeUserManager!.settings.post_logout_redirect_uri).toBe(NATIVE_REDIRECT_URI);
  });

  // The whole point of the custom navigator: `signinRedirect()` must not navigate the WebView
  // to Keycloak, which is what the default redirect navigator does.
  test('opens the authorization page in the system browser, not in the web view', async () => {
    const { nativeUserManager } = await load(true);
    const { systemBrowserNavigator } = await import('./nativeAuth');

    expect(nativeUserManager!['_redirectNavigator']).toBe(systemBrowserNavigator);
  });
});

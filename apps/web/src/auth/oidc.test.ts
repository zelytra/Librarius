import { afterEach, describe, expect, test, vi } from 'vitest';

/**
 * Loads the configuration for a given platform. The module resolves the platform once,
 * when it is imported, so the mock has to be in place before that import.
 */
async function loadOidcConfig(isNativePlatform: boolean) {
  vi.resetModules();
  vi.doMock('@capacitor/core', () => ({
    Capacitor: { isNativePlatform: () => isNativePlatform },
  }));
  return (await import('./oidc')).oidcConfig;
}

afterEach(() => {
  vi.doUnmock('@capacitor/core');
  vi.resetModules();
});

describe('oidcConfig', () => {
  test('sends the browser back to its own origin', async () => {
    const config = await loadOidcConfig(false);

    expect(config.redirect_uri).toBe(window.location.origin);
    expect(config.post_logout_redirect_uri).toBe(window.location.origin);
  });

  // The native container's origin is `https://localhost` (Android) or
  // `capacitor://localhost` (iOS): shared by every installation, and not something
  // Keycloak can redirect back to. The callback goes through the custom scheme that the
  // native project registers.
  test('sends the native container back to the custom scheme', async () => {
    const config = await loadOidcConfig(true);

    expect(config.redirect_uri).toBe('fr.zelytra.librarius://auth');
    expect(config.post_logout_redirect_uri).toBe('fr.zelytra.librarius://auth');
  });
});

import { App as NativeApp } from '@capacitor/app';
import { Browser } from '@capacitor/browser';
import type { INavigator, IWindow, NavigateParams, NavigateResponse } from 'oidc-client-ts';
import { note } from '../shared/feedback/diagnostics';

/**
 * Custom scheme the native container is reachable at, mirroring the Capacitor `appId`
 * declared in `apps/mobile/capacitor.config.ts`. Reverse-DNS rather than a bare
 * `librarius://`: any application installed on the device can claim a short scheme.
 *
 * Registered natively by the `<intent-filter>` in `AndroidManifest.xml` (through
 * `@string/custom_url_scheme`) and, on iOS, by `CFBundleURLTypes` in `Info.plist`.
 */
export const NATIVE_REDIRECT_URI = 'fr.zelytra.librarius://auth';

/**
 * The native half of the sign-in flow (#528).
 *
 * In the browser, `signinRedirect()` navigates the page to Keycloak and the answer comes back
 * as a navigation the same page can read. Neither half of that works inside the container:
 *
 * - RFC 8252 rules the WebView out as the place to type a password — the app can read what is
 *   typed into its own WebView, and that cookie jar is private to the app, so there is no
 *   single sign-on with the device's browser and every launch would ask again. The
 *   authorization page therefore opens in the system browser (a Custom Tab on Android, an
 *   `ASWebAuthenticationSession` on iOS), which is what `@capacitor/browser` provides.
 * - The answer comes back out of band, as a link on the app's own scheme that the OS routes
 *   to it — `appUrlOpen` from `@capacitor/app`, not a navigation.
 *
 * `UserManager` takes the navigator below in place of its redirect navigator, and
 * `routeAuthCallbacks` is what finishes the flow when the link arrives: `signinRedirect()`
 * ignores whatever `navigate()` returns, so the response cannot be handed back through it.
 */

/** Opens the authorization URL in the system browser instead of the WebView. */
export const systemBrowserNavigator: INavigator = {
  prepare: (): Promise<IWindow> =>
    Promise.resolve({
      async navigate(params: NavigateParams): Promise<NavigateResponse> {
        await Browser.open({ url: params.url });
        // Resolved rather than left pending: the callback arrives through `appUrlOpen`, and a
        // promise that never settles would leave the sign-in button spinning for good.
        return { url: params.url };
      },
      close() {
        void Browser.close().catch(() => undefined);
      },
    }),
  // Only ever called in the window the authorization server redirected to, which on a device
  // is the system browser — not this WebView.
  callback: () => Promise.resolve(),
};

/** What `routeAuthCallbacks` needs of a `UserManager`, so it can be driven by a test. */
export interface AuthCallbackTarget {
  signinCallback(url: string): Promise<unknown>;
  signoutCallback(url: string): Promise<unknown>;
}

/**
 * Routes the callback link into the OIDC client, and returns the function that stops
 * listening. Anything on the device may send the app a link, so only the exact callback URI
 * is acted on.
 */
export async function routeAuthCallbacks(oidc: AuthCallbackTarget): Promise<() => void> {
  const handle = await NativeApp.addListener('appUrlOpen', ({ url }: { url: string }) => {
    if (!url.startsWith(`${NATIVE_REDIRECT_URI}?`) && url !== NATIVE_REDIRECT_URI) return;
    // The reader is looking at the system browser; close it before the app takes over again.
    void Browser.close().catch(() => undefined);
    void complete(oidc, url);
  });
  return () => void handle.remove();
}

/**
 * Sign-in and sign-out come back on the same URI. A `code` (or an `error`, when the reader
 * refused or Keycloak did) is the sign-in leg; a bare `state` is the sign-out leg.
 */
async function complete(oidc: AuthCallbackTarget, url: string): Promise<void> {
  const params = new URL(url).searchParams;
  const signingIn = params.has('code') || params.has('error');
  try {
    await (signingIn ? oidc.signinCallback(url) : oidc.signoutCallback(url));
  } catch (error) {
    // Reported, not thrown: the flow ended badly in the browser, and the reader is back on a
    // screen that still works. #489's log carries the reason into a report.
    note(`auth callback refused: ${error instanceof Error ? error.message : String(error)}`);
  }
}

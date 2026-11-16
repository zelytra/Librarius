import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

/**
 * The two plugins are faked rather than the module under test: what is worth locking is that
 * the authorization page opens in the **system browser** and never in the WebView (RFC 8252),
 * and that the deep link the OS routes back reaches the OIDC client (#528).
 */
const open = vi.fn(() => Promise.resolve());
const close = vi.fn(() => Promise.resolve());
const remove = vi.fn(() => Promise.resolve());
let listener: ((event: { url: string }) => void) | undefined;
const addListener = vi.fn((_event: string, handler: (event: { url: string }) => void) => {
  listener = handler;
  return Promise.resolve({ remove });
});

vi.mock('@capacitor/browser', () => ({ Browser: { open, close } }));
vi.mock('@capacitor/app', () => ({ App: { addListener } }));

const { routeAuthCallbacks, systemBrowserNavigator } = await import('./nativeAuth');

beforeEach(() => {
  open.mockClear();
  close.mockClear();
  remove.mockClear();
  addListener.mockClear();
  listener = undefined;
});

afterEach(() => vi.clearAllMocks());

describe('systemBrowserNavigator', () => {
  test('opens the authorization page in the system browser', async () => {
    const handle = await systemBrowserNavigator.prepare({});

    await handle.navigate({
      url: 'https://librarius.zelytra.fr/auth/realms/librarius/protocol/openid-connect/auth?x=1',
      state: 'state-1',
    });

    expect(open).toHaveBeenCalledWith({
      url: 'https://librarius.zelytra.fr/auth/realms/librarius/protocol/openid-connect/auth?x=1',
    });
  });

  // The WebView cannot follow a custom scheme, so the callback never comes back through this
  // promise: the deep-link listener finishes the flow. What matters is that `navigate` does
  // not hang for ever, which would leave the sign-in button spinning.
  test('resolves once the browser is open', async () => {
    const handle = await systemBrowserNavigator.prepare({});

    await expect(handle.navigate({ url: 'https://example.org/auth', state: 's' })).resolves.toEqual({
      url: 'https://example.org/auth',
    });
  });

  test('closes the system browser when the flow is abandoned', async () => {
    const handle = await systemBrowserNavigator.prepare({});

    handle.close();

    expect(close).toHaveBeenCalled();
  });
});

describe('routeAuthCallbacks', () => {
  const target = () => ({
    signinCallback: vi.fn(() => Promise.resolve()),
    signoutCallback: vi.fn(() => Promise.resolve()),
  });

  test('hands a sign-in callback to the OIDC client and closes the browser', async () => {
    const oidc = target();
    await routeAuthCallbacks(oidc);

    listener!({ url: 'fr.zelytra.librarius://auth?code=abc&state=xyz' });
    await vi.waitFor(() => expect(oidc.signinCallback).toHaveBeenCalledTimes(1));

    expect(oidc.signinCallback).toHaveBeenCalledWith('fr.zelytra.librarius://auth?code=abc&state=xyz');
    expect(oidc.signoutCallback).not.toHaveBeenCalled();
    expect(close).toHaveBeenCalled();
  });

  // A failed sign-in comes back on the same URI with `error` instead of `code`; it is still
  // the sign-in leg, and the client has to see it to clear the state it is holding.
  test('hands a refused sign-in to the sign-in callback too', async () => {
    const oidc = target();
    await routeAuthCallbacks(oidc);

    listener!({ url: 'fr.zelytra.librarius://auth?error=access_denied&state=xyz' });
    await vi.waitFor(() => expect(oidc.signinCallback).toHaveBeenCalledTimes(1));
  });

  test('hands a sign-out callback to the sign-out callback', async () => {
    const oidc = target();
    await routeAuthCallbacks(oidc);

    listener!({ url: 'fr.zelytra.librarius://auth?state=xyz' });
    await vi.waitFor(() => expect(oidc.signoutCallback).toHaveBeenCalledTimes(1));

    expect(oidc.signinCallback).not.toHaveBeenCalled();
  });

  // Any application on the device can send the app a link. One that is not the callback is
  // not an authentication answer and must not be fed to the OIDC client.
  test('ignores a link that is not the authentication callback', async () => {
    const oidc = target();
    await routeAuthCallbacks(oidc);

    listener!({ url: 'https://librarius.zelytra.fr/library' });
    listener!({ url: 'fr.zelytra.librarius://other?code=abc' });
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(oidc.signinCallback).not.toHaveBeenCalled();
    expect(oidc.signoutCallback).not.toHaveBeenCalled();
  });

  // A failing callback must not take the app down with it: the reader stays on the sign-in
  // screen and can try again.
  test('swallows a callback the OIDC client refuses', async () => {
    const oidc = {
      signinCallback: vi.fn(() => Promise.reject(new Error('invalid_grant'))),
      signoutCallback: vi.fn(() => Promise.resolve()),
    };
    await routeAuthCallbacks(oidc);

    listener!({ url: 'fr.zelytra.librarius://auth?code=abc&state=xyz' });
    await vi.waitFor(() => expect(oidc.signinCallback).toHaveBeenCalledTimes(1));
  });

  test('removes its listener when told to stop', async () => {
    const stop = await routeAuthCallbacks(target());

    stop();

    expect(remove).toHaveBeenCalledTimes(1);
  });
});

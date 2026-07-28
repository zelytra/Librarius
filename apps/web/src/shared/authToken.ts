/**
 * Bridge between the React authentication context and the plain-function API client.
 *
 * The generated client is not a component and cannot call `useAuth()`, yet every request
 * needs a bearer token. `AuthTokenBridge` mirrors the current session into this module,
 * which the client then reads synchronously.
 */

let accessToken: string | undefined;
let renewSession: (() => Promise<void>) | undefined;
let signIn: (() => void) | undefined;

export function setAccessToken(token: string | undefined) {
  accessToken = token;
}

export function getAccessToken(): string | undefined {
  return accessToken;
}

/** Registers the callbacks used to recover from an expired token. */
export function setSessionHandlers(handlers: {
  renew: () => Promise<void>;
  signIn: () => void;
}) {
  renewSession = handlers.renew;
  signIn = handlers.signIn;
}

/**
 * Attempts a silent renewal. Returns true when a fresh token is available, false when
 * the caller should give up and let the sign-in redirect happen.
 */
export async function tryRenewSession(): Promise<boolean> {
  if (!renewSession) return false;
  try {
    await renewSession();
    return accessToken != null;
  } catch {
    return false;
  }
}

export function redirectToSignIn() {
  signIn?.();
}

/** Test helper: clears the module state between test files. */
export function resetSession() {
  accessToken = undefined;
  renewSession = undefined;
  signIn = undefined;
}

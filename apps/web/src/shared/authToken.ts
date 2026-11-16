/**
 * Bridge between the React authentication context and the plain-function API client.
 *
 * The generated client is not a component and cannot call `useAuth()`, yet every request
 * needs a bearer token. `AuthTokenBridge` mirrors the current session into this module,
 * which the client then reads synchronously.
 */

let accessToken: string | undefined;
let renewSession: (() => Promise<void>) | undefined;
let endSession: (() => void) | undefined;

/** Remembers, on this device, that a reader has been signed in here (#494). */
const SESSION_SEEN_KEY = 'librarius.sessionSeen';

export function setAccessToken(token: string | undefined) {
  accessToken = token;
  if (token) markSessionSeen();
}

export function getAccessToken(): string | undefined {
  return accessToken;
}

/** Registers the callbacks used to recover from an expired token. */
export function setSessionHandlers(handlers: {
  renew: () => Promise<void>;
  endSession: () => void;
}) {
  renewSession = handlers.renew;
  endSession = handlers.endSession;
}

/**
 * Attempts a silent renewal. Returns true when a fresh token is available, false when
 * the caller should give up and end the session (`endExpiredSession`).
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

/**
 * Gives up on a session that could not be renewed (#494): the local session is dropped and
 * the screen's own sign-in prompt takes over, where the reader is. Nothing navigates — being
 * thrown onto the Keycloak page or the landing page mid-screen was the bug.
 */
export function endExpiredSession() {
  endSession?.();
}

/**
 * Whether a reader has been signed in on this device. The root only sends a visitor to the
 * landing page when not (#494): someone whose session ended is a reader, not a stranger.
 * Storage may be unavailable (private mode): the answer is then "no", the former behaviour.
 */
export function sessionSeen(): boolean {
  try {
    return localStorage.getItem(SESSION_SEEN_KEY) === '1';
  } catch {
    return false;
  }
}

export function markSessionSeen() {
  try {
    localStorage.setItem(SESSION_SEEN_KEY, '1');
  } catch {
    // Not remembered: the root falls back to the landing page for this visitor.
  }
}

/** A reader who signs out on purpose, or deletes their account, is a visitor again. */
export function forgetSession() {
  try {
    localStorage.removeItem(SESSION_SEEN_KEY);
  } catch {
    // Nothing stored, nothing to forget.
  }
}

/** Test helper: clears the module state between test files. */
export function resetSession() {
  accessToken = undefined;
  renewSession = undefined;
  endSession = undefined;
  forgetSession();
}

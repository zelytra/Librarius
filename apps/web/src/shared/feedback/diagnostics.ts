import { postApiFeedbackErrors, type ClientErrorDto } from '../../api/generated/librarius';
import { getAccessToken } from '../authToken';

/**
 * What the app knows of its own recent life (#489, #490): the screens visited, the API calls
 * that failed and the errors nobody caught, kept in memory for the length of the session. A
 * member's report carries it, and an uncaught error is reported by itself.
 */

/** How many lines the log keeps: enough to see what led to a problem, not a whole session. */
export const MAX_LOG = 50;

/** How many distinct errors a session reports at most: a broken screen is not a flood. */
export const MAX_REPORTS = 5;

// Stamped at build time (`VITE_APP_VERSION`, see apps/web/Dockerfile); empty in `pnpm web:dev`.
export const APP_VERSION = import.meta.env.VITE_APP_VERSION || 'dev';

const lines: string[] = [];
const reported = new Set<string>();

/** Adds a line to the log, stamped with the time. */
export function note(line: string) {
  lines.push(`${new Date().toISOString().slice(11, 19)} ${line}`);
  if (lines.length > MAX_LOG) lines.shift();
}

/** The log, oldest line first. */
export function recentLog(): string {
  return lines.join('\n');
}

/** Where the app is, which version it runs and in which browser. */
export function context() {
  return {
    page: window.location.pathname + window.location.search,
    appVersion: APP_VERSION,
    userAgent: navigator.userAgent,
  };
}

const describe = (error: unknown) =>
  error instanceof Error ? `${error.name}: ${error.message}` : String(error);

/**
 * Logs an error nobody caught and, for a signed-in member, reports it (#490) — once per
 * session for the same error, and no more than {@link MAX_REPORTS} of them.
 */
export function reportError(
  error: unknown,
  send: (error: ClientErrorDto) => Promise<unknown> = postApiFeedbackErrors,
) {
  const message = describe(error).slice(0, 2000);
  const stack = error instanceof Error ? error.stack?.slice(0, 20000) : undefined;
  note(`erreur ${message}`);
  if (!getAccessToken()) return;
  const key = `${message}|${stack?.split('\n')[1] ?? ''}`;
  if (reported.has(key) || reported.size >= MAX_REPORTS) return;
  reported.add(key);
  // A report that cannot be sent is dropped: it must not raise an error of its own.
  send({ message, stack, ...context() }).catch(() => undefined);
}

/** Reports what the window catches: errors thrown outside React and promises nobody awaited. */
export function installErrorReporting(): () => void {
  const onError = (event: ErrorEvent) => reportError(event.error ?? event.message);
  const onRejection = (event: PromiseRejectionEvent) => reportError(event.reason);
  window.addEventListener('error', onError);
  window.addEventListener('unhandledrejection', onRejection);
  return () => {
    window.removeEventListener('error', onError);
    window.removeEventListener('unhandledrejection', onRejection);
  };
}

/** Test-only: a fresh log and a fresh count of reported errors. */
export function resetDiagnostics() {
  lines.length = 0;
  reported.clear();
}

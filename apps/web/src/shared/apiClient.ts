import { getAccessToken, redirectToSignIn, tryRenewSession } from './authToken';

/**
 * HTTP client backing the generated React Query hooks (orval `mutator`).
 *
 * It exists so that the bearer token is attached in exactly one place instead of being
 * threaded through every call site, and so that an expired token is renewed instead of
 * surfacing as an unexplained 401. It is built on `fetch` on purpose: orval's react-query
 * client defaults to axios, which would add a dependency for no benefit here.
 *
 * The signature is the one orval 8 calls a mutator with — `fetch`'s own, `(url, init)`.
 * Orval 7 passed a single axios-shaped object and left URL building and body encoding to
 * the mutator; the generated code now does both, which is why neither lives here any more.
 */

/** Thrown on any non-2xx response, so React Query treats it as a failure. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly url: string,
    readonly body?: unknown,
  ) {
    super(`${status} on ${url}`);
    this.name = 'ApiError';
  }
}

/**
 * Status behind a React Query failure, or `undefined` when the call never reached the
 * API. The generated hooks type their error as `void`, so screens cannot narrow it
 * themselves.
 */
export function apiErrorStatus(error: unknown): number | undefined {
  return error instanceof ApiError ? error.status : undefined;
}

/**
 * Sends the request the generated code built, with the session token added.
 *
 * The headers go through `Headers` rather than an object spread: `RequestInit.headers`
 * may legitimately be a `Headers` instance or an array of pairs, and spreading either of
 * those silently drops every header.
 */
async function send(url: string, init: RequestInit, token: string | undefined): Promise<Response> {
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  return fetch(url, { ...init, headers });
}

export async function apiClient<T>(url: string, init: RequestInit = {}): Promise<T> {
  let response = await send(url, init, getAccessToken());

  // A 401 usually means the token expired mid-session. Renew once and replay; only
  // send the user back to sign-in when that fails, so a stale token is invisible.
  if (response.status === 401) {
    const renewed = await tryRenewSession();
    if (renewed) {
      response = await send(url, init, getAccessToken());
    } else {
      redirectToSignIn();
    }
  }

  if (!response.ok) {
    const body = await response.text().catch(() => undefined);
    throw new ApiError(response.status, url, body);
  }

  // 204 and empty bodies are legitimate: progress updates and deletions return nothing.
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export default apiClient;

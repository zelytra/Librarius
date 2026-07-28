import { getAccessToken, redirectToSignIn, tryRenewSession } from './authToken';

/**
 * HTTP client backing the generated React Query hooks (orval `mutator`).
 *
 * It exists so that the bearer token is attached in exactly one place instead of being
 * threaded through every call site, and so that an expired token is renewed instead of
 * surfacing as an unexplained 401. It is built on `fetch` on purpose: orval's react-query
 * client defaults to axios, which would add a dependency for no benefit here.
 */

export type ApiRequest = {
  url: string;
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  params?: Record<string, unknown>;
  data?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
};

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

function buildUrl(url: string, params?: Record<string, unknown>): string {
  if (!params) return url;
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null) search.append(key, String(value));
  }
  const query = search.toString();
  return query ? `${url}?${query}` : url;
}

async function send(request: ApiRequest, token: string | undefined): Promise<Response> {
  return fetch(buildUrl(request.url, request.params), {
    method: request.method,
    signal: request.signal,
    headers: {
      ...(request.data !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...request.headers,
    },
    body: request.data !== undefined ? JSON.stringify(request.data) : undefined,
  });
}

export async function apiClient<T>(request: ApiRequest): Promise<T> {
  let response = await send(request, getAccessToken());

  // A 401 usually means the token expired mid-session. Renew once and replay; only
  // send the user back to sign-in when that fails, so a stale token is invisible.
  if (response.status === 401) {
    const renewed = await tryRenewSession();
    if (renewed) {
      response = await send(request, getAccessToken());
    } else {
      redirectToSignIn();
    }
  }

  if (!response.ok) {
    const body = await response.text().catch(() => undefined);
    throw new ApiError(response.status, request.url, body);
  }

  // 204 and empty bodies are legitimate: progress updates and deletions return nothing.
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export default apiClient;

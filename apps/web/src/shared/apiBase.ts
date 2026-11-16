/**
 * Where the API lives, for the builds where it is not same-origin.
 *
 * In the browser it always is: the bundle and the API sit behind one host, so the generated
 * client's relative URLs (`orval.config.ts`, `baseUrl: ''`) resolve by themselves and
 * `VITE_API_BASE_URL` stays empty — the Vite dev proxy and production both depend on that.
 *
 * Inside the Capacitor container the page's origin is the bundle itself (`https://localhost`
 * on Android, `capacitor://localhost` on iOS), so a relative URL is looked for among the
 * packaged assets and comes back as the index page. The native build therefore sets
 * `VITE_API_BASE_URL` — `apps/web/.env.mobile` — and every request goes through the one
 * function below (#527).
 */
const base = (import.meta.env.VITE_API_BASE_URL ?? '').trim().replace(/\/+$/, '');

/** The configured API origin, empty on a web build. */
export const apiBaseUrl = base;

/**
 * The URL to fetch for an API path. Paths are what the generated client and the few
 * hand-written calls produce (`/api/…`); anything already absolute is handed back as it is.
 */
export function apiUrl(path: string): string {
  if (!base || !path.startsWith('/')) return path;
  return `${base}${path}`;
}

import { afterEach, describe, expect, test, vi } from 'vitest';

/**
 * `apiBase` reads the environment once, when the module is first imported, so each case
 * re-imports it with the variable it is about to assert on.
 */
async function loadApiUrl(base: string | undefined) {
  vi.resetModules();
  if (base === undefined) vi.stubEnv('VITE_API_BASE_URL', '');
  else vi.stubEnv('VITE_API_BASE_URL', base);
  return (await import('./apiBase')).apiUrl;
}

afterEach(() => {
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe('apiUrl', () => {
  // The web build is the one that must not change: the API is same-origin there, and a
  // prefix of any kind would break the Vite dev proxy as well as production.
  test('leaves the path alone when no base URL is configured', async () => {
    const apiUrl = await loadApiUrl(undefined);
    expect(apiUrl('/api/library?page=0')).toBe('/api/library?page=0');
  });

  test('prefixes the path with the configured base URL', async () => {
    const apiUrl = await loadApiUrl('https://librarius.zelytra.fr');
    expect(apiUrl('/api/library?page=0')).toBe('https://librarius.zelytra.fr/api/library?page=0');
  });

  // The variable is typed by a human into an env file; a trailing slash would otherwise
  // produce `//api/library`, which nginx answers with a redirect the WebView drops.
  test('tolerates a trailing slash on the base URL', async () => {
    const apiUrl = await loadApiUrl('https://librarius.zelytra.fr/');
    expect(apiUrl('/api/library')).toBe('https://librarius.zelytra.fr/api/library');
  });

  test('ignores surrounding whitespace', async () => {
    const apiUrl = await loadApiUrl('  https://librarius.zelytra.fr  ');
    expect(apiUrl('/api/me')).toBe('https://librarius.zelytra.fr/api/me');
  });

  // Nothing builds such a URL today, but a caller that already has an absolute one — an
  // avatar served by a provider, a signed link — must not have the base glued in front.
  test('leaves an absolute URL untouched', async () => {
    const apiUrl = await loadApiUrl('https://librarius.zelytra.fr');
    expect(apiUrl('https://covers.example/1.jpg')).toBe('https://covers.example/1.jpg');
  });
});

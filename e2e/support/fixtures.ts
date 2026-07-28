import { test as base, expect, type APIRequestContext } from '@playwright/test';
import { BASE_URL } from './config';
import { signIn, type Session } from './session';

type Fixtures = {
  /** The signed-in `alice` session, already injected into the browser context. */
  session: Session;
  /** Direct API access, used to put the account back to a known state — never to assert. */
  api: APIRequestContext;
};

/**
 * Reads the ids of a user-scoped collection.
 *
 * This is arrange-and-clean-up code, not an assertion: it tolerates both the bare array
 * the endpoints return today and the `{ items, page, size, total }` envelope they are
 * about to return, so paginating the API cannot break the suite.
 */
async function collectionIds(api: APIRequestContext, path: string): Promise<string[]> {
  const response = await api.get(path);
  if (!response.ok()) throw new Error(`GET ${path} answered ${response.status()}`);
  const body: unknown = await response.json();
  const items = Array.isArray(body) ? body : ((body as { items?: unknown[] })?.items ?? []);
  return items.map((item) => (item as { id: string }).id).filter(Boolean);
}

/** Empties the collection and the wishlist so each test starts from a known state. */
async function resetAccount(api: APIRequestContext): Promise<void> {
  for (const path of ['/api/library', '/api/wishlist']) {
    // A paginated endpoint only hands back one page at a time: drain it.
    for (let page = 0; page < 20; page++) {
      const ids = await collectionIds(api, path);
      if (ids.length === 0) break;
      for (const id of ids) {
        const response = await api.delete(`${path}/${id}`);
        if (!response.ok()) throw new Error(`DELETE ${path}/${id} answered ${response.status()}`);
      }
    }
  }
}

export const test = base.extend<Fixtures>({
  session: async ({}, use) => {
    await use(await signIn());
  },

  api: async ({ playwright, session }, use) => {
    const api = await playwright.request.newContext({
      baseURL: BASE_URL,
      extraHTTPHeaders: { Authorization: `Bearer ${session.accessToken}` },
    });
    await resetAccount(api);
    await use(api);
    await api.dispose();
  },

  context: async ({ context, session, api }, use) => {
    // Seeded before any page script runs, so the application boots already signed in.
    await context.addInitScript(
      ([key, value]) => {
        window.localStorage.setItem(key, value);
      },
      [session.storageKey, session.storageValue],
    );
    // `api` is requested here only for its side effect: the account is emptied before
    // the first page of the test is opened.
    void api;
    await use(context);
  },
});

export { expect };

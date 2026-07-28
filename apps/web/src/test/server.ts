import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { BUILTIN_CATEGORIES, stats } from './fixtures';

/**
 * MSW server: intercepts API calls at the network level, without mocking the generated
 * client. Tests therefore assert on what the user sees, not on how the data is loaded —
 * they survive a change of HTTP client.
 *
 * The handlers below are the default responses (empty collection). A test that needs
 * something else overrides them with `server.use(...)`.
 */

const BASE = '*/api';

export const defaultHandlers = [
  http.get(`${BASE}/library`, () => HttpResponse.json([])),
  http.get(`${BASE}/wishlist`, () => HttpResponse.json([])),
  http.get(`${BASE}/categories`, () => HttpResponse.json(BUILTIN_CATEGORIES)),
  http.get(`${BASE}/stats`, () => HttpResponse.json(stats())),
  http.get(`${BASE}/goals`, () => HttpResponse.json([])),
  http.get(`${BASE}/catalog/search`, () => HttpResponse.json([])),
  http.get(`${BASE}/catalog/upcoming`, () => HttpResponse.json([])),
  http.get(`${BASE}/me`, () =>
    HttpResponse.json({ id: 'alice', displayName: 'alice', email: 'alice@test.fr', locale: 'fr' })),

  http.post(`${BASE}/library`, () => HttpResponse.json({ id: 'nouveau' }, { status: 201 })),
  http.post(`${BASE}/wishlist`, () => HttpResponse.json({ id: 'nouveau' }, { status: 201 })),
  http.post(`${BASE}/categories`, () => HttpResponse.json({ id: 'cat', code: 'perso' })),
  http.put(`${BASE}/library/:id/rank`, () => HttpResponse.json({ id: 'item-1' })),
  http.put(`${BASE}/library/:id/progress`, () => new HttpResponse(null, { status: 204 })),
  http.delete(`${BASE}/library/:id`, () => new HttpResponse(null, { status: 204 })),
  http.delete(`${BASE}/wishlist/:id`, () => new HttpResponse(null, { status: 204 })),
];

export const server = setupServer(...defaultHandlers);

/** Makes a given path answer 500, to exercise the error states. */
export function failWith(path: string, status = 500) {
  server.use(http.get(`${BASE}${path}`, () => new HttpResponse(null, { status })));
}

export { http, HttpResponse };

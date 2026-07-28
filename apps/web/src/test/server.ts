import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { BUILTIN_CATEGORIES, stats } from './fixtures';

/**
 * Serveur MSW : intercepte les appels de l'API au niveau réseau, sans mocker le client
 * généré. Les tests portent donc sur ce que voit l'utilisateur, pas sur la façon dont
 * les données sont chargées — ils survivent à un changement de client HTTP.
 *
 * Les handlers ci-dessous sont les réponses par défaut (collection vide). Un test qui a
 * besoin d'autre chose les remplace avec `server.use(...)`.
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

/** Fait répondre 500 à un chemin donné, pour éprouver les états d'erreur. */
export function failWith(path: string, status = 500) {
  server.use(http.get(`${BASE}${path}`, () => new HttpResponse(null, { status })));
}

export { http, HttpResponse };

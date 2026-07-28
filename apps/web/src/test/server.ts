import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import {
  BUILTIN_CATEGORIES,
  libraryPage,
  stats,
  timeline,
  wishlistBudget,
  wishlistPage,
} from './fixtures';
import type {
  BookView,
  LibraryItemDto,
  SeriesDetailDto,
  SeriesSummaryDto,
  UpcomingReleaseDto,
  WishlistItemDto,
} from '../api/generated/librarius';

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
  http.get(`${BASE}/library`, () => HttpResponse.json(libraryPage([]))),
  http.get(`${BASE}/library/:id`, () => new HttpResponse(null, { status: 404 })),
  http.get(`${BASE}/wishlist`, () => HttpResponse.json(wishlistPage([]))),
  http.get(`${BASE}/series`, () => HttpResponse.json([])),
  http.get(`${BASE}/series/:id`, () => new HttpResponse(null, { status: 404 })),
  http.put(`${BASE}/series/:id/follow`, () => new HttpResponse(null, { status: 204 })),
  http.delete(`${BASE}/series/:id/follow`, () => new HttpResponse(null, { status: 204 })),
  http.get(`${BASE}/categories`, () => HttpResponse.json(BUILTIN_CATEGORIES)),
  http.get(`${BASE}/stats`, () => HttpResponse.json(stats())),
  http.get(`${BASE}/stats/timeline`, () => HttpResponse.json(timeline())),
  http.get(`${BASE}/goals`, () => HttpResponse.json([])),
  http.get(`${BASE}/catalog/search`, () => HttpResponse.json([])),
  http.get(`${BASE}/catalog/upcoming`, () => HttpResponse.json([])),
  http.get(`${BASE}/releases/upcoming`, () => HttpResponse.json([])),
  http.get(`${BASE}/me`, () =>
    HttpResponse.json({ id: 'alice', displayName: 'alice', email: 'alice@test.fr', locale: 'fr' })),

  http.post(`${BASE}/library`, () => HttpResponse.json({ id: 'nouveau' }, { status: 201 })),
  http.post(`${BASE}/wishlist`, () => HttpResponse.json({ id: 'nouveau' }, { status: 201 })),
  http.post(`${BASE}/categories`, () => HttpResponse.json({ id: 'cat', code: 'perso' })),
  http.put(`${BASE}/goals/:year`, async ({ params, request }) => {
    const body = (await request.json()) as { targetCount?: number; unit?: string };
    return HttpResponse.json({
      id: 'goal-1',
      year: Number(params.year),
      targetCount: body.targetCount,
      unit: body.unit ?? 'BOOKS',
    });
  }),
  http.put(`${BASE}/categories/:id`, () => HttpResponse.json({ id: 'cat', code: 'perso' })),
  http.delete(`${BASE}/categories/:id`, () => new HttpResponse(null, { status: 204 })),
  http.put(`${BASE}/library/:id/rank`, () => HttpResponse.json({ id: 'item-1' })),
  http.put(`${BASE}/library/:id/progress`, () => new HttpResponse(null, { status: 204 })),
  http.put(`${BASE}/library/:id/review`, () => HttpResponse.json({ id: 'item-1' })),
  http.put(`${BASE}/wishlist/:id`, () => HttpResponse.json({ id: 'wish-1' })),
  http.post(`${BASE}/wishlist/:id/acquire`, () =>
    HttpResponse.json({ id: 'item-1' }, { status: 201 })),
  http.delete(`${BASE}/library/:id`, () => new HttpResponse(null, { status: 204 })),
  http.delete(`${BASE}/wishlist/:id`, () => new HttpResponse(null, { status: 204 })),
];

export const server = setupServer(...defaultHandlers);

/** Makes a given path answer 500, to exercise the error states. */
export function failWith(path: string, status = 500) {
  server.use(http.get(`${BASE}${path}`, () => new HttpResponse(null, { status })));
}

/** Field a `sort` value orders on, matching the API. */
const SORT_FIELD: Record<string, 'title' | 'authors' | 'genres'> = {
  title: 'title',
  author: 'authors',
  genre: 'genres',
};

/** Free-text match, over the same three fields as the API. */
function matches(book: BookView | undefined, q: string): boolean {
  const needle = q.trim().toLowerCase();
  return [book?.title, book?.authors, book?.seriesTitle]
    .some((field) => (field ?? '').toLowerCase().includes(needle));
}

/**
 * Serves `/api/library` the way the API does: filter, sort, then slice, and answer with
 * the `{ items, page, size, total }` envelope. Tests keep asserting on what the screen
 * shows, and a screen that forgot to forward a filter now visibly returns too much.
 */
export function libraryReturns(items: LibraryItemDto[]) {
  server.use(http.get(`${BASE}/library`, ({ request }) => {
    const params = new URL(request.url).searchParams;
    let matching = items;

    const kind = params.get('kind');
    if (kind) matching = matching.filter((it) => it.book?.kind === kind);
    const status = params.get('status');
    if (status) matching = matching.filter((it) => it.status === status);
    const rank = params.get('rank');
    if (rank) matching = matching.filter((it) => it.rankCode === rank);
    const minRating = params.get('minRating');
    if (minRating) matching = matching.filter((it) => (it.rating ?? 0) >= Number(minRating));
    const q = params.get('q');
    if (q) matching = matching.filter((it) => matches(it.book, q));

    const field = SORT_FIELD[params.get('sort') ?? 'added'];
    if (field) {
      matching = [...matching].sort((a, b) =>
        (a.book?.[field] ?? '').localeCompare(b.book?.[field] ?? '', 'fr'));
    }

    const page = Number(params.get('page') ?? 0);
    const size = Number(params.get('size') ?? 50);
    return HttpResponse.json({
      items: matching.slice(page * size, page * size + size),
      page,
      size,
      total: matching.length,
    });
  }));
}

/** Serves `/api/series`: the series the user owns a volume of or follows. */
export function seriesReturns(list: SeriesSummaryDto[]) {
  server.use(http.get(`${BASE}/series`, () => HttpResponse.json(list)));
}

/** Serves `/api/releases/upcoming`: the announcements still ahead of the caller. */
export function upcomingReleasesReturn(list: UpcomingReleaseDto[]) {
  server.use(http.get(`${BASE}/releases/upcoming`, () => HttpResponse.json(list)));
}

/**
 * Serves one series on `/api/series/{id}`; any other identifier answers 404 — the answer
 * a series the caller neither owns nor follows gets.
 */
export function seriesDetailReturns(detail: SeriesDetailDto) {
  server.use(http.get(`${BASE}/series/:id`, ({ params }) =>
    params.id === detail.id ? HttpResponse.json(detail) : new HttpResponse(null, { status: 404 })));
}

/** Serves one item on `/api/library/{id}`; any other identifier answers 404. */
export function libraryItemReturns(item: LibraryItemDto) {
  server.use(http.get(`${BASE}/library/:id`, ({ params }) =>
    params.id === item.id ? HttpResponse.json(item) : new HttpResponse(null, { status: 404 })));
}

/** Same as {@link libraryReturns}, for the wishlist. */
export function wishlistReturns(items: WishlistItemDto[]) {
  server.use(http.get(`${BASE}/wishlist`, ({ request }) => {
    const params = new URL(request.url).searchParams;
    let matching = items;

    const kind = params.get('kind');
    if (kind) matching = matching.filter((it) => it.book?.kind === kind);
    const priority = params.get('priority');
    if (priority) matching = matching.filter((it) => it.priority === priority);

    const page = Number(params.get('page') ?? 0);
    const size = Number(params.get('size') ?? 50);
    return HttpResponse.json({
      items: matching.slice(page * size, page * size + size),
      page,
      size,
      total: matching.length,
      // Like `total`, the budget covers the filtered set and not the page.
      budget: wishlistBudget(matching),
    });
  }));
}

export { http, HttpResponse };

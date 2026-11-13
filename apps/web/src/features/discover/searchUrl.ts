import type { GetApiCatalogSearchParams, Kind } from '../../api/generated/librarius';
import { ALL_KINDS } from './medium';

/** Nothing to search: every criterion is empty, and the API would answer an empty list. */
export function isBlankSearch(params: GetApiCatalogSearchParams): boolean {
  return !params.q && !params.isbn && !params.author && !params.year && !params.language
    && !params.publisher;
}

/**
 * The search a screen ran, written into the URL (#435) so that going back from a result's
 * fiche finds it again — the query, the advanced criteria and, through the query cache, the
 * results themselves.
 */
export function searchToUrl(params: GetApiCatalogSearchParams): URLSearchParams {
  const url = new URLSearchParams();
  for (const key of ['q', 'isbn', 'author', 'language', 'publisher'] as const) {
    const value = params[key];
    if (value) url.set(key, value);
  }
  if (params.year != null) url.set('year', String(params.year));
  for (const kind of params.kind ?? []) url.append('kind', kind);
  return url;
}

/** The search a URL describes, or null when it holds none. */
export function searchFromUrl(url: URLSearchParams): GetApiCatalogSearchParams | null {
  const year = Number(url.get('year'));
  const kinds = url.getAll('kind').filter((k): k is Kind => (ALL_KINDS as string[]).includes(k));
  const params: GetApiCatalogSearchParams = {
    kind: kinds.length > 0 ? kinds : undefined,
    q: url.get('q') ?? undefined,
    isbn: url.get('isbn') ?? undefined,
    author: url.get('author') ?? undefined,
    year: url.get('year') && !Number.isNaN(year) ? year : undefined,
    language: url.get('language') ?? undefined,
    publisher: url.get('publisher') ?? undefined,
  };
  return isBlankSearch(params) ? null : params;
}


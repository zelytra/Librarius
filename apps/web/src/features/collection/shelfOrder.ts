import type { LibraryItemDto, SeriesSummaryDto } from '../../api/generated/librarius';

export type KeyedSort = 'title' | 'author' | 'genre';

export function isKeyedSort(sort: string): sort is KeyedSort {
  return sort === 'title' || sort === 'author' || sort === 'genre';
}

/** Lower-cased, and unknown values filed last — the order `GET /api/library` uses. */
function sortKey(value: string | undefined | null): string {
  const v = value?.trim().toLocaleLowerCase('fr');
  return v ? v : '\uffff';
}

/** The first of a free-text genre list, alphabetically: what the API files a title under. */
function firstGenre(genres: string | undefined): string | undefined {
  return genres
    ?.split(/[,;/]/)
    .map((g) => g.trim())
    .filter(Boolean)
    .sort((a, b) => a.localeCompare(b, 'fr'))[0];
}

/**
 * Files the series blocks among the one-shots the API already sorted, by the same key. The
 * titles keep the server's order; a series goes before the first title that sorts after it.
 * While more pages remain, a series sorting after every loaded title waits for its page.
 */
export function interleave(
  series: SeriesSummaryDto[],
  items: LibraryItemDto[],
  sort: KeyedSort,
  hasNextPage: boolean,
): ({ series: SeriesSummaryDto } | { item: LibraryItemDto })[] {
  const seriesKey = (s: SeriesSummaryDto) =>
    sortKey(sort === 'author' ? s.authors : sort === 'genre' ? s.genre : s.title);
  const itemKey = (it: LibraryItemDto) =>
    sortKey(sort === 'author' ? it.book?.authors : sort === 'genre' ? firstGenre(it.book?.genres) : it.book?.title);
  const queue = [...series].sort((a, b) => seriesKey(a).localeCompare(seriesKey(b), 'fr'));
  const out: ({ series: SeriesSummaryDto } | { item: LibraryItemDto })[] = [];
  let next = 0;
  for (const item of items) {
    const key = itemKey(item);
    while (next < queue.length && seriesKey(queue[next]).localeCompare(key, 'fr') <= 0) {
      out.push({ series: queue[next++] });
    }
    out.push({ item });
  }
  if (!hasNextPage) {
    while (next < queue.length) out.push({ series: queue[next++] });
  }
  return out;
}


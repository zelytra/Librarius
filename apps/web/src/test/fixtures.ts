import type {
  CatalogResult,
  CategoryDto,
  LibraryItemDto,
  LibraryPageDto,
  StatsDto,
  WishlistItemDto,
  WishlistPageDto,
} from '../api/generated/librarius';

/** Test data sets, shaped like the real API responses. */

/**
 * Envelope returned by the paged endpoints. `total` defaults to the number of items
 * handed over, which is what a single-page answer looks like; pass it explicitly to
 * simulate a collection larger than the page.
 */
export function libraryPage(items: LibraryItemDto[], total = items.length): LibraryPageDto {
  return { items, page: 0, size: 50, total };
}

export function wishlistPage(items: WishlistItemDto[], total = items.length): WishlistPageDto {
  return { items, page: 0, size: 50, total };
}

export function libraryItem(overrides: Partial<LibraryItemDto> = {}): LibraryItemDto {
  return {
    id: 'item-1',
    status: 'OWNED',
    rating: undefined,
    acquiredAt: '2026-01-15',
    rankCode: undefined,
    book: {
      kind: 'BOOK',
      title: 'Le Nom du vent',
      authors: 'Patrick Rothfuss',
      seriesTitle: 'Chronique du tueur de roi',
      volumeNumber: 1,
      pageCount: 720,
      originalYear: 2007,
      genres: 'Fantasy',
      synopsis: 'Kvothe raconte sa propre légende.',
      coverUrl: undefined,
    },
    ...overrides,
  };
}

export function wishlistItem(overrides: Partial<WishlistItemDto> = {}): WishlistItemDto {
  return {
    id: 'wish-1',
    priority: 'PRIORITY',
    estimatedPrice: 24.9,
    note: 'Édition collector',
    book: {
      kind: 'MANGA',
      title: 'Vinland Saga',
      authors: 'Makoto Yukimura',
      volumeNumber: 27,
      coverUrl: undefined,
    },
    ...overrides,
  };
}

export function catalogResult(overrides: Partial<CatalogResult> = {}): CatalogResult {
  return {
    kind: 'BOOK',
    title: 'Fourth Wing',
    authors: 'Rebecca Yarros',
    provider: 'openlibrary',
    providerRef: 'OL123W',
    year: 2023,
    coverUrl: undefined,
    ...overrides,
  };
}

export const BUILTIN_CATEGORIES: CategoryDto[] = [
  { id: 'cat-or', code: 'or', label: 'Or', color: '#d9b94e', builtin: true },
  { id: 'cat-argent', code: 'argent', label: 'Argent', color: '#b3b7bf', builtin: true },
  { id: 'cat-bronze', code: 'bronze', label: 'Bronze', color: '#c08a5a', builtin: true },
];

export function stats(overrides: Partial<StatsDto> = {}): StatsDto {
  return {
    read: 12,
    reading: 2,
    toRead: 34,
    pagesRead: 4200,
    seriesCount: 5,
    goalTarget: undefined,
    goalCurrent: 12,
    goalUnit: undefined,
    byGenre: [
      { genre: 'Fantasy', count: 8 },
      { genre: 'Science-fiction', count: 4 },
    ],
    ...overrides,
  };
}

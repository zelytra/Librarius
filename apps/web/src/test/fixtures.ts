import type {
  CatalogResult,
  CategoryDto,
  GoalDto,
  LibraryItemDto,
  LibraryPageDto,
  SeriesDetailDto,
  SeriesSummaryDto,
  SeriesVolumeDto,
  StatsDto,
  TimelineDto,
  WishlistBudgetDto,
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
  return { items, page: 0, size: 50, total, budget: wishlistBudget(items) };
}

/** Priority buckets, most urgent first, in the order the API reports them. */
const PRIORITIES = ['PRIORITY', 'SOON', 'SOMEDAY'];

/**
 * The budget the API attaches to a wishlist page. Computed the same way it is
 * server-side — over the whole filtered set, and skipping the priorities no wish carries
 * — so a screen reading the envelope sees in a test what it sees in production.
 */
export function wishlistBudget(items: WishlistItemDto[]): WishlistBudgetDto {
  const sum = (group: WishlistItemDto[]) =>
    group.reduce((total, it) => total + (it.estimatedPrice ?? 0), 0);
  const priced = (group: WishlistItemDto[]) =>
    group.filter((it) => it.estimatedPrice != null).length;

  return {
    total: sum(items),
    pricedCount: priced(items),
    byPriority: PRIORITIES.map((priority) => ({
      priority,
      group: items.filter((it) => (it.priority ?? 'SOON') === priority),
    }))
      .filter(({ group }) => group.length > 0)
      .map(({ priority, group }) => ({
        priority,
        count: group.length,
        pricedCount: priced(group),
        total: sum(group),
      })),
  };
}

export function libraryItem(overrides: Partial<LibraryItemDto> = {}): LibraryItemDto {
  return {
    id: 'item-1',
    status: 'OWNED',
    rating: undefined,
    review: undefined,
    acquiredAt: '2026-01-15',
    rankCode: undefined,
    // A title nobody has opened carries no progress at all.
    progress: undefined,
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

/**
 * Lays a run out the way the API does: a volume the user does not own is a hole when it
 * sits below the highest one they own, and still ahead of them above it.
 */
export function seriesVolumes({
  total,
  owned = [],
  read = [],
}: {
  total: number;
  owned?: number[];
  read?: number[];
}): SeriesVolumeDto[] {
  const highestOwned = Math.max(0, ...owned);
  return Array.from({ length: total }, (_, i) => {
    const volumeNumber = i + 1;
    const isOwned = owned.includes(volumeNumber);
    return {
      volumeNumber,
      libraryItemId: isOwned ? `item-${volumeNumber}` : undefined,
      owned: isOwned,
      read: read.includes(volumeNumber),
      missing: !isOwned && volumeNumber < highestOwned,
      upcoming: !isOwned && volumeNumber > highestOwned,
    };
  });
}

export function seriesSummary(overrides: Partial<SeriesSummaryDto> = {}): SeriesSummaryDto {
  return {
    id: 'series-1',
    kind: 'MANGA',
    title: 'Vinland Saga',
    coverUrl: undefined,
    totalVolumes: 27,
    status: 'ONGOING',
    ownedCount: 3,
    readCount: 1,
    followed: false,
    ...overrides,
  };
}

export function seriesDetail(overrides: Partial<SeriesDetailDto> = {}): SeriesDetailDto {
  return {
    id: 'series-1',
    kind: 'MANGA',
    title: 'Vinland Saga',
    coverUrl: undefined,
    synopsis: 'Thorfinn poursuit sa vengeance.',
    totalVolumes: 5,
    status: 'ONGOING',
    ownedCount: 3,
    readCount: 1,
    followed: false,
    volumes: seriesVolumes({ total: 5, owned: [1, 2, 4], read: [1] }),
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
    goalUnit: undefined,
    goalCurrent: 12,
    byGenre: [
      { code: 'fantasy', genre: 'Fantasy', count: 8 },
      { code: 'science-fiction', genre: 'Science-fiction', count: 4 },
    ],
    ...overrides,
  };
}

/** An empty reading history: what a brand new account's timeline looks like. */
export function timeline(overrides: Partial<TimelineDto> = {}): TimelineDto {
  const year = new Date().getFullYear();
  return {
    from: `${year}-01-01`,
    to: `${year}-12-31`,
    granularity: 'MONTH',
    points: [],
    books: 0,
    pages: 0,
    pagesPerDay: 0,
    daysPerBook: undefined,
    bestPeriod: undefined,
    bestPeriodBooks: 0,
    byAuthor: [],
    byPublisher: [],
    byLanguage: [],
    byRank: [],
    ...overrides,
  };
}

export function goal(overrides: Partial<GoalDto> = {}): GoalDto {
  return {
    id: 'goal-1',
    year: new Date().getFullYear(),
    targetCount: 30,
    unit: 'BOOKS',
    ...overrides,
  };
}

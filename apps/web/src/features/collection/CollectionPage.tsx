import { InfiniteMore } from '../../shared/ui/InfiniteMore';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery, useQueries, useQueryClient } from '@tanstack/react-query';
import { Icon } from '../../shared/ui/Icon';
import { Cover } from '../../shared/ui/Cover';
import { RANK_ICONS, isRankCode } from '../../shared/ui/ranks';
import { Button, Chip, Grid, Screen, ScreenTitle, Segmented } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { LoginGate } from '../../shared/LoginGate';
import { useViewportAtLeast } from '../../shared/ui/breakpoints';
import {
  Kind,
  getApiLibrary,
  getGetApiLibraryQueryKey,
  useDeleteApiLibraryId,
  useGetApiCategories,
  useGetApiSeries,
  useGetApiViews,
  usePutApiViewsIdOrder,
  getGetApiViewsQueryKey,
  type CategoryDto,
  type LibraryViewDto,
  type GetApiLibraryParams,
  type LibraryItemDto,
} from '../../api/generated/librarius';
import { AuthorNames } from '../author/AuthorNames';
import { SeriesBlock } from '../series/SeriesBlock';
import { filterSeries } from '../series/series';
import { OWNERSHIPS, ownershipLabelKey, type Ownership } from '../../shared/statuses';
import styles from './CollectionPage.module.css';
import { interleave, isKeyedSort } from './shelfOrder';
import { ViewsBar, type ViewSettings } from './ViewsBar';
import { ReorderGrid } from './ReorderGrid';
import { SERIES_KEY, useSelection } from '../../shared/selection/useSelection';
import { Selectable } from '../../shared/selection/Selectable';
import { SelectButton, SelectionBar } from '../../shared/selection/SelectionBar';
import { byCategory, inManualOrder, keyOf, mergedOrder, type ShelfEntry } from './views';
import { toast } from '../../shared/ui/toast';
import { STATS_QUERIES } from '../../shared/statsQueries';

/**
 * The whole taxonomy, in the order the shelf switch offers it — same order as the API's
 * `Kind` enum, so the tie-break of "which shelf opens by default" ({@link bestKind}) reads
 * the same top-to-bottom. Growing the taxonomy again only means adding a value here and to
 * the two label maps below; nothing else in this screen names a kind.
 */
const ALL_KINDS: Kind[] = [Kind.BOOK, Kind.MANGA, Kind.COMIC, Kind.GRAPHIC_NOVEL, Kind.AUDIOBOOK];

/** The shelves the kind switch always offers, owned or not (#383). */
const ALWAYS_OFFERED: Kind[] = [Kind.BOOK, Kind.MANGA, Kind.COMIC];

/** i18n key of the segmented-control label for each kind. */
const KIND_LABEL_KEY: Record<Kind, string> = {
  BOOK: 'collection.kinds.book',
  MANGA: 'collection.kinds.manga',
  COMIC: 'collection.kinds.comic',
  GRAPHIC_NOVEL: 'collection.kinds.graphicNovel',
  AUDIOBOOK: 'collection.kinds.audiobook',
};

/** i18n key of the short cover tag for each kind. */
const KIND_TAG_KEY: Record<Kind, string> = {
  BOOK: 'collection.tag.book',
  MANGA: 'collection.tag.manga',
  COMIC: 'collection.tag.comic',
  GRAPHIC_NOVEL: 'collection.tag.graphicNovel',
  AUDIOBOOK: 'collection.tag.audiobook',
};

/** Narrows a free-form kind coming from the API to a known one, `BOOK` when it is not. */
function knownKind(kind: string | undefined): Kind {
  return (ALL_KINDS as string[]).includes(kind ?? '') ? (kind as Kind) : Kind.BOOK;
}
/**
 * The shelf filter is one chip row: `all`, `favorites`, then the code of any category the
 * user has — the three built-ins and their own. `favorites` is the odd one out, since it
 * narrows on the personal rating rather than on a rank category. It sits here all the same
 * — the user reads the row as "which shelf am I looking at", not as "which column is
 * filtered".
 */
type ShelfFilter =
  | { kind: 'all' }
  | { kind: 'toRead' }
  | { kind: 'favorites' }
  | { kind: 'rank'; code: string }
  /** The titles had in a given form (#411): borrowed, digital, wished, not had. */
  | { kind: 'ownership'; ownership: Ownership };

/** The ownerships offered as shelves; "owned" is most of a library and is no shelf of its own. */
const OWNERSHIP_SHELVES: Ownership[] = ['BORROWED', 'EBOOK', 'AUDIOBOOK', 'WISHLIST', 'NOT_OWNED'];

const ALL_SHELVES: ShelfFilter = { kind: 'all' };
/** The to-read pile: titles in the collection, not started yet. */
const TO_READ: ShelfFilter = { kind: 'toRead' };
const FAVORITES: ShelfFilter = { kind: 'favorites' };

/** A shelf as the `shelf` URL parameter spells it: absent for all, `rank:<code>` for a category. */
function shelfToParam(filter: ShelfFilter): string | null {
  if (filter.kind === 'rank') return `rank:${filter.code}`;
  if (filter.kind === 'ownership') return `own:${filter.ownership}`;
  return filter.kind === 'all' ? null : filter.kind;
}

function shelfFromParam(value: string | null): ShelfFilter {
  if (value === 'toRead') return TO_READ;
  if (value === 'favorites') return FAVORITES;
  if (value?.startsWith('rank:') && value.length > 5) return { kind: 'rank', code: value.slice(5) };
  const owned = value?.startsWith('own:') ? (value.slice(4) as Ownership) : undefined;
  if (owned && OWNERSHIP_SHELVES.includes(owned)) return { kind: 'ownership', ownership: owned };
  return ALL_SHELVES;
}

function sameShelf(a: ShelfFilter, b: ShelfFilter): boolean {
  if (a.kind === 'rank' && b.kind === 'rank') return a.code === b.code;
  if (a.kind === 'ownership' && b.kind === 'ownership') return a.ownership === b.ownership;
  return a.kind === b.kind;
}
/** Ordering values understood by `GET /api/library`; sent through as they are. */
type SortBy = 'recent' | 'added' | 'title' | 'author' | 'genre' | 'rating' | 'manual';
/** The two ways of reading the collection: title by title, or run by run. */
type View = 'grouped' | 'flat';

/** Number of titles fetched per request — one shelf worth of covers. */
const PAGE_SIZE = 24;

/** Rating from which a title counts as a favourite. */
const FAVORITE_RATING = 4;

/** Delay before a keystroke turns into a request. */
const SEARCH_DEBOUNCE_MS = 300;

/** Sort chips, in display order, each with the key of its label. */
const SORTS: { id: SortBy; labelKey: string }[] = [
  { id: 'recent', labelKey: 'collection.sorts.recent' },
  { id: 'added', labelKey: 'collection.sorts.added' },
  { id: 'title', labelKey: 'collection.sorts.title' },
  { id: 'author', labelKey: 'collection.sorts.author' },
  { id: 'genre', labelKey: 'collection.sorts.genre' },
  { id: 'rating', labelKey: 'collection.sorts.rating' },
];

/** The date of the last thing that happened to a title: finished, started or added. */
function activityOf(item: LibraryItemDto): string {
  return [item.progress?.finishedAt, item.progress?.startedAt, item.acquiredAt]
    .filter((d): d is string => Boolean(d))
    .sort()
    .pop() ?? '';
}

function CoverTile({
  item,
  rankColor,
  onDelete,
  onOpen,
  selecting = false,
}: {
  item: LibraryItemDto;
  /** While several titles are being picked (#468): the remove button would only get in the way. */
  selecting?: boolean;
  /** Colour of the category the title is filed under, absent when it carries no rank. */
  rankColor?: string;
  onDelete: () => void;
  onOpen: () => void;
}) {
  const { t } = useTranslation();
  const b = item.book!;
  // The built-ins own an icon; a custom category gets the generic medal.
  const rankIcon = isRankCode(item.rankCode) ? RANK_ICONS[item.rankCode] : 'military_tech';
  const tag = b.volumeNumber
    ? t('collection.volumeShort', { number: b.volumeNumber })
    : t(KIND_TAG_KEY[knownKind(b.kind)]);
  return (
    // The actual grid item: a plain wrapper rather than a prop on Cover, so it can carry
    // the min-width the grid track needs without Cover knowing it sits in one. See `.cell`
    // in CollectionPage.module.css for why that matters below --bp-tablet.
    <div className={styles.cell}>
      <Cover
        variant="tile"
        title={b.title ?? '—'}
        imageUrl={b.coverUrl}
        tag={tag}
        caption={<AuthorNames text={b.authors} />}
        onClick={onOpen}
      >
        {/* How the reader has the book, when it is not a copy on the shelves (#411). */}
        {item.ownership && item.ownership !== 'OWNED' && (
          <span className={styles.ownershipBadge} title={t(ownershipLabelKey(item.ownership))}>
            <Icon
              name={OWNERSHIPS.find((o) => o.id === item.ownership)?.icon ?? 'info'}
              size={13}
              color="var(--overlay-ink)"
            />
            {t(ownershipLabelKey(item.ownership))}
          </span>
        )}
        {rankColor && (
          // The medal colour depends on the rank, so it stays on the element.
          <span className={styles.rankBadge} style={{ background: rankColor }}>
            <Icon name={rankIcon} size={14} fill color="var(--on-accent)" />
          </span>
        )}
        {!selecting && (
        <button
          onClick={(e) => {
            e.stopPropagation();
            onDelete();
          }}
          aria-label={t('common.remove')}
          className={styles.removeButton}
        >
          <Icon name="delete" size={14} color="var(--rose)" />
        </button>
        )}
      </Cover>
    </div>
  );
}

function CollectionContent() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const open = (it: LibraryItemDto) => navigate(`/detail/${it.id}`, { state: { item: it } });

  // One lightweight request per kind, `size: 1` so only the `total` of the envelope is
  // paid for — no dedicated endpoint exists to ask "which kinds does this user own", the
  // answer is derived from data `/api/library` already exposes, one filter at a time.
  const kindCounts = useQueries({
    queries: ALL_KINDS.map((kind) => ({
      queryKey: getGetApiLibraryQueryKey({ kind, size: 1 }),
      queryFn: () => getApiLibrary({ kind, size: 1 }),
    })),
  });
  const totalOf = (kind: Kind) => kindCounts[ALL_KINDS.indexOf(kind)]?.data?.total ?? 0;

  // The shelf the user owns the most of; the first kind of the taxonomy wins a tie, and is
  // also what an account that owns nothing yet lands on — never a hardcoded 'BOOK'.
  let bestKind: Kind = ALL_KINDS[0];
  let bestCount = -1;
  for (const kind of ALL_KINDS) {
    const count = totalOf(kind);
    if (count > bestCount) {
      bestCount = count;
      bestKind = kind;
    }
  }

  // The shelf's state lives in the URL (#389): opening a book and coming back lands on the
  // same kind, shelf, sort, view and search, and a filtered shelf can be bookmarked. Each
  // change replaces the entry rather than pushing one, so Back leaves the library at once.
  const [params, setParams] = useSearchParams();
  const setParam = (key: string, value: string | null) =>
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (value) next.set(key, value);
        else next.delete(key);
        return next;
      },
      { replace: true },
    );

  // Once the counts resolve they may move the default shelf under the user's feet — unless
  // the user has already picked one by hand, in which case that choice wins.
  const urlKind = params.get('kind');
  const manualKind = urlKind && (ALL_KINDS as string[]).includes(urlKind) ? (urlKind as Kind) : null;
  const collType = manualKind ?? bestKind;
  const setCollType = (kind: Kind) => setParam('kind', kind);

  // Books, manga and comics are always offered (#383): they are the shelves a reader files
  // by, and an import that cannot tell a manga from a book must not take the switch away.
  // The two rarer kinds join only once the reader owns one.
  const kindOptions = ALL_KINDS.filter((kind) => ALWAYS_OFFERED.includes(kind) || totalOf(kind) > 0);

  const shelfFilter = shelfFromParam(params.get('shelf'));
  const setShelfFilter = (filter: ShelfFilter) => setParam('shelf', shelfToParam(filter));
  // Latest activity first — read, started or added — the way a reader thinks of their shelf.
  // The saved views (#451): the one in force is named by the `v` parameter.
  const { data: views = [] } = useGetApiViews();
  const activeView = views.find((v) => v.id === params.get('v')) ?? null;
  const urlSort = params.get('sort');
  // The manual order is a view's: without one in force, it falls back to the default.
  const sortBy: SortBy = SORTS.some((s) => s.id === urlSort) || (urlSort === 'manual' && activeView)
    ? (urlSort as SortBy)
    : 'recent';
  const byCategoryOn = params.get('cat') === '1';
  const [arranging, setArranging] = useState(false);
  // Picking several titles to act on together (#457).
  const selection = useSelection();
  const setSortBy = (sort: SortBy) => setParam('sort', sort === 'recent' ? null : sort);
  const search = params.get('q') ?? '';
  const [searchInput, setSearchInput] = useState(search);
  const view: View = params.get('view') === 'flat' ? 'flat' : 'grouped';
  const setView = (next: View) => setParam('view', next === 'flat' ? 'flat' : null);
  // By series by default: a run is one block, a one-shot one tile. A category or the
  // favourites shelf filters titles one by one — a rank or a rating belongs to a volume, not
  // to its run — so those shelves always list the titles themselves.
  const grouped = view === 'grouped' && shelfFilter.kind === 'all';
  // Same shape decision as the Home shelves: whether the chip rows scroll or wrap is not
  // something a track-size token can answer, so it is read once from the shared breakpoint
  // rather than declared as a media query here.
  const wide = useViewportAtLeast('tablet');
  const chipRowClass = wide ? `${styles.chipRow} ${styles.rowWide}` : `scroll-x ${styles.chipRow}`;
  const sortRowClass = wide ? `${styles.sortRow} ${styles.rowWide}` : `scroll-x ${styles.sortRow}`;

  // One request per pause in the typing rather than one per keystroke.
  useEffect(() => {
    const handle = setTimeout(() => {
      const q = searchInput.trim();
      if (q !== search) setParam('q', q || null);
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(handle);
    // Only the typing drives it; `search` and `setParam` follow from the URL.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchInput]);

  // The shelf is a window onto the server-side result set: filtering, sorting and
  // slicing all happen in the database, so a 5000-title collection costs no more to
  // display than a 50-title one. Each combination of criteria is its own cache entry,
  // which is what makes going back to a filter instantaneous.
  const criteria: GetApiLibraryParams = {
    size: PAGE_SIZE,
    sort: sortBy,
    kind: collType,
    rank: shelfFilter.kind === 'rank' ? shelfFilter.code : undefined,
    status: shelfFilter.kind === 'toRead' ? 'TO_READ' : undefined,
    minRating: shelfFilter.kind === 'favorites' ? FAVORITE_RATING : undefined,
    ownership: shelfFilter.kind === 'ownership' ? shelfFilter.ownership : undefined,
    q: search || undefined,
    view: sortBy === 'manual' ? activeView?.id : undefined,
    // Grouped, the volumes of a run are drawn as its series block: only the one-shots remain.
    standalone: grouped || undefined,
  };

  const {
    data,
    isPending: loading,
    isError,
    refetch,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
  } = useInfiniteQuery({
    // Marked so an infinite result never lands under the key of a plain page query,
    // while staying under the `/api/library` prefix the mutations invalidate.
    queryKey: [...getGetApiLibraryQueryKey(criteria), 'infinite'],
    queryFn: ({ pageParam }) => getApiLibrary({ ...criteria, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (last) => {
      const loaded = ((last.page ?? 0) + 1) * (last.size ?? PAGE_SIZE);
      return loaded < (last.total ?? 0) ? (last.page ?? 0) + 1 : undefined;
    },
  });

  const items = useMemo(() => data?.pages.flatMap((p) => p.items ?? []) ?? [], [data]);
  // Every page carries the same total; the first one is enough.
  const total = data?.pages[0]?.total ?? 0;

  const { mutate: removeItem } = useDeleteApiLibraryId({
    mutation: {
      onSuccess: () => {
        toast.info(t('toasts.removed'), 'delete');
        // Removing a title also changes the counters and the home carousels.
        void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
        void queryClient.invalidateQueries(STATS_QUERIES);
      },
      onError: () => toast.error(t('toasts.failed')),
    },
  });

  const remove = (id: string) => removeItem({ id });

  // The series blocks are not a grouping of the loaded page: `/api/series` knows the whole
  // run — its announced total, what is owned of it and what is read — which no amount of
  // grouping over the titles fetched so far could reconstruct.
  const {
    data: allSeries = [],
    isPending: seriesLoading,
    isError: seriesFailed,
    refetch: refetchSeries,
  } = useGetApiSeries({ query: { enabled: grouped } });

  const shelf = useMemo(
    () =>
      grouped
        ? filterSeries(allSeries, { kind: collType, search, sort: 'title' }).filter(
            // A series only followed, with nothing of it on the shelf, is not in the collection.
            (s) => (s.ownedCount ?? 0) > 0,
          )
        : [],
    [allSeries, collType, search, grouped],
  );

  // Grouped and by latest activity, the series blocks take their place among the one-shots
  // by the date of what last happened in them, instead of all leading the shelf. A series
  // older than every title loaded so far waits for the page that reaches its date.
  const mixed = useMemo(() => {
    if (!grouped || sortBy !== 'recent') return null;
    const dated = items.map((it) => ({ item: it, date: activityOf(it) }));
    const floor = hasNextPage && dated.length > 0 ? dated[dated.length - 1].date : '';
    const series = shelf
      .map((s) => ({ series: s, date: s.lastActivity ?? '' }))
      .filter((s) => !hasNextPage || s.date >= floor);
    return [...series, ...dated].sort((a, b) => b.date.localeCompare(a.date));
  }, [grouped, sortBy, items, shelf, hasNextPage]);

  // Grouped and by title, author or genre (#387): the series blocks are filed among the
  // one-shots by the same key, instead of all leading the shelf in title order — which made
  // the author and genre sorts look like they did nothing on a library of runs.
  const interleaved = useMemo(() => {
    if (!grouped || !isKeyedSort(sortBy)) return null;
    return interleave(shelf, items, sortBy, hasNextPage);
  }, [grouped, sortBy, items, shelf, hasNextPage]);

  const showLoading = loading || (grouped && seriesLoading);
  const showError = isError || (grouped && seriesFailed);
  const isEmpty = !showLoading && !showError && shelf.length === 0 && items.length === 0;

  // The shelves are the categories the user actually has, not the three built-ins the
  // screen used to name itself: a category created on /categories has to become a filter
  // here without anybody touching this file again. Their labels come from the API, which
  // is where the French wording of Or / Argent / Bronze lives.
  const { data: categories = [] } = useGetApiCategories();

  const shelves: { key: string; filter: ShelfFilter; name: string; dot?: string }[] = [
    { key: 'all', filter: ALL_SHELVES, name: t('collection.ranks.all') },
    { key: 'toRead', filter: TO_READ, name: t('collection.ranks.toRead') },
    { key: 'favorites', filter: FAVORITES, name: t('collection.ranks.favorites') },
    ...OWNERSHIP_SHELVES.map((ownership) => ({
      key: `own-${ownership}`,
      filter: { kind: 'ownership' as const, ownership },
      name: t(ownershipLabelKey(ownership)),
    })),
    ...categories.map((c: CategoryDto) => ({
      key: c.id!,
      filter: { kind: 'rank' as const, code: c.code! },
      name: c.label ?? '',
      dot: c.color ?? undefined,
    })),
  ];

  /** Colour of each category, by code, to draw the medal on a cover. */
  const rankColors = new Map(categories.map((c: CategoryDto) => [c.code, c.color]));

  // A view brings the whole layout back at once (#451); "Toute la bibliothèque" clears it.
  const selectView = (view: LibraryViewDto | null) => {
    setArranging(false);
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        for (const key of ['v', 'kind', 'shelf', 'sort', 'view', 'cat']) next.delete(key);
        if (view?.id) {
          next.set('v', view.id);
          if (view.kind) next.set('kind', view.kind);
          if (view.shelf) next.set('shelf', view.shelf);
          if (view.sort && view.sort !== 'recent') next.set('sort', view.sort);
          if (view.layout === 'flat') next.set('view', 'flat');
          if (view.groupByCategory) next.set('cat', '1');
        }
        return next;
      },
      { replace: true },
    );
  };
  const currentSettings: ViewSettings = {
    kind: collType,
    shelf: shelfToParam(shelfFilter) ?? undefined,
    sort: sortBy,
    layout: view,
    groupByCategory: byCategoryOn,
  };

  const entries: ShelfEntry[] = useMemo(() => {
    const base: ShelfEntry[] =
      mixed ?? interleaved ?? [...shelf.map((s) => ({ series: s })), ...items.map((it) => ({ item: it }))];
    return sortBy === 'manual' && activeView ? inManualOrder(base, activeView.order ?? []) : base;
  }, [mixed, interleaved, shelf, items, sortBy, activeView]);
  const entryByKey = useMemo(() => new Map(entries.map((e) => [keyOf(e), e])), [entries]);
  const entryKeys = useMemo(() => entries.map(keyOf), [entries]);

  const { mutate: saveOrder } = usePutApiViewsIdOrder({
    mutation: {
      onSuccess: (saved) => {
        // The order is in: the view's cache entry takes it at once, the page re-sorts.
        queryClient.setQueryData<LibraryViewDto[]>(getGetApiViewsQueryKey(), (old) =>
          old?.map((v) => (v.id === saved.id ? saved : v)));
        void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
      },
      onError: () => toast.error(t('toasts.failed')),
    },
  });

  const renderEntry = (entry: ShelfEntry) =>
    'series' in entry ? (
      // A series picked whole stands for every volume of it held (#467).
      <Selectable
        key={entry.series.id}
        id={entry.series.id ? SERIES_KEY + entry.series.id : undefined}
        label={t('selection.pickSeries', { title: entry.series.title ?? '' })}
        selection={selection}
      >
        <SeriesBlock series={entry.series} />
      </Selectable>
    ) : (
      <Selectable
        key={entry.item.id}
        id={entry.item.id}
        label={t('selection.pick', { title: entry.item.book?.title ?? '' })}
        selection={selection}
      >
        <CoverTile
          item={entry.item}
          rankColor={entry.item.rankCode ? rankColors.get(entry.item.rankCode) : undefined}
          onOpen={() => open(entry.item)}
          onDelete={() => void remove(entry.item.id!)}
          selecting={selection.active}
        />
      </Selectable>
    );

  return (
    <>
      <ViewsBar views={views} activeId={activeView?.id ?? null} current={currentSettings} onSelect={selectView} />

      <div className={styles.kindSwitch}>
        <Segmented<Kind>
          value={collType}
          onChange={setCollType}
          options={kindOptions.map((kind) => ({ id: kind, label: t(KIND_LABEL_KEY[kind]) }))}
        />
      </div>

      <div className={styles.searchBar}>
        <Icon name="search" size={21} color="var(--faint)" />
        <input
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder={t('collection.searchPlaceholder')}
          aria-label={t('collection.searchPlaceholder')}
          className={styles.searchInput}
        />
      </div>

      {/* The shelves filter titles one by one: picking one lists the titles themselves,
          whichever view was on — a rank or a rating belongs to a volume, not to a run. */}
      <div className={chipRowClass}>
        {shelves.map((s) => (
          <Chip
            key={s.key}
            selected={sameShelf(shelfFilter, s.filter)}
            dotColor={s.dot}
            onClick={() => setShelfFilter(s.filter)}
          >
            {s.name}
          </Chip>
        ))}
        {/* The way out of the row: the shelves are editable, and this is where the user
            is standing when they realise they want another one. */}
        <button
          onClick={() => navigate('/categories')}
          className={styles.manageShelves}
        >
          <Icon name="tune" size={15} color="var(--accent-deep)" />
          {t('collection.ranks.manage')}
        </button>
      </div>

      <div className={styles.countRow}>
        <span className={styles.count}>
          {grouped && shelf.length > 0
            ? t('collection.groupedTotal', { count: shelf.length, oneShots: total })
            : t('collection.total', { total })}
        </span>
        <div className={styles.viewSwitch}>
          {!arranging && <SelectButton selection={selection} />}
          {sortBy === 'manual' && activeView && (
            <Button variant={arranging ? 'primary' : 'secondary'} size="compact" onClick={() => setArranging(!arranging)}>
              <Icon name={arranging ? 'check_circle' : 'drag_indicator'} size={16}
                color={arranging ? 'var(--on-accent)' : 'var(--accent-deep)'} />
              {t(arranging ? 'views.done' : 'views.arrange')}
            </Button>
          )}
          <Chip selected={byCategoryOn} onClick={() => setParam('cat', byCategoryOn ? null : '1')}>
            {t('views.byCategoryShort')}
          </Chip>
          <Segmented<View>
            value={view}
            onChange={setView}
            options={[
              { id: 'grouped', label: t('collection.grouped') },
              { id: 'flat', label: t('collection.flat') },
            ]}
          />
        </div>
      </div>

      <div className={sortRowClass}>
        <span className={styles.sortLabel}>{t('collection.sortBy')}</span>
        {SORTS.map((s) => (
          <Chip key={s.id} selected={sortBy === s.id} onClick={() => setSortBy(s.id)}>
            {t(s.labelKey)}
          </Chip>
        ))}
        {/* The order the reader arranged by hand belongs to a view (#451). */}
        {activeView && (
          <Chip selected={sortBy === 'manual'} onClick={() => setSortBy('manual')}>
            {t('collection.sorts.manual')}
          </Chip>
        )}
      </div>

      {showLoading && <Loading />}

      {showError && (
        <ErrorState
          message={t(seriesFailed ? 'series.listError' : 'collection.error')}
          onRetry={() => void (seriesFailed ? refetchSeries() : refetch())}
        />
      )}

      {isEmpty && (
        <EmptyState
          icon="bookmark_add"
          className={styles.empty}
          title={t('collection.empty.title')}
          description={t('collection.empty.description')}
          action={
            <Button variant="secondary" onClick={() => navigate('/discover')}>
              {t('collection.empty.action')}
            </Button>
          }
        />
      )}

      {entries.length > 0 && arranging && activeView?.id && (
        <ReorderGrid
          keys={entryKeys}
          render={(key) => {
            const entry = entryByKey.get(key);
            return entry ? renderEntry(entry) : null;
          }}
          onCommit={(keys) => saveOrder({ id: activeView.id!, data: { keys: mergedOrder(keys, activeView.order ?? []) } })}
        />
      )}

      {entries.length > 0 && !arranging && byCategoryOn && (
        // Split by category (#451): a series stays one block, on the shelf most of its volumes
        // are filed under.
        byCategory(entries, categories).map((section) => (
          <section key={section.code ?? 'none'} className={styles.categorySection}>
            <h3 className={styles.categoryTitle}>
              {section.color && <span className={styles.categoryDot} style={{ background: section.color }} />}
              {section.label ?? t('views.uncategorised')}
              <span className={styles.categoryCount}>{section.entries.length}</span>
            </h3>
            <Grid>{section.entries.map(renderEntry)}</Grid>
          </section>
        ))
      )}

      {entries.length > 0 && !arranging && !byCategoryOn && (
          // The shared cover grid (shared/ui/primitives.tsx): three fixed columns on a
          // phone, as many as the width holds past --bp-tablet — see the Grid block of
          // tokens.css for the reasoning behind the track sizes. Grouped, the series blocks
          // lead and the one-shots follow, each in the order the sort asks for.
          <Grid>{entries.map(renderEntry)}</Grid>
      )}

      <SelectionBar selection={selection} />

      <InfiniteMore
        className={styles.loadMore}
        hasMore={!!hasNextPage}
        loading={isFetchingNextPage}
        onMore={() => void fetchNextPage()}
        label={t('collection.loadMore', { loaded: items.length, total })}
      />
    </>
  );
}

export function CollectionPage() {
  const { t } = useTranslation();
  return (
    <Screen>
      <ScreenTitle className={styles.title}>{t('collection.title')}</ScreenTitle>
      <LoginGate prompt={t('auth.prompts.collection')}>
        <CollectionContent />
      </LoginGate>
    </Screen>
  );
}

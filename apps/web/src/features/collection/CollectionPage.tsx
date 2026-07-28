import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { Icon } from '../../shared/ui/Icon';
import { Cover } from '../../shared/ui/Cover';
import { RANK_COLORS, RANK_ICONS, isRankCode } from '../../shared/ui/ranks';
import { Button, Chip, Screen, ScreenTitle, Segmented } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { LoginGate } from '../../shared/LoginGate';
import {
  getApiLibrary,
  getGetApiLibraryQueryKey,
  getGetApiStatsQueryKey,
  useDeleteApiLibraryId,
  useGetApiSeries,
  type GetApiLibraryParams,
  type LibraryItemDto,
} from '../../api/generated/librarius';
import { SeriesList } from '../series/SeriesList';
import { SERIES_SORTS, filterSeries, type SeriesSort } from '../series/series';
import styles from './CollectionPage.module.css';

type Kind = 'BOOK' | 'MANGA';
/**
 * The shelf filter is one chip row, and `favorites` is the odd one out: it narrows on the
 * personal rating rather than on a rank category. It sits here all the same — the user
 * reads the row as "which shelf am I looking at", not as "which column is filtered".
 */
type ShelfFilter = 'all' | 'or' | 'argent' | 'bronze' | 'favorites';
/** Ordering values understood by `GET /api/library`; sent through as they are. */
type SortBy = 'added' | 'title' | 'author' | 'genre' | 'rating';
/** The two ways of reading the collection: title by title, or run by run. */
type View = 'flat' | 'series';

/** Number of titles fetched per request — one shelf worth of covers. */
const PAGE_SIZE = 24;

/** Rating from which a title counts as a favourite. */
const FAVORITE_RATING = 4;

/** Delay before a keystroke turns into a request. */
const SEARCH_DEBOUNCE_MS = 300;

/** Sort chips, in display order, each with the key of its label. */
const SORTS: { id: SortBy; labelKey: string }[] = [
  { id: 'added', labelKey: 'collection.sorts.added' },
  { id: 'title', labelKey: 'collection.sorts.title' },
  { id: 'author', labelKey: 'collection.sorts.author' },
  { id: 'genre', labelKey: 'collection.sorts.genre' },
  { id: 'rating', labelKey: 'collection.sorts.rating' },
];

function CoverTile({ item, onDelete, onOpen }: { item: LibraryItemDto; onDelete: () => void; onOpen: () => void }) {
  const { t } = useTranslation();
  const b = item.book!;
  const rank = isRankCode(item.rankCode) ? item.rankCode : null;
  const tag = b.volumeNumber
    ? t('collection.volumeShort', { number: b.volumeNumber })
    : t(b.kind === 'MANGA' ? 'collection.tag.manga' : 'collection.tag.book');
  return (
    <Cover
      variant="tile"
      title={b.title ?? '—'}
      imageUrl={b.coverUrl}
      tag={tag}
      caption={b.authors}
      onClick={onOpen}
    >
      {rank && (
        // The medal colour depends on the rank, so it stays on the element.
        <span className={styles.rankBadge} style={{ background: RANK_COLORS[rank] }}>
          <Icon name={RANK_ICONS[rank]} size={14} fill color="var(--on-accent)" />
        </span>
      )}
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
    </Cover>
  );
}

function CollectionContent() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const open = (it: LibraryItemDto) => navigate(`/detail/${it.id}`, { state: { item: it } });
  const [collType, setCollType] = useState<Kind>('BOOK');
  const [shelfFilter, setShelfFilter] = useState<ShelfFilter>('all');
  const [sortBy, setSortBy] = useState<SortBy>('added');
  const [seriesSort, setSeriesSort] = useState<SeriesSort>('progress');
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [view, setView] = useState<View>('flat');
  const series = view === 'series';

  // One request per pause in the typing rather than one per keystroke.
  useEffect(() => {
    const handle = setTimeout(() => setSearch(searchInput.trim()), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [searchInput]);

  // The shelf is a window onto the server-side result set: filtering, sorting and
  // slicing all happen in the database, so a 5000-title collection costs no more to
  // display than a 50-title one. Each combination of criteria is its own cache entry,
  // which is what makes going back to a filter instantaneous.
  const criteria: GetApiLibraryParams = {
    size: PAGE_SIZE,
    sort: sortBy,
    kind: collType,
    rank: shelfFilter === 'all' || shelfFilter === 'favorites' ? undefined : shelfFilter,
    minRating: shelfFilter === 'favorites' ? FAVORITE_RATING : undefined,
    q: search || undefined,
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
    // The Series view reads `/api/series`, not the pages of the collection.
    enabled: !series,
  });

  const items = useMemo(() => data?.pages.flatMap((p) => p.items ?? []) ?? [], [data]);
  // Every page carries the same total; the first one is enough.
  const total = data?.pages[0]?.total ?? 0;

  const { mutate: removeItem } = useDeleteApiLibraryId({
    mutation: {
      onSuccess: () => {
        // Removing a title also changes the counters and the home carousels.
        void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
      },
    },
  });

  const remove = (id: string) => removeItem({ id });

  // The Series view is not a grouping of the loaded page: `/api/series` knows the whole
  // run — its announced total, what is owned of it and what is read — which no amount of
  // grouping over the titles fetched so far could reconstruct.
  const {
    data: allSeries = [],
    isPending: seriesLoading,
    isError: seriesFailed,
    refetch: refetchSeries,
  } = useGetApiSeries({ query: { enabled: series } });

  const shelf = useMemo(
    () => filterSeries(allSeries, { kind: collType, search, sort: seriesSort }),
    [allSeries, collType, search, seriesSort],
  );

  // The two views share the kind switch and the search box, so each one reads the state
  // of whichever query is actually feeding the screen.
  const showLoading = series ? seriesLoading : loading;
  const showError = series ? seriesFailed : isError;
  const isEmpty = !showLoading && !showError && (series ? shelf.length === 0 : items.length === 0);

  const cats: { id: ShelfFilter; name: string; dot?: string }[] = [
    { id: 'all', name: t('collection.ranks.all') },
    { id: 'favorites', name: t('collection.ranks.favorites') },
    { id: 'or', name: t('collection.ranks.gold'), dot: RANK_COLORS.or },
    { id: 'argent', name: t('collection.ranks.silver'), dot: RANK_COLORS.argent },
    { id: 'bronze', name: t('collection.ranks.bronze'), dot: RANK_COLORS.bronze },
  ];

  return (
    <>
      <div className={styles.kindSwitch}>
        <Segmented<Kind>
          value={collType}
          onChange={setCollType}
          options={[
            { id: 'BOOK', label: t('common.books') },
            { id: 'MANGA', label: t('common.mangas') },
          ]}
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

      {/* A rank and a rating both belong to a title, not to a run: these chips would
          filter nothing in the Series view. They keep their state so switching back
          restores the shelf the user was on. */}
      {!series && (
        <div className={`scroll-x ${styles.chipRow}`}>
          {cats.map((c) => (
            <Chip key={c.id} selected={shelfFilter === c.id} dotColor={c.dot} onClick={() => setShelfFilter(c.id)}>
              {c.name}
            </Chip>
          ))}
        </div>
      )}

      <div className={styles.countRow}>
        <span className={styles.count}>
          {series
            ? t('collection.seriesTotal', { count: shelf.length })
            : t('collection.total', { total })}
        </span>
        <div className={styles.viewSwitch}>
          <Segmented<View>
            value={view}
            onChange={setView}
            options={[
              { id: 'flat', label: t('collection.list') },
              { id: 'series', label: t('collection.series') },
            ]}
          />
        </div>
      </div>

      <div className={`scroll-x ${styles.sortRow}`}>
        <span className={styles.sortLabel}>{t('collection.sortBy')}</span>
        {series
          ? SERIES_SORTS.map((s) => (
              <Chip key={s.id} selected={seriesSort === s.id} onClick={() => setSeriesSort(s.id)}>
                {t(s.labelKey)}
              </Chip>
            ))
          : SORTS.map((s) => (
              <Chip key={s.id} selected={sortBy === s.id} onClick={() => setSortBy(s.id)}>
                {t(s.labelKey)}
              </Chip>
            ))}
      </div>

      {showLoading && <Loading />}

      {showError && (
        <ErrorState
          message={t(series ? 'series.listError' : 'collection.error')}
          onRetry={() => void (series ? refetchSeries() : refetch())}
        />
      )}

      {isEmpty && (
        <EmptyState
          icon={series ? 'collections_bookmark' : 'bookmark_add'}
          className={styles.empty}
          title={t(series ? 'series.empty.title' : 'collection.empty.title')}
          description={t(series ? 'series.empty.description' : 'collection.empty.description')}
          action={
            <Button variant="secondary" onClick={() => navigate('/discover')}>
              {t('collection.empty.action')}
            </Button>
          }
        />
      )}

      {series ? (
        shelf.length > 0 && <SeriesList series={shelf} />
      ) : (
        items.length > 0 && (
          <div className={styles.grid}>
            {items.map((it) => (
              <CoverTile key={it.id} item={it} onOpen={() => open(it)} onDelete={() => void remove(it.id!)} />
            ))}
          </div>
        )
      )}

      {!series && hasNextPage && (
        <div className={styles.loadMore}>
          <Button variant="secondary" disabled={isFetchingNextPage} onClick={() => void fetchNextPage()}>
            {isFetchingNextPage
              ? t('common.loading')
              : t('collection.loadMore', { loaded: items.length, total })}
          </Button>
        </div>
      )}
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

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
  type GetApiLibraryParams,
  type LibraryItemDto,
} from '../../api/generated/librarius';
import styles from './CollectionPage.module.css';

type Kind = 'BOOK' | 'MANGA';
type RankFilter = 'all' | 'or' | 'argent' | 'bronze';
/** Ordering values understood by `GET /api/library`; sent through as they are. */
type SortBy = 'added' | 'title' | 'author' | 'genre';

/** Number of titles fetched per request — one shelf worth of covers. */
const PAGE_SIZE = 24;

/** Delay before a keystroke turns into a request. */
const SEARCH_DEBOUNCE_MS = 300;

/** Width of a cover inside a series shelf, where the grid no longer applies. */
const SERIES_COVER_WIDTH = 84;

/** Sort chips, in display order, each with the key of its label. */
const SORTS: { id: SortBy; labelKey: string }[] = [
  { id: 'added', labelKey: 'collection.sorts.added' },
  { id: 'title', labelKey: 'collection.sorts.title' },
  { id: 'author', labelKey: 'collection.sorts.author' },
  { id: 'genre', labelKey: 'collection.sorts.genre' },
];

function CoverTile({ item, onDelete, onOpen, width }: { item: LibraryItemDto; onDelete: () => void; onOpen: () => void; width?: number }) {
  const { t } = useTranslation();
  const b = item.book!;
  const rank = isRankCode(item.rankCode) ? item.rankCode : null;
  const tag = b.volumeNumber
    ? t('collection.volumeShort', { number: b.volumeNumber })
    : t(b.kind === 'MANGA' ? 'collection.tag.manga' : 'collection.tag.book');
  return (
    <Cover
      variant="tile"
      width={width}
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
  const [rankFilter, setRankFilter] = useState<RankFilter>('all');
  const [sortBy, setSortBy] = useState<SortBy>('added');
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [grouped, setGrouped] = useState(false);

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
    rank: rankFilter === 'all' ? undefined : rankFilter,
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

  // Grouping applies to what has been loaded so far; the button below extends it.
  const groups = useMemo(() => {
    const map = new Map<string, LibraryItemDto[]>();
    items.forEach((it) => {
      const s = it.book?.seriesTitle || it.book?.title || '—';
      if (!map.has(s)) map.set(s, []);
      map.get(s)!.push(it);
    });
    return [...map.entries()];
  }, [items]);

  const cats: { id: RankFilter; name: string; dot?: string }[] = [
    { id: 'all', name: t('collection.ranks.all') },
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

      <div className={`scroll-x ${styles.chipRow}`}>
        {cats.map((c) => (
          <Chip key={c.id} selected={rankFilter === c.id} dotColor={c.dot} onClick={() => setRankFilter(c.id)}>
            {c.name}
          </Chip>
        ))}
      </div>

      <div className={styles.countRow}>
        <span className={styles.count}>{t('collection.total', { total })}</span>
        <div className={styles.viewSwitch}>
          <Segmented<'flat' | 'grouped'>
            value={grouped ? 'grouped' : 'flat'}
            onChange={(v) => setGrouped(v === 'grouped')}
            options={[
              { id: 'flat', label: t('collection.list') },
              { id: 'grouped', label: t('collection.series') },
            ]}
          />
        </div>
      </div>

      <div className={`scroll-x ${styles.sortRow}`}>
        <span className={styles.sortLabel}>{t('collection.sortBy')}</span>
        {SORTS.map((s) => (
          <Chip key={s.id} selected={sortBy === s.id} onClick={() => setSortBy(s.id)}>{t(s.labelKey)}</Chip>
        ))}
      </div>

      {loading && <Loading />}

      {isError && <ErrorState message={t('collection.error')} onRetry={() => void refetch()} />}

      {!loading && !isError && items.length === 0 && (
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

      {!grouped && items.length > 0 && (
        <div className={styles.grid}>
          {items.map((it) => (
            <CoverTile key={it.id} item={it} onOpen={() => open(it)} onDelete={() => void remove(it.id!)} />
          ))}
        </div>
      )}

      {grouped && items.length > 0 && (
        <div className={styles.groupList}>
          {groups.map(([series, list]) => (
            <div key={series} className={styles.group}>
              <div className={styles.groupHeader}>
                <div className={styles.groupHeading}>
                  <div className={styles.groupTitle}>{series}</div>
                  <div className={styles.groupAuthors}>{list[0]?.book?.authors}</div>
                </div>
                <span className={styles.groupBadge}>
                  {list.length > 1 ? t('collection.volumes', { volumes: list.length }) : (list[0]?.book?.genres ?? '')}
                </span>
              </div>
              <div className={`scroll-x ${styles.groupShelf}`}>
                {list.map((it) => (
                  <CoverTile
                    key={it.id}
                    item={it}
                    onOpen={() => open(it)}
                    onDelete={() => void remove(it.id!)}
                    width={SERIES_COVER_WIDTH}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {hasNextPage && (
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

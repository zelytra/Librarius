import { InfiniteMore } from '../../shared/ui/InfiniteMore';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query';
import {
  getApiLibrary,
  getGetApiLibraryQueryKey,
  type GetApiLibraryParams,
  type LibraryItemDto,
} from '../../api/generated/librarius';
import { LoginGate } from '../../shared/LoginGate';
import { Cover } from '../../shared/ui/Cover';
import { Grid, Screen, ScreenTitle } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { AuthorNames } from '../author/AuthorNames';
import { SearchField } from '../../shared/ui/SearchField';
import { useUrlSearch } from '../../shared/useUrlSearch';
import { KindFilter } from '../../shared/ui/KindFilter';
import { useSelection } from '../../shared/selection/useSelection';
import { Selectable } from '../../shared/selection/Selectable';
import { SelectButton, SelectionBar } from '../../shared/selection/SelectionBar';
import { useKindFilter } from '../../shared/useKindFilter';
import styles from './ToReadPage.module.css';

const PAGE_SIZE = 60;

/** Owned, not started, not read: the pile of books waiting on the shelf. */
const CRITERIA: GetApiLibraryParams = { status: 'TO_READ', sort: 'recent', size: PAGE_SIZE };

function ToReadContent() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  // Searching the pile (#430), on the server like Collection's, kept in the URL.
  const { input, setInput, search } = useUrlSearch();
  const [kind, setKind] = useKindFilter();
  const selection = useSelection();
  const criteria: GetApiLibraryParams = { ...CRITERIA, q: search || undefined, kind };
  const { data, isPending, isError, refetch, hasNextPage, isFetchingNextPage, fetchNextPage } =
    useInfiniteQuery({
      queryKey: [...getGetApiLibraryQueryKey(criteria), 'infinite'],
      queryFn: ({ pageParam }) => getApiLibrary({ ...criteria, page: pageParam }),
      // The previous results stay while a new search is answered: no flash of a spinner
      // on every keystroke.
      placeholderData: keepPreviousData,
      initialPageParam: 0,
      getNextPageParam: (last) => {
        const loaded = ((last.page ?? 0) + 1) * (last.size ?? PAGE_SIZE);
        return loaded < (last.total ?? 0) ? (last.page ?? 0) + 1 : undefined;
      },
    });
  const items = useMemo(() => data?.pages.flatMap((p) => p.items ?? []) ?? [], [data]);
  // The whole pile, as the API counts it — not the page loaded so far.
  const total = data?.pages[0]?.total ?? 0;
  const open = (it: LibraryItemDto) => navigate(`/detail/${it.id}`, { state: { item: it } });

  const field = (
    <>
      <SearchField value={input} onChange={setInput} label={t('toRead.searchPlaceholder')} />
      <KindFilter value={kind} onChange={setKind} />
    </>
  );

  if (isPending) return <>{field}<Loading /></>;
  if (isError) return <ErrorState onRetry={() => void refetch()} />;
  if (items.length === 0 && (search || kind)) {
    return (
      <>
        {field}
        <p className={styles.count}>{search ? t('toRead.noMatch', { q: search }) : t('toRead.noKind')}</p>
      </>
    );
  }
  if (items.length === 0) {
    return (
      <EmptyState
        icon="library_books"
        title={t('toRead.empty.title')}
        description={t('toRead.empty.description')}
      />
    );
  }

  return (
    <>
      {field}
      <div className={styles.countRow}>
        <p className={styles.count} aria-live="polite">
          {t('toRead.count', { count: total })}
        </p>
        <SelectButton selection={selection} />
      </div>
      <Grid>
        {items.map((it) => (
          <Selectable key={it.id} id={it.id} label={t('selection.pick', { title: it.book?.title ?? '' })} selection={selection}>
          <div className={styles.cell}>
            <Cover
              variant="tile"
              title={it.book?.title ?? '—'}
              imageUrl={it.book?.coverUrl}
              tag={it.book?.volumeNumber ? t('collection.volumeShort', { number: it.book.volumeNumber }) : undefined}
              caption={<AuthorNames text={it.book?.authors} />}
              onClick={() => open(it)}
            />
          </div>
          </Selectable>
        ))}
      </Grid>
      <SelectionBar selection={selection} />
      <InfiniteMore
        className={styles.more}
        hasMore={!!hasNextPage}
        loading={isFetchingNextPage}
        onMore={() => void fetchNextPage()}
        label={t('toRead.more')}
      />
    </>
  );
}

/**
 * "Pile à lire" (#391): every title owned and not opened yet — neither in progress nor read —
 * with the size of the pile. Its own destination in the navigation, not only a chip of the
 * library, because "what do I read next" is a question of its own.
 */
export function ToReadPage() {
  const { t } = useTranslation();
  return (
    <Screen>
      <ScreenTitle className={styles.title}>{t('toRead.title')}</ScreenTitle>
      <LoginGate prompt={t('toRead.prompt')}>
        <ToReadContent />
      </LoginGate>
    </Screen>
  );
}

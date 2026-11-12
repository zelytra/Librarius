import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { useInfiniteQuery } from '@tanstack/react-query';
import {
  getApiLibrary,
  getGetApiLibraryQueryKey,
  type GetApiLibraryParams,
  type LibraryItemDto,
} from '../../api/generated/librarius';
import { LoginGate } from '../../shared/LoginGate';
import { Cover } from '../../shared/ui/Cover';
import { Button, Grid, Screen, ScreenTitle } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { AuthorNames } from '../author/AuthorNames';
import styles from './ToReadPage.module.css';

const PAGE_SIZE = 60;

/** Owned, not started, not read: the pile of books waiting on the shelf. */
const CRITERIA: GetApiLibraryParams = { status: 'OWNED', sort: 'recent', size: PAGE_SIZE };

function ToReadContent() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data, isPending, isError, refetch, hasNextPage, isFetchingNextPage, fetchNextPage } =
    useInfiniteQuery({
      queryKey: [...getGetApiLibraryQueryKey(CRITERIA), 'infinite'],
      queryFn: ({ pageParam }) => getApiLibrary({ ...CRITERIA, page: pageParam }),
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

  if (isPending) return <Loading />;
  if (isError) return <ErrorState onRetry={() => void refetch()} />;
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
      <p className={styles.count} aria-live="polite">
        {t('toRead.count', { count: total })}
      </p>
      <Grid>
        {items.map((it) => (
          <div key={it.id} className={styles.cell}>
            <Cover
              variant="tile"
              title={it.book?.title ?? '—'}
              imageUrl={it.book?.coverUrl}
              tag={it.book?.volumeNumber ? t('collection.volumeShort', { number: it.book.volumeNumber }) : undefined}
              caption={<AuthorNames text={it.book?.authors} />}
              onClick={() => open(it)}
            />
          </div>
        ))}
      </Grid>
      {hasNextPage && (
        <div className={styles.more}>
          <Button variant="secondary" disabled={isFetchingNextPage} onClick={() => void fetchNextPage()}>
            {t(isFetchingNextPage ? 'common.loading' : 'toRead.more')}
          </Button>
        </div>
      )}
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

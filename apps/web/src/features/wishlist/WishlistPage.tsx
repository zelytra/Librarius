import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { Icon } from '../../shared/ui/Icon';
import { colorFor } from '../../shared/ui/coverPalette';
import { Button, Screen, ScreenTitle } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { LoginGate } from '../../shared/LoginGate';
import {
  getApiWishlist,
  getGetApiWishlistQueryKey,
  useDeleteApiWishlistId,
} from '../../api/generated/librarius';
import styles from './WishlistPage.module.css';

/** Number of wishes fetched per request. */
const PAGE_SIZE = 50;

/** Badge colours; the label itself is keyed on the same priority. */
const PRIO: Record<string, string> = {
  PRIORITY: styles.priorityUrgent,
  SOON: styles.prioritySoon,
  SOMEDAY: styles.prioritySomeday,
};

function WishlistContent() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // The server pages the wishlist; the screen asks for the next page on demand.
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
    // while staying under the `/api/wishlist` prefix the mutations invalidate.
    queryKey: [...getGetApiWishlistQueryKey({ size: PAGE_SIZE }), 'infinite'],
    queryFn: ({ pageParam }) => getApiWishlist({ size: PAGE_SIZE, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (last) => {
      const loaded = ((last.page ?? 0) + 1) * (last.size ?? PAGE_SIZE);
      return loaded < (last.total ?? 0) ? (last.page ?? 0) + 1 : undefined;
    },
  });

  const items = useMemo(() => data?.pages.flatMap((p) => p.items ?? []) ?? [], [data]);
  const count = data?.pages[0]?.total ?? 0;

  const { mutate: removeItem } = useDeleteApiWishlistId({
    mutation: {
      onSuccess: () =>
        void queryClient.invalidateQueries({ queryKey: getGetApiWishlistQueryKey() }),
    },
  });

  const remove = (id: string) => removeItem({ id });

  // Budget of what has been loaded. A total over the whole wishlist belongs to
  // /api/stats, which aggregates in SQL — see issue #38.
  const budget = items.reduce((s, w) => s + (w.estimatedPrice ?? 0), 0);

  if (loading) return <Loading />;

  if (isError) {
    return <ErrorState message={t('wishlist.error')} onRetry={() => void refetch()} />;
  }

  if (items.length === 0) {
    return (
      <EmptyState
        icon="favorite"
        iconSize={40}
        className={styles.empty}
        title={t('wishlist.empty.title')}
        description={t('wishlist.empty.description')}
        action={
          <Button variant="secondary" onClick={() => navigate('/discover')}>
            {t('wishlist.empty.action')}
          </Button>
        }
      />
    );
  }

  return (
    <>
      <p className={styles.summary}>
        {t('wishlist.summary', { total: count, budget: budget.toFixed(2).replace('.', ',') })}
      </p>
      <div className={styles.list}>
        {items.map((w) => {
          const priority = w.priority && PRIO[w.priority] ? w.priority : 'SOON';
          const title = w.book?.title ?? '—';
          return (
            <div key={w.id} className={styles.row}>
              <div
                className={styles.thumb}
                // Either the real cover, or a colour derived from the title.
                style={{
                  background: w.book?.coverUrl
                    ? `center/cover no-repeat url(${w.book.coverUrl})`
                    : colorFor(title),
                }}
              />
              <div className={styles.body}>
                <div className={styles.bookTitle}>{title}</div>
                <div className={styles.authors}>{w.book?.authors}</div>
                <span className={`${styles.priority} ${PRIO[priority]}`}>
                  {t(`wishlist.priority.${priority}`)}
                </span>
              </div>
              <div className={styles.aside}>
                {w.estimatedPrice != null && (
                  <div className={styles.price}>
                    {t('wishlist.price', { price: w.estimatedPrice.toFixed(2).replace('.', ',') })}
                  </div>
                )}
                <button onClick={() => void remove(w.id!)} aria-label={t('common.remove')} className={styles.removeButton}>
                  <Icon name="delete" size={22} color="var(--faint)" />
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {hasNextPage && (
        <div className={styles.loadMore}>
          <Button variant="secondary" disabled={isFetchingNextPage} onClick={() => void fetchNextPage()}>
            {isFetchingNextPage
              ? t('common.loading')
              : t('collection.loadMore', { loaded: items.length, total: count })}
          </Button>
        </div>
      )}
    </>
  );
}

export function WishlistPage() {
  const { t } = useTranslation();
  return (
    <Screen>
      <ScreenTitle className={styles.title}>{t('wishlist.title')}</ScreenTitle>
      <LoginGate prompt={t('auth.prompts.wishlist')}>
        <WishlistContent />
      </LoginGate>
    </Screen>
  );
}

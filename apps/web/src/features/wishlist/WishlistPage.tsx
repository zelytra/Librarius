import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { Icon } from '../../shared/ui/Icon';
import { colorFor } from '../../shared/ui/coverPalette';
import {
  Button,
  EmptyState,
  Screen,
  ScreenTitle,
  StatusText,
} from '../../shared/ui/primitives';
import { LoginGate } from '../../shared/LoginGate';
import {
  getApiWishlist,
  getGetApiWishlistQueryKey,
  useDeleteApiWishlistId,
} from '../../api/generated/librarius';
import styles from './WishlistPage.module.css';

/** Number of wishes fetched per request. */
const PAGE_SIZE = 50;

const PRIO: Record<string, { label: string; className: string }> = {
  PRIORITY: { label: 'Priorité', className: styles.priorityUrgent },
  SOON: { label: 'Bientôt', className: styles.prioritySoon },
  SOMEDAY: { label: 'Un jour', className: styles.prioritySomeday },
};

function WishlistContent() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  // The server pages the wishlist; the screen asks for the next page on demand.
  const {
    data,
    isPending: loading,
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

  if (loading) return <StatusText>{t('common.loading')}</StatusText>;

  if (items.length === 0) {
    return (
      <EmptyState icon="favorite" iconSize={40} className={styles.empty}>
        Ta liste de souhaits est vide. Ajoute des titres depuis <strong>Découvrir</strong>.
      </EmptyState>
    );
  }

  return (
    <>
      <p className={styles.summary}>
        {count} titres · estimé {budget.toFixed(2).replace('.', ',')} €
      </p>
      <div className={styles.list}>
        {items.map((w) => {
          const p = PRIO[w.priority ?? 'SOON'] ?? PRIO.SOON;
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
                <span className={`${styles.priority} ${p.className}`}>{p.label}</span>
              </div>
              <div className={styles.aside}>
                {w.estimatedPrice != null && (
                  <div className={styles.price}>{w.estimatedPrice.toFixed(2).replace('.', ',')} €</div>
                )}
                <button onClick={() => void remove(w.id!)} aria-label="Retirer" className={styles.removeButton}>
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
      <LoginGate prompt="Connecte-toi pour voir tes souhaits.">
        <WishlistContent />
      </LoginGate>
    </Screen>
  );
}

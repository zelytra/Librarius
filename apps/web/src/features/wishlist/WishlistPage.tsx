import { useMemo, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { Icon } from '../../shared/ui/Icon';
import { colorFor } from '../../shared/ui/coverPalette';
import { Button, Screen, ScreenTitle, Segmented } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { LoginGate } from '../../shared/LoginGate';
import {
  getApiWishlist,
  getGetApiLibraryQueryKey,
  getGetApiStatsQueryKey,
  getGetApiWishlistQueryKey,
  useDeleteApiWishlistId,
  usePostApiWishlistIdAcquire,
  usePutApiWishlistId,
  type WishlistItemDto,
  type WishlistUpdateDto,
} from '../../api/generated/librarius';
import styles from './WishlistPage.module.css';

/** Number of wishes fetched per request. */
const PAGE_SIZE = 50;

/** The buckets, in the order the API sorts them: most urgent first. */
const PRIORITIES = ['PRIORITY', 'SOON', 'SOMEDAY'] as const;

type Priority = (typeof PRIORITIES)[number];

/** Badge colours; the label itself is keyed on the same priority. */
const PRIO: Record<Priority, string> = {
  PRIORITY: styles.priorityUrgent,
  SOON: styles.prioritySoon,
  SOMEDAY: styles.prioritySomeday,
};

/** A priority the front end does not know about would leave the wish with no bucket. */
function priorityOf(wish: WishlistItemDto): Priority {
  return PRIORITIES.find((p) => p === wish.priority) ?? 'SOON';
}

/** French money: the API carries a decimal, the interface shows a comma. */
function money(amount: number): string {
  return amount.toFixed(2).replace('.', ',');
}

interface EditorProps {
  wish: WishlistItemDto;
  busy: boolean;
  onCancel: () => void;
  onSave: (data: WishlistUpdateDto) => void;
}

/**
 * Inline edit of a wish. Keeps its own draft, so cancelling costs nothing and typing a
 * price does not re-render the rest of the list.
 */
function WishEditor({ wish, busy, onCancel, onSave }: EditorProps) {
  const { t } = useTranslation();
  const [priority, setPriority] = useState<Priority>(priorityOf(wish));
  const [price, setPrice] = useState(wish.estimatedPrice != null ? money(wish.estimatedPrice) : '');
  const [note, setNote] = useState(wish.note ?? '');

  // An empty field clears the price — the endpoint is a PUT, it replaces the three
  // fields. Anything else that is not a positive number blocks the save rather than
  // reaching the API as a clear the user never asked for.
  const typed = price.trim().replace(',', '.');
  const parsed = typed === '' ? undefined : Number(typed);
  const priceValid = parsed === undefined || (Number.isFinite(parsed) && parsed >= 0);

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!priceValid) return;
    onSave({ priority, estimatedPrice: parsed, note: note.trim() || undefined });
  }

  return (
    <form className={styles.editor} onSubmit={submit}>
      <Segmented<Priority>
        value={priority}
        onChange={setPriority}
        options={PRIORITIES.map((p) => ({ id: p, label: t(`wishlist.priority.${p}`) }))}
      />
      <input
        value={price}
        inputMode="decimal"
        onChange={(e) => setPrice(e.target.value)}
        placeholder={t('wishlist.edit.pricePlaceholder')}
        aria-label={t('wishlist.edit.price')}
        aria-invalid={!priceValid}
        className={styles.editorInput}
      />
      <input
        value={note}
        maxLength={512}
        onChange={(e) => setNote(e.target.value)}
        placeholder={t('wishlist.edit.notePlaceholder')}
        aria-label={t('wishlist.edit.note')}
        className={styles.editorInput}
      />
      <div className={styles.editorActions}>
        <Button type="submit" size="compact" disabled={busy || !priceValid}>
          {t('wishlist.edit.save')}
          <Loading size="compact" pending={busy} />
        </Button>
        <Button type="button" variant="ghost" size="compact" onClick={onCancel}>
          {t('wishlist.edit.cancel')}
        </Button>
      </div>
    </form>
  );
}

function WishlistContent() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

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
  // The budget covers the whole wishlist, not the loaded pages: it rides on the envelope
  // exactly like `total`, and is therefore read from the first page like it.
  const budget = data?.pages[0]?.budget;

  /** The list itself, which every write below changes. */
  const refreshWishlist = () =>
    void queryClient.invalidateQueries({ queryKey: getGetApiWishlistQueryKey() });

  const { mutate: removeItem } = useDeleteApiWishlistId({
    mutation: {
      onSuccess: refreshWishlist,
      onError: () => setActionError(t('wishlist.errors.removeFailed')),
    },
  });

  const { mutate: updateItem, isPending: saving } = usePutApiWishlistId({
    mutation: {
      onSuccess: () => {
        setEditing(null);
        refreshWishlist();
      },
      onError: () => setActionError(t('wishlist.errors.editFailed')),
    },
  });

  const { mutate: acquireItem } = usePostApiWishlistIdAcquire({
    mutation: {
      onSuccess: () => {
        refreshWishlist();
        // The title left the wishlist for the collection: both of them moved, and so
        // did the counters the Home screen and the statistics read.
        void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
      },
      onError: () => setActionError(t('wishlist.errors.acquireFailed')),
    },
  });

  /** Bought today, and owned rather than read: the user said nothing more than that. */
  const acquire = (id: string) =>
    acquireItem({ id, data: { status: 'OWNED', acquiredAt: new Date().toISOString().slice(0, 10) } });

  const startEditing = (id: string) => {
    setActionError(null);
    setEditing(id);
  };

  /**
   * Loaded wishes, bucketed by priority. The subtotal attached to a bucket is the
   * server's, so it covers the whole bucket even when only its first page is loaded.
   */
  const groups = useMemo(
    () =>
      PRIORITIES.map((priority) => ({
        priority,
        wishes: items.filter((w) => priorityOf(w) === priority),
        line: budget?.byPriority?.find((b) => b.priority === priority),
      })).filter((group) => group.wishes.length > 0),
    [items, budget],
  );

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
        {t('wishlist.summary', { total: count, budget: money(budget?.total ?? 0) })}
      </p>

      {actionError && <ErrorState message={actionError} />}

      {/* Below --bp-tablet this is the single stacked column shipped today, one bucket
          under the next; past it the buckets that fit lay out side by side instead of
          each one running the whole width of a wide window — see `.groups` in the
          module CSS for how the column count follows the grid tokens without a media
          query of its own. */}
      <div className={styles.groups}>
        {groups.map((group) => (
          <section key={group.priority} className={styles.group}>
            <div className={styles.groupHeader}>
              <span className={`${styles.priority} ${PRIO[group.priority]}`}>
                {t(`wishlist.priority.${group.priority}`)}
              </span>
              <span className={styles.groupTotal}>
                {t('wishlist.groupTotal', {
                  titles: group.line?.count ?? group.wishes.length,
                  budget: money(group.line?.total ?? 0),
                })}
              </span>
            </div>

            <div className={styles.list}>
              {group.wishes.map((w) => {
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
                  {editing === w.id ? (
                    <WishEditor
                      wish={w}
                      busy={saving}
                      onCancel={() => setEditing(null)}
                      onSave={(update) => updateItem({ id: w.id!, data: update })}
                    />
                  ) : (
                    <div className={styles.body}>
                      <div className={styles.headline}>
                        <span className={styles.bookTitle}>{title}</span>
                        {w.estimatedPrice != null && (
                          <span className={styles.price}>
                            {t('wishlist.price', { price: money(w.estimatedPrice) })}
                          </span>
                        )}
                      </div>
                      <div className={styles.authors}>{w.book?.authors}</div>
                      {w.note && <p className={styles.note}>{w.note}</p>}

                      <div className={styles.rowActions}>
                        <button onClick={() => acquire(w.id!)} className={styles.acquire}>
                          <Icon name="shopping_bag" size={16} color="var(--accent-deep)" />
                          {t('wishlist.acquire')}
                        </button>
                        <span className={styles.iconButtons}>
                          <button
                            onClick={() => startEditing(w.id!)}
                            aria-label={t('wishlist.edit.action')}
                            className={styles.iconButton}
                          >
                            <Icon name="edit" size={20} color="var(--faint)" />
                          </button>
                          <button
                            onClick={() => removeItem({ id: w.id! })}
                            aria-label={t('common.remove')}
                            className={styles.iconButton}
                          >
                            <Icon name="delete" size={20} color="var(--faint)" />
                          </button>
                        </span>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
            </div>
          </section>
        ))}
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

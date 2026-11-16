import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery } from '@tanstack/react-query';
import { getApiUsersIdShelf } from '../../api/generated/librarius';
import { MemberBook } from '../../shared/ui/MemberBook';
import { Button } from '../../shared/ui/primitives';
import { ErrorState, Loading } from '../../shared/ui/states';
import { statusLabelKey } from '../../shared/statuses';
import styles from './ProfilePage.module.css';

export type ShelfName = 'library' | 'toRead' | 'wishlist';

const PAGE_SIZE = 30;
const DEBOUNCE_MS = 300;

/**
 * One shelf of a member (#514) — their whole library, their to-read pile or their wishlist —
 * a page at a time, by title, with a search. Every book opens its screen (#513).
 */
export function Shelf({ memberId, shelf }: { memberId: string; shelf: ShelfName }) {
  const { t } = useTranslation();
  const [input, setInput] = useState('');
  const [query, setQuery] = useState('');
  useEffect(() => {
    const handle = setTimeout(() => setQuery(input.trim()), DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [input]);

  const pages = useInfiniteQuery({
    queryKey: ['member-shelf', memberId, shelf, query],
    queryFn: ({ pageParam }) =>
      getApiUsersIdShelf(memberId, { shelf, page: pageParam, size: PAGE_SIZE, ...(query ? { q: query } : {}) }),
    initialPageParam: 0,
    getNextPageParam: (last, all) =>
      all.reduce((sum, page) => sum + (page.items?.length ?? 0), 0) < (last.total ?? 0) ? all.length : undefined,
  });
  const titles = pages.data?.pages.flatMap((page) => page.items ?? []) ?? [];
  const total = pages.data?.pages[0]?.total ?? 0;

  return (
    <section className={styles.section}>
      <input
        value={input}
        onChange={(event) => setInput(event.target.value)}
        placeholder={t('profile.shelf.search')}
        aria-label={t('profile.shelf.search')}
        className={styles.search}
      />
      {pages.isPending ? (
        <Loading />
      ) : pages.isError ? (
        <ErrorState message={t('profile.shelf.error')} onRetry={() => void pages.refetch()} />
      ) : titles.length === 0 ? (
        <p className={styles.empty}>{t('profile.shelf.empty')}</p>
      ) : (
        <>
          <p className={styles.count}>{t('profile.shelf.total', { count: total })}</p>
          <ul className={styles.list}>
            {titles.map((item, index) => (
              <li key={`${item.book?.editionId}-${index}`} className={styles.row}>
                <MemberBook book={item.book ?? {}} detail={item.status ? t(statusLabelKey(item.status)) : null} />
              </li>
            ))}
          </ul>
          {pages.hasNextPage && (
            <Button variant="secondary" disabled={pages.isFetchingNextPage} onClick={() => void pages.fetchNextPage()}>
              {pages.isFetchingNextPage ? t('common.loading') : t('feed.more')}
            </Button>
          )}
        </>
      )}
    </section>
  );
}

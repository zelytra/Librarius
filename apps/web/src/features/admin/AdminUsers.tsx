import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  deleteApiAdminUsersId,
  getGetApiAdminUsersQueryKey,
  useGetApiAdminUsers,
  type AdminUserDto,
} from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';
import { Avatar } from '../../shared/ui/Avatar';
import { Modal } from '../../shared/ui/Modal';
import { Button } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { toast } from '../../shared/ui/toast';
import styles from './AdminPage.module.css';

const PAGE_SIZE = 50;
const DEBOUNCE_MS = 300;

const day = (value?: string) =>
  value ? new Date(value).toLocaleDateString(activeLanguage(), { day: 'numeric', month: 'long', year: 'numeric' }) : '';

/**
 * The members (#499): picture, name, username, when they joined and how much they keep here,
 * and deleting one — their Keycloak account first, then everything they own — behind a
 * confirmation that asks for their username.
 */
export function AdminUsers() {
  const { t } = useTranslation();
  const [input, setInput] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [doomed, setDoomed] = useState<AdminUserDto | null>(null);

  useEffect(() => {
    const handle = setTimeout(() => {
      setQuery(input.trim());
      setPage(0);
    }, DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [input]);

  const { data, isPending, isError, refetch } = useGetApiAdminUsers({
    page,
    size: PAGE_SIZE,
    ...(query ? { q: query } : {}),
  });
  const users = data?.items ?? [];
  const pages = Math.ceil((data?.total ?? 0) / PAGE_SIZE);

  return (
    <>
      <input
        value={input}
        onChange={(event) => setInput(event.target.value)}
        placeholder={t('admin.users.search')}
        aria-label={t('admin.users.search')}
        className={styles.search}
      />
      {isPending ? (
        <Loading />
      ) : isError ? (
        <ErrorState message={t('admin.users.error')} onRetry={() => void refetch()} />
      ) : users.length === 0 ? (
        <EmptyState icon="search_off" title={t('admin.users.empty')} />
      ) : (
        <>
          <p className={styles.count}>{t('admin.users.total', { count: data?.total ?? 0 })}</p>
          <ul className={styles.reports}>
            {users.map((user) => {
              const name = user.displayName ?? user.username ?? '—';
              return (
                <li key={user.id} className={styles.user}>
                  {/* The member's profile, one tap away (#562). */}
                  <Link to={`/people/${user.id}`} className={styles.userLink}>
                    <Avatar id={user.id} name={name} version={user.avatarVersion} size={44} decorative />
                    <span className={styles.userText}>
                      <span className={styles.userName}>{name}</span>
                      {user.username && <span className={styles.meta}>{t('admin.users.handle', { username: user.username })}</span>}
                      <span className={styles.meta}>
                        {[t('admin.users.since', { date: day(user.createdAt) }), t('admin.users.titles', { count: user.libraryItems ?? 0 })]
                          .join(' · ')}
                      </span>
                    </span>
                  </Link>
                  <Button
                    variant="secondary"
                    size="compact"
                    aria-label={t('admin.users.deleteAria', { name })}
                    onClick={() => setDoomed(user)}
                  >
                    {t('admin.users.delete')}
                  </Button>
                </li>
              );
            })}
          </ul>
          {pages > 1 && (
            <div className={styles.pager}>
              <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
                {t('admin.reports.previous')}
              </Button>
              <span className={styles.count}>{t('admin.reports.page', { page: page + 1, pages })}</span>
              <Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>
                {t('admin.reports.next')}
              </Button>
            </div>
          )}
        </>
      )}
      {doomed && <DeleteUser user={doomed} onClose={() => setDoomed(null)} />}
    </>
  );
}

function DeleteUser({ user, onClose }: { user: AdminUserDto; onClose: () => void }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [typed, setTyped] = useState('');
  const [busy, setBusy] = useState(false);
  const name = user.displayName ?? user.username ?? '—';
  const expected = user.username ?? user.displayName ?? '';

  async function confirm() {
    setBusy(true);
    try {
      await deleteApiAdminUsersId(user.id!);
      toast.success(t('admin.users.deleted', { name }));
      void queryClient.invalidateQueries({ queryKey: getGetApiAdminUsersQueryKey().slice(0, 1) });
      onClose();
    } catch {
      toast.error(t('admin.users.deleteFailed'));
      setBusy(false);
    }
  }

  return (
    <Modal title={t('admin.users.deleteTitle', { name })} onClose={onClose}>
      <p className={styles.detailMessage}>{t('admin.users.deleteText')}</p>
      <label className={styles.confirmField}>
        <span>{t('admin.users.confirmLabel', { username: expected })}</span>
        <input value={typed} onChange={(event) => setTyped(event.target.value)} autoComplete="off" className={styles.search} />
      </label>
      <div className={styles.triageActions}>
        <Button variant="secondary" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button variant="primary" disabled={busy || typed.trim() !== expected} onClick={() => void confirm()}>
          {t('admin.users.confirm')}
        </Button>
      </div>
    </Modal>
  );
}

import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import {
  deleteApiNotifications,
  deleteApiNotificationsId,
  getApiNotifications,
  getGetApiNotificationsUnreadQueryKey,
  postApiNotificationsRead,
  usePutApiUsersIdFollow,
  type NotificationDto,
  type UpcomingReleaseDto,
} from '../../api/generated/librarius';
import { toast } from '../../shared/ui/toast';
import { activeLanguage } from '../../i18n/languages';
import { LoginGate } from '../../shared/LoginGate';
import { Avatar } from '../../shared/ui/Avatar';
import { Icon } from '../../shared/ui/Icon';
import { Button, Screen } from '../../shared/ui/primitives';
import { Modal } from '../../shared/ui/Modal';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { formatReleaseDate } from '../home/releases';
import styles from './Notifications.module.css';

const PAGE_SIZE = 30;
const EMOJI: Record<string, string> = { LOVE: '❤️', FUNNY: '😂', WOW: '😮', CLAP: '👏', SAD: '😢' };

/**
 * The notifications (#516, #211), newest first: a followed reader's activity, a reaction or a
 * comment on mine. Each opens the event it is about; opening the list reads them all.
 */
export function NotificationsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <Screen measure="reading">
      <div className={`sticky-header ${styles.header}`}>
        <button onClick={() => navigate(-1)} aria-label={t('common.back')} className={styles.backButton}>
          <Icon name="arrow_back" size={24} color="var(--ink)" />
        </button>
        <h2 className={styles.title}>{t('notifications.title')}</h2>
      </div>
      <LoginGate prompt={t('notifications.prompt')}>
        <Notifications />
      </LoginGate>
    </Screen>
  );
}

function Notifications() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const list = useInfiniteQuery({
    queryKey: ['notifications'],
    queryFn: ({ pageParam }) => getApiNotifications({ page: pageParam, size: PAGE_SIZE }),
    initialPageParam: 0,
    getNextPageParam: (last, _all, lastParam) => (last.hasMore ? lastParam + 1 : undefined),
  });
  const loaded = list.isSuccess;

  // Seen once the list is on screen: the badge empties, the entries keep their unread mark
  // until the next visit.
  useEffect(() => {
    if (!loaded) return;
    void postApiNotificationsRead().then(() =>
      queryClient.invalidateQueries({ queryKey: getGetApiNotificationsUnreadQueryKey() }),
    );
  }, [loaded, queryClient]);

  if (list.isPending) return <Loading />;
  if (list.isError) return <ErrorState message={t('notifications.error')} onRetry={() => void list.refetch()} />;
  const items = list.data.pages.flatMap((page) => page.items ?? []);
  if (items.length === 0) {
    return <EmptyState icon="notifications" title={t('notifications.empty')} description={t('notifications.emptyHint')} />;
  }
  return (
    <>
      <div className={styles.toolbar}>
        <ClearAll />
      </div>
      <ul className={styles.list}>
        {items.map((item) => (
          <li key={item.id} className={styles.row}>
            <Entry item={item} />
            <DeleteOne id={item.id!} />
          </li>
        ))}
      </ul>
      {list.hasNextPage && (
        <Button variant="secondary" disabled={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>
          {list.isFetchingNextPage ? t('common.loading') : t('feed.more')}
        </Button>
      )}
    </>
  );
}

/** Reads the list and the bell's count again after a deletion (#629). */
function useRefreshNotifications() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    void queryClient.invalidateQueries({ queryKey: getGetApiNotificationsUnreadQueryKey() });
  };
}

/** Deletes one notification (#629): a quiet button beside it, no confirmation for a single line. */
function DeleteOne({ id }: { id: string }) {
  const { t } = useTranslation();
  const refresh = useRefreshNotifications();
  const [busy, setBusy] = useState(false);
  async function remove() {
    setBusy(true);
    try {
      await deleteApiNotificationsId(id);
      toast.success(t('notifications.deleted'), 'delete');
      refresh();
    } catch {
      toast.error(t('notifications.deleteFailed'));
      setBusy(false);
    }
  }
  return (
    <button
      type="button"
      className={styles.delete}
      disabled={busy}
      aria-label={t('notifications.deleteOne')}
      onClick={() => void remove()}
    >
      <Icon name="delete" size={20} color="var(--ink-soft)" />
    </button>
  );
}

/** Deletes every notification (#629), behind a confirmation: it cannot be undone. */
function ClearAll() {
  const { t } = useTranslation();
  const refresh = useRefreshNotifications();
  const [asking, setAsking] = useState(false);
  const [busy, setBusy] = useState(false);
  async function clear() {
    setBusy(true);
    try {
      await deleteApiNotifications();
      toast.success(t('notifications.clearedAll'), 'delete');
      setAsking(false);
      refresh();
    } catch {
      toast.error(t('notifications.deleteFailed'));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Button variant="secondary" size="compact" onClick={() => setAsking(true)}>
        <Icon name="delete_forever" size={16} color="currentColor" />
        {t('notifications.clearAll')}
      </Button>
      {asking && (
        <Modal title={t('notifications.clearAllTitle')} description={t('notifications.clearAllText')} onClose={() => setAsking(false)}>
          <div className={styles.confirm}>
            <Button variant="primary" size="block" disabled={busy} onClick={() => void clear()}>
              {t('notifications.clearAll')}
            </Button>
            <Button variant="ghost" size="block" onClick={() => setAsking(false)}>
              {t('common.cancel')}
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}

const when = (value?: string) =>
  value ? new Date(value).toLocaleString(activeLanguage(), { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '';

/**
 * A new follower (#544): their profile one tap away, and following them back without leaving
 * the list. Not one link like the others — a button cannot sit inside a link.
 */
function FollowEntry({ item }: { item: NotificationDto }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const name = item.actor?.displayName ?? '—';
  const unread = !item.readAt;
  const { mutate: followBack, isPending } = usePutApiUsersIdFollow({
    mutation: {
      onSuccess: () => {
        toast.success(t('notifications.followedBack', { name }), 'check_circle');
        void queryClient.invalidateQueries({ queryKey: ['notifications'] });
      },
      onError: () => toast.error(t('toasts.failed')),
    },
  });
  return (
    <div className={`${styles.entry} ${unread ? styles.unread : ''}`}>
      <Link to={`/people/${item.actor?.id}`} className={styles.entryLink}>
        <Avatar id={item.actor?.id} name={name} version={item.actor?.avatarVersion} size={40} decorative />
        <span className={styles.body}>
          <span className={styles.text}>{t('notifications.follow', { name })}</span>
          <span className={styles.meta}>
            <time dateTime={item.createdAt}>{when(item.createdAt)}</time>
            {unread && <span className={styles.unreadMark}>{t('notifications.unread')}</span>}
            {item.followingActor && <span>{t('notifications.followingToo')}</span>}
          </span>
        </span>
      </Link>
      {!item.followingActor && item.actor?.id && (
        <Button
          variant="primary"
          size="compact"
          className={styles.followBack}
          disabled={isPending}
          aria-label={t('notifications.followBackLabel', { name })}
          onClick={() => followBack({ id: item.actor!.id! })}
        >
          {t('notifications.followBack')}
        </Button>
      )}
    </div>
  );
}

/**
 * A volume announced for a followed series or author (#615): nobody's doing, so an icon stands
 * where the avatar would, and the entry opens the series.
 */
function ReleaseEntry({ item, release }: { item: NotificationDto; release: UpcomingReleaseDto }) {
  const { t } = useTranslation();
  const series = release.seriesTitle ?? '—';
  const what =
    release.volumeNumber != null
      ? t('notifications.release.volume', { number: release.volumeNumber, series })
      : t('notifications.release.series', { series });
  const date = formatReleaseDate(release, t);
  const text = t(release.datePrecision === 'DAY' ? 'notifications.release.day' : 'notifications.release.other', {
    what,
    date,
  });
  const unread = !item.readAt;
  return (
    <Link to={`/series/${release.seriesId}`} className={`${styles.entry} ${unread ? styles.unread : ''}`}>
      <span className={styles.releaseIcon} aria-hidden="true">
        <Icon name="calendar_month" size={22} color="var(--accent-deep)" />
      </span>
      <span className={styles.body}>
        <span className={styles.text}>{text}</span>
        <span className={styles.meta}>
          <time dateTime={item.createdAt}>{when(item.createdAt)}</time>
          {unread && <span className={styles.unreadMark}>{t('notifications.unread')}</span>}
        </span>
      </span>
    </Link>
  );
}

/** What an admin decided on the recipient's catalog edit proposal (#618). */
function EditEntry({ item }: { item: NotificationDto }) {
  const { t } = useTranslation();
  const edit = item.edit!;
  const unread = !item.readAt;
  const to =
    edit.targetType === 'SERIES' ? `/series/${edit.targetId}` : edit.libraryItemId ? `/detail/${edit.libraryItemId}` : null;
  const content = (
    <>
      <span className={styles.editIcon} aria-hidden="true">
        <Icon name="edit" size={20} color="var(--accent-deep)" />
      </span>
      <span className={styles.body}>
        <span className={styles.text}>
          {t(`notifications.edit.${edit.status}`, { title: edit.targetTitle ?? '' })}
        </span>
        {edit.note && <span className={styles.quote}>{t('notifications.quote', { comment: edit.note })}</span>}
        <span className={styles.meta}>
          <time dateTime={item.createdAt}>{when(item.createdAt)}</time>
          {unread && <span className={styles.unreadMark}>{t('notifications.unread')}</span>}
        </span>
      </span>
    </>
  );
  const className = `${styles.entry} ${unread ? styles.unread : ''}`;
  return to ? (
    <Link to={to} className={className}>
      {content}
    </Link>
  ) : (
    <div className={className}>{content}</div>
  );
}

function Entry({ item }: { item: NotificationDto }) {
  const { t } = useTranslation();
  if (item.type === 'FOLLOW') return <FollowEntry item={item} />;
  if (item.type === 'RELEASE') return item.release ? <ReleaseEntry item={item} release={item.release} /> : null;
  if (item.type === 'EDIT' && item.edit) return <EditEntry item={item} />;
  const name = item.actor?.displayName ?? '—';
  const title = item.book?.title ?? '';
  const text =
    item.type === 'REACTION'
      ? t('notifications.reaction', { name, emoji: EMOJI[item.emote ?? ''] ?? '', title })
      : item.type === 'COMMENT'
        ? t('notifications.comment', { name, title })
        : t(`notifications.activity.${item.eventType}`, { name, title });
  const unread = !item.readAt;
  return (
    <Link to={`/feed/${item.eventId}`} className={`${styles.entry} ${unread ? styles.unread : ''}`}>
      <Avatar id={item.actor?.id} name={name} version={item.actor?.avatarVersion} size={40} decorative />
      <span className={styles.body}>
        <span className={styles.text}>{text}</span>
        {item.comment && <span className={styles.quote}>{t('notifications.quote', { comment: item.comment })}</span>}
        <span className={styles.meta}>
          <time dateTime={item.createdAt}>{when(item.createdAt)}</time>
          {unread && <span className={styles.unreadMark}>{t('notifications.unread')}</span>}
        </span>
      </span>
    </Link>
  );
}

import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import {
  deleteApiFeedEventIdCommentsCommentId,
  deleteApiFeedEventIdReaction,
  getApiFeed,
  getGetApiFeedQueryKey,
  postApiFeedEventIdComments,
  putApiFeedEventIdReaction,
  useGetApiReleasesUpcoming,
  type FeedItemDto,
} from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';
import { MemberBook } from '../../shared/ui/MemberBook';
import { Avatar } from '../../shared/ui/Avatar';
import { Icon } from '../../shared/ui/Icon';
import { Button } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { toast } from '../../shared/ui/toast';
import { Stars } from '../detail/StarRating';
import { formatReleaseDate, releaseVolumeLabel } from '../home/releases';
import styles from './Feed.module.css';

const PAGE_SIZE = 20;

/** The fixed set of reactions (#210), the emoji only illustrating the word each one is read as. */
const EMOTES = ['LOVE', 'FUNNY', 'WOW', 'CLAP', 'SAD'] as const;
type Emote = (typeof EMOTES)[number];
const EMOJI: Record<Emote, string> = { LOVE: '❤️', FUNNY: '😂', WOW: '😮', CLAP: '👏', SAD: '😢' };

/** The feed's queries, whatever page: what a write refreshes. */
const FEED_KEY = getGetApiFeedQueryKey().slice(0, 1);

/**
 * The feed (#209, #210): what the readers you follow finished, wished or reviewed, newest
 * first, with reactions and a flat thread of comments on each — and, apart, the coming
 * releases of your series, which nobody "did" and nobody reacts to.
 */
export function Feed({ onFindPeople }: { onFindPeople: () => void }) {
  const { t } = useTranslation();
  const feed = useInfiniteQuery({
    queryKey: [...FEED_KEY, 'infinite'],
    queryFn: ({ pageParam }) => getApiFeed({ page: pageParam, size: PAGE_SIZE }),
    initialPageParam: 0,
    getNextPageParam: (last, _all, lastParam) => (last.hasMore ? lastParam + 1 : undefined),
  });

  const items = feed.data?.pages.flatMap((page) => page.items ?? []) ?? [];

  return (
    <div className={styles.feed}>
      <Releases />
      {feed.isPending ? (
        <Loading />
      ) : feed.isError ? (
        <ErrorState message={t('feed.error')} onRetry={() => void feed.refetch()} />
      ) : items.length === 0 ? (
        <EmptyState
          icon="group"
          title={t('feed.empty')}
          description={t('feed.emptyHint')}
          action={
            <Button variant="secondary" onClick={onFindPeople}>
              {t('people.open')}
            </Button>
          }
        />
      ) : (
        <>
          <ul className={styles.list}>
            {items.map((item) => (
              <li key={item.id}>
                <FeedEvent item={item} />
              </li>
            ))}
          </ul>
          {feed.hasNextPage && (
            <Button variant="secondary" disabled={feed.isFetchingNextPage} onClick={() => void feed.fetchNextPage()}>
              {feed.isFetchingNextPage ? t('common.loading') : t('feed.more')}
            </Button>
          )}
        </>
      )}
    </div>
  );
}

const when = (value?: string) =>
  value ? new Date(value).toLocaleDateString(activeLanguage(), { day: 'numeric', month: 'short' }) : '';

function FeedEvent({ item }: { item: FeedItemDto }) {
  const { t } = useTranslation();
  const actor = item.actor;
  const name = actor?.displayName ?? '—';
  const title = item.book?.title ?? '';

  return (
    <article className={styles.event}>
      <p className={styles.head}>
        <Avatar id={actor?.id} name={name} version={actor?.avatarVersion} size={28} decorative />
        <Link to={`/people/${actor?.id}`} className={styles.actor}>
          {name}
        </Link>{' '}
        <span>{t(`feed.verbs.${item.type}`)}</span>
        <time className={styles.date} dateTime={item.createdAt}>
          {when(item.createdAt)}
        </time>
      </p>
      <MemberBook book={item.book ?? { title }} coverWidth={48}
        detail={item.rating != null ? <Stars value={item.rating} /> : null} />
      {item.review && <p className={styles.review}>{item.review}</p>}
      <Reactions item={item} />
      <Comments item={item} />
    </article>
  );
}

function Reactions({ item }: { item: FeedItemDto }) {
  const { t } = useTranslation();
  // What the screen shows, ahead of the server; put back if it refuses.
  const [mine, setMine] = useState<string | null>(item.myReaction ?? null);
  const [counts, setCounts] = useState<Record<string, number>>(() => ({ ...(item.reactions ?? {}) }));
  const [busy, setBusy] = useState(false);

  async function choose(emote: Emote) {
    const before = { mine, counts };
    const next = mine === emote ? null : emote;
    const nextCounts = { ...counts };
    if (mine) nextCounts[mine] = Math.max(0, (nextCounts[mine] ?? 0) - 1);
    if (next) nextCounts[next] = (nextCounts[next] ?? 0) + 1;
    setMine(next);
    setCounts(nextCounts);
    setBusy(true);
    try {
      if (next) await putApiFeedEventIdReaction(item.id!, { emote: next });
      else await deleteApiFeedEventIdReaction(item.id!);
    } catch {
      setMine(before.mine);
      setCounts(before.counts);
      toast.error(t('profile.actionFailed'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.reactions} role="group" aria-label={t('feed.react')}>
      {EMOTES.map((emote) => {
        const count = counts[emote] ?? 0;
        return (
          <button
            key={emote}
            type="button"
            className={`${styles.reaction} ${mine === emote ? styles.reactionOn : ''}`}
            aria-pressed={mine === emote}
            aria-label={t('feed.reaction', { emote: t(`feed.emotes.${emote}`), count })}
            disabled={busy}
            onClick={() => void choose(emote)}
          >
            <span aria-hidden="true">{EMOJI[emote]}</span>
            {count > 0 && <span aria-hidden="true">{count}</span>}
          </button>
        );
      })}
    </div>
  );
}

function Comments({ item }: { item: FeedItemDto }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const comments = item.comments ?? [];
  const refresh = () => void queryClient.invalidateQueries({ queryKey: FEED_KEY });

  async function send(event: React.FormEvent) {
    event.preventDefault();
    const comment = draft.trim();
    if (!comment) return;
    setSending(true);
    try {
      await postApiFeedEventIdComments(item.id!, { comment });
      setDraft('');
      toast.success(t('feed.commented'));
      refresh();
    } catch {
      toast.error(t('profile.actionFailed'));
    } finally {
      setSending(false);
    }
  }

  async function remove(commentId: string) {
    try {
      await deleteApiFeedEventIdCommentsCommentId(item.id!, commentId);
      toast.success(t('feed.commentDeleted'));
      refresh();
    } catch {
      toast.error(t('profile.actionFailed'));
    }
  }

  return (
    <div className={styles.comments}>
      {comments.length > 0 && (
        <ul className={styles.commentList} aria-label={t('feed.comments')}>
          {comments.map((comment) => (
            <li key={comment.id} className={styles.comment}>
              <p className={styles.commentBody}>
                <Link to={`/people/${comment.author?.id}`} className={styles.actor}>
                  {comment.author?.displayName ?? '—'}
                </Link>{' '}
                {comment.comment}
              </p>
              {comment.mine && (
                <button
                  type="button"
                  className={styles.iconButton}
                  aria-label={t('feed.deleteComment')}
                  onClick={() => void remove(comment.id!)}
                >
                  <Icon name="delete" size={18} color="var(--muted)" />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      <form className={styles.commentForm} onSubmit={(e) => void send(e)}>
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          maxLength={2000}
          placeholder={t('feed.commentPlaceholder')}
          aria-label={t('feed.comment')}
          className={styles.commentInput}
        />
        <button
          type="submit"
          className={styles.iconButton}
          aria-label={t('feed.send')}
          disabled={sending || !draft.trim()}
        >
          <Icon name="send" size={20} color="var(--accent-deep)" />
        </button>
      </form>
    </div>
  );
}

/** The coming releases of the reader's series: a card of its own, nothing to react to. */
function Releases() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data: releases = [] } = useGetApiReleasesUpcoming({ limit: 3 });
  if (releases.length === 0) return null;
  return (
    <section className={styles.releases}>
      <h3 className={styles.releasesTitle}>
        <Icon name="calendar_month" size={18} color="var(--accent-deep)" />
        {t('feed.releases')}
      </h3>
      <ul className={styles.releaseList}>
        {releases.map((release) => (
          <li key={release.id}>
            <button type="button" className={styles.release} onClick={() => navigate(`/series/${release.seriesId}`)}>
              <span className={styles.bookTitle}>{release.seriesTitle ?? '—'}</span>
              <span className={styles.bookAuthors}>
                {[releaseVolumeLabel(release, t), formatReleaseDate(release, t)].filter(Boolean).join(' · ')}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

import { useTranslation } from 'react-i18next';
import { useGetApiWorksIdFriends, type FriendOnWorkDto } from '../../api/generated/librarius';
import { colorFor } from '../../shared/ui/coverPalette';
import { initials } from '../author/author';
import { ownershipLabelKey, statusLabelKey } from '../../shared/statuses';
import styles from './FriendsOnWork.module.css';

/** What a friend's copy says, in the order a reader looks for it. */
function facts(friend: FriendOnWorkDto, t: (key: string, options?: Record<string, unknown>) => string): string[] {
  const out: string[] = [];
  if (friend.status) out.push(t(statusLabelKey(friend.status)));
  if (friend.ownership && friend.ownership !== 'NOT_OWNED') out.push(t(ownershipLabelKey(friend.ownership)));
  if (friend.wished && friend.ownership !== 'WISHLIST') out.push(t('friends.wished'));
  return out;
}

/**
 * The people the reader follows who have this title too (#452): who read it, own it or want
 * it, and the category they filed it under. Hidden when there is nobody — a book no friend
 * holds says nothing worth a section.
 */
export function FriendsOnWork({ workId }: { workId: string | undefined }) {
  const { t } = useTranslation();
  const { data: friends = [] } = useGetApiWorksIdFriends(workId ?? '', { query: { enabled: Boolean(workId) } });
  if (friends.length === 0) return null;

  return (
    <section className={styles.section} aria-label={t('friends.title')}>
      <h3 className={styles.title}>{t('friends.title')}</h3>
      <ul className={styles.list}>
        {friends.map((friend) => {
          const name = friend.displayName ?? '—';
          return (
            <li key={friend.id} className={styles.item}>
              <span className={styles.avatar} style={{ background: colorFor(name) }} aria-hidden="true">
                {initials(name)}
              </span>
              <div className={styles.body}>
                <span className={styles.name}>{name}</span>
                <span className={styles.facts}>{facts(friend, t).join(' · ')}</span>
              </div>
              {friend.rankLabel && (
                <span
                  className={styles.rank}
                  style={friend.rankColor ? { borderColor: friend.rankColor, color: friend.rankColor } : undefined}
                >
                  {friend.rankLabel}
                </span>
              )}
              {friend.rating != null && (
                <span className={styles.stars} aria-label={t('reviews.stars', { rating: friend.rating })}>
                  {'★'.repeat(friend.rating)}
                </span>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}

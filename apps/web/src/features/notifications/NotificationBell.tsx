import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useApiAuth } from '../../shared/api';
import { Icon } from '../../shared/ui/Icon';
import { useUnreadCount } from './useUnread';
import styles from './Notifications.module.css';

/** The bell (#516): the way to the notifications, with the count of unread ones. Signed in only. */
export function NotificationBell({ className }: { className?: string }) {
  const { t } = useTranslation();
  const { authed } = useApiAuth();
  const unread = useUnreadCount();
  if (!authed) return null;
  return (
    <Link
      to="/notifications"
      className={`${styles.bell} ${className ?? ''}`}
      aria-label={unread > 0 ? t('notifications.bellUnread', { count: unread }) : t('notifications.title')}
    >
      <Icon name="notifications" size={22} color="var(--ink-soft)" fill={unread > 0} />
      {unread > 0 && (
        <span className={styles.badge} aria-hidden="true">
          {unread > 99 ? '99+' : unread}
        </span>
      )}
    </Link>
  );
}

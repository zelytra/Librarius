import { NavLink } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Icon } from '../shared/ui/Icon';
import { NOTIFICATIONS_DESTINATION, SETTINGS_DESTINATION, type Destination } from './navigation';
import { useUnreadCount } from '../features/notifications/useUnread';
import { useAccountEntries, useNavDestinations } from './useNavDestinations';
import { ProfileEntry } from './ProfileEntry';
import { useUnreadReports } from '../features/admin/useUnreadReports';
import styles from './SideNav.module.css';

/**
 * The navigation of a wide viewport: a persistent column beside the page rather than a
 * bar under it. Mounted by `AppShell` from `--bp-tablet` up, in place of `BottomNav`.
 *
 * It has two forms, and the component draws neither of them: `SideNav.module.css` reads
 * `--nav-side-*` from `tokens.css`, which is the only file that knows a viewport width.
 * A rail between the two breakpoints — icon over a small label, the bottom bar's tab
 * stood on its side — and a 240px sidebar past `--bp-desktop`, which is the width that
 * breakpoint was derived for.
 */
export function SideNav() {
  const { t } = useTranslation();
  const destinations = useNavDestinations(true);
  const { admin, me } = useAccountEntries();
  const unreadReports = useUnreadReports(admin != null);

  return (
    <nav className={styles.nav} aria-label={t('nav.label')}>
      {destinations.map((destination) => (
        <Entry key={destination.to} destination={destination} />
      ))}
      {/* Pushed to the bottom and set behind a hairline: what is about the account rather
          than a place to browse — the administration (#556), the reader's own profile
          (#555), then Settings. */}
      <div className={styles.footer}>
        {admin && <Entry destination={admin} reports={unreadReports} />}
        {me && <ProfileEntry me={me} className={`${styles.entry} ${styles.profile}`} labelClassName={styles.label} />}
        <Entry destination={SETTINGS_DESTINATION} />
      </div>
    </nav>
  );
}

/** `reports`: the unread reports on the Admin entry (#565), counted like the bell's. */
function Entry({ destination, reports = 0 }: { destination: Destination; reports?: number }) {
  const { t } = useTranslation();
  const unread = useUnreadCount();
  const bell = destination.to === NOTIFICATIONS_DESTINATION.to;
  const count = bell ? unread : reports;
  const label = count === 0
    ? undefined
    : bell
      ? t('notifications.bellUnread', { count })
      : t('nav.adminUnread', { count, label: t(destination.labelKey) });

  return (
    <NavLink
      to={destination.to}
      aria-label={label}
      // Home is the only prefix of every other route: without this it would read as
      // active everywhere.
      end={destination.to === '/'}
      className={styles.entry}
    >
      {/* The active pill is drawn from the `aria-current="page"` NavLink already sets
          (see the stylesheet), so what is announced and what is seen cannot drift
          apart. The filled glyph is the one thing CSS cannot reach. */}
      {({ isActive }) => (
        <>
          <Icon name={destination.icon} size={24} fill={isActive} />
          <span className={styles.label}>{t(destination.labelKey)}</span>
          {count > 0 && (
            <span className={styles.badge} aria-hidden="true">
              {count > 99 ? '99+' : count}
            </span>
          )}
        </>
      )}
    </NavLink>
  );
}

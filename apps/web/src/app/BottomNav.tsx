import { useEffect, useRef, useState } from 'react';
import { NavLink, useLocation } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Icon } from '../shared/ui/Icon';
import { NOTIFICATIONS_DESTINATION, PHONE_PRIMARY, SETTINGS_DESTINATION, type Destination } from './navigation';
import { useUnreadCount } from '../features/notifications/useUnread';
import { useAccountEntries, useNavDestinations } from './useNavDestinations';
import { ProfileEntry } from './ProfileEntry';
import styles from './BottomNav.module.css';

/**
 * The navigation of a phone, and only of a phone: `AppShell` mounts `SideNav` instead from
 * `--bp-tablet` up. Four main pages and « Plus » (#504): the sheet behind it holds every other
 * destination — the to-read pile, the stats, the readers, the administration for an admin, the
 * settings — so the bar stays compact and no page is out of reach.
 */
export function BottomNav() {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const all = useNavDestinations(true);
  const primary = all.filter((d) => PHONE_PRIMARY.includes(d.to));
  const { admin, me } = useAccountEntries();
  const others = [...all.filter((d) => !PHONE_PRIMARY.includes(d.to)), ...(admin ? [admin] : [])];
  const more = [...others, SETTINGS_DESTINATION];
  const profilePath = me ? `/people/${me.id}` : null;
  const [open, setOpen] = useState(false);
  const unread = useUnreadCount();
  const button = useRef<HTMLButtonElement>(null);
  const sheet = useRef<HTMLDivElement>(null);
  const current = more.some((d) => pathname === d.to || pathname.startsWith(`${d.to}/`)) || pathname === profilePath;

  const close = (focusBack: boolean) => {
    setOpen(false);
    if (focusBack) button.current?.focus();
  };

  useEffect(() => {
    if (!open) return;
    sheet.current?.querySelector<HTMLElement>('a')?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close(true);
    };
    const onPointer = (event: PointerEvent) => {
      const target = event.target as Node;
      if (!sheet.current?.contains(target) && !button.current?.contains(target)) close(false);
    };
    document.addEventListener('keydown', onKey);
    document.addEventListener('pointerdown', onPointer);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('pointerdown', onPointer);
    };
  }, [open]);

  return (
    <>
      {open && (
        <div ref={sheet} role="dialog" aria-label={t('nav.more')} className={styles.sheet}>
          <ul className={styles.sheetList}>
            {others.map((destination) => (
              <li key={destination.to}>
                <NavLink
                  to={destination.to}
                  className={styles.sheetEntry}
                  onClick={() => close(false)}
                  aria-label={destination.to === NOTIFICATIONS_DESTINATION.to && unread > 0
                    ? t('notifications.bellUnread', { count: unread }) : undefined}
                >
                  {({ isActive }) => (
                    <>
                      <Icon name={destination.icon} size={22} fill={isActive} />
                      <span>{t(destination.labelKey)}</span>
                      {destination.to === NOTIFICATIONS_DESTINATION.to && unread > 0 && (
                        <span className={styles.badge} aria-hidden="true">{unread > 99 ? '99+' : unread}</span>
                      )}
                    </>
                  )}
                </NavLink>
              </li>
            ))}
            {/* The reader's own profile (#555), just above Settings. */}
            {me && (
              <li>
                <ProfileEntry me={me} className={styles.sheetEntry} onClick={() => close(false)} />
              </li>
            )}
            <li>
              <NavLink to={SETTINGS_DESTINATION.to} className={styles.sheetEntry} onClick={() => close(false)}>
                {({ isActive }) => (
                  <>
                    <Icon name={SETTINGS_DESTINATION.icon} size={22} fill={isActive} />
                    <span>{t(SETTINGS_DESTINATION.labelKey)}</span>
                  </>
                )}
              </NavLink>
            </li>
          </ul>
        </div>
      )}
      <nav className={styles.nav}>
        {primary.map((tab) => (
          <Tab key={tab.to} destination={tab} />
        ))}
        <button
          ref={button}
          type="button"
          className={styles.tab}
          aria-expanded={open}
          aria-haspopup="dialog"
          data-current={current}
          onClick={() => setOpen((value) => !value)}
        >
          <span className={`${styles.tabContent} ${current || open ? styles.tabContentActive : ''}`}>
            {/* A dot when something is unread: the sheet's bell entry says how many (#516). */}
            {unread > 0 && <span className={styles.dot} aria-hidden="true" />}
            <Icon name="menu" size={25} />
            <span className={styles.label}>{t('nav.more')}</span>
          </span>
        </button>
      </nav>
    </>
  );
}

function Tab({ destination }: { destination: Destination }) {
  const { t } = useTranslation();
  return (
    <NavLink to={destination.to} end={destination.to === '/'} className={styles.tab}>
      {({ isActive }) => (
        <span className={`${styles.tabContent} ${isActive ? styles.tabContentActive : ''}`}>
          <Icon name={destination.icon} size={25} fill={isActive} />
          <span className={styles.label}>{t(destination.labelKey)}</span>
        </span>
      )}
    </NavLink>
  );
}

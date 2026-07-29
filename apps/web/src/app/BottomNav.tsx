import { NavLink } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Icon } from '../shared/ui/Icon';
import { NAV_DESTINATIONS } from './navigation';
import styles from './BottomNav.module.css';

/**
 * The navigation of a phone, and only of a phone: `AppShell` mounts `SideNav` instead
 * from `--bp-tablet` up. Its five tabs and its markup are what they have always been —
 * the destinations simply moved to `navigation.ts`, where the side navigation reads the
 * same list.
 */
export function BottomNav() {
  const { t } = useTranslation();
  return (
    <nav className={styles.nav}>
      {NAV_DESTINATIONS.map((tab) => (
        <NavLink key={tab.to} to={tab.to} end={tab.to === '/'} className={styles.tab}>
          {({ isActive }) => (
            <span
              className={`${styles.tabContent} ${isActive ? styles.tabContentActive : ''}`}
            >
              <Icon name={tab.icon} size={25} fill={isActive} />
              <span className={styles.label}>{t(tab.labelKey)}</span>
            </span>
          )}
        </NavLink>
      ))}
    </nav>
  );
}

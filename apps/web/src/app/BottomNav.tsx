import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../shared/ui/Icon';
import styles from './BottomNav.module.css';

const TABS = [
  { to: '/', icon: 'cottage', labelKey: 'nav.home' },
  { to: '/collection', icon: 'auto_stories', labelKey: 'nav.collection' },
  { to: '/discover', icon: 'search', labelKey: 'nav.discover' },
  { to: '/wishlist', icon: 'favorite', labelKey: 'nav.wishlist' },
  { to: '/stats', icon: 'insights', labelKey: 'nav.stats' },
] as const;

export function BottomNav() {
  const { t } = useTranslation();
  return (
    <nav className={styles.nav}>
      {TABS.map((tab) => (
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

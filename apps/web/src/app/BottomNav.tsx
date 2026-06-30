import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../shared/ui/Icon';

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
    <nav
      style={{
        flex: '0 0 auto',
        height: 84,
        background: 'color-mix(in srgb, var(--surface) 92%, transparent)',
        backdropFilter: 'blur(12px)',
        borderTop: '1px solid var(--chip)',
        display: 'flex',
        alignItems: 'flex-start',
        padding: '11px 12px 0',
      }}
    >
      {TABS.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end={tab.to === '/'}
          style={{ flex: 1, textDecoration: 'none' }}
        >
          {({ isActive }) => (
            <span
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 4,
                color: isActive ? 'var(--accent-deep)' : 'var(--faint)',
              }}
            >
              <Icon name={tab.icon} size={25} fill={isActive} />
              <span style={{ fontSize: 10, fontWeight: 600 }}>{t(tab.labelKey)}</span>
            </span>
          )}
        </NavLink>
      ))}
    </nav>
  );
}

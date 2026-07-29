import { NavLink } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Icon } from '../shared/ui/Icon';
import { NAV_DESTINATIONS, SETTINGS_DESTINATION, type Destination } from './navigation';
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

  return (
    <nav className={styles.nav} aria-label={t('nav.label')}>
      {NAV_DESTINATIONS.map((destination) => (
        <Entry key={destination.to} destination={destination} />
      ))}
      {/* Pushed to the bottom and set behind a hairline: Settings configures the
          application, it is not a sixth place to browse. */}
      <div className={styles.footer}>
        <Entry destination={SETTINGS_DESTINATION} />
      </div>
    </nav>
  );
}

function Entry({ destination }: { destination: Destination }) {
  const { t } = useTranslation();

  return (
    <NavLink
      to={destination.to}
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
        </>
      )}
    </NavLink>
  );
}

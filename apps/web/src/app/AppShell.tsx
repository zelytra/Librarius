import { Outlet, useLocation } from 'react-router';
import { BottomNav } from './BottomNav';
import styles from './AppShell.module.css';

// The bottom bar is hidden on "full screen" screens (detail, series, settings).
const HIDDEN_NAV_PREFIXES = ['/detail', '/series', '/settings'];

export function AppShell() {
  const location = useLocation();
  const showNav = !HIDDEN_NAV_PREFIXES.some((p) => location.pathname.startsWith(p));

  return (
    <div className={styles.viewport}>
      <div className={styles.frame}>
        <main className={`scroll-x ${styles.content}`}>
          <Outlet />
        </main>
        {showNav && <BottomNav />}
      </div>
    </div>
  );
}

import { Suspense } from 'react';
import { Outlet, useLocation } from 'react-router';
import { BottomNav } from './BottomNav';
import { Loading } from '../shared/ui/states';
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
          {/* The screens behind the routes are code split (see App.tsx): this boundary
              covers the moment their chunk is on the wire, and sits inside the frame so
              the bottom bar does not disappear during the fetch. */}
          <Suspense fallback={<Loading />}>
            <Outlet />
          </Suspense>
        </main>
        {showNav && <BottomNav />}
      </div>
    </div>
  );
}

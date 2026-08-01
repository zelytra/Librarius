import { Suspense } from 'react';
import { Outlet, useLocation } from 'react-router';
import { BottomNav } from './BottomNav';
import { SideNav } from './SideNav';
import { useProfileLanguage } from './useProfileLanguage';
import { Loading } from '../shared/ui/states';
import { useViewportAtLeast } from '../shared/ui/breakpoints';
import styles from './AppShell.module.css';

/**
 * The bottom bar is hidden on "full screen" screens (detail, series, settings).
 *
 * It applies to the bottom bar only, and deliberately so. The reasoning behind it is a
 * phone's: the bar costs 84px of vertical room on a 900px-tall frame, and Detail carries
 * its own back button, so a screen the reader has opened *into* takes the whole height.
 * None of that holds beside a side navigation — it costs horizontal room, of which a wide
 * viewport has plenty; hiding it would shift the whole page sideways the moment a cover is
 * clicked and hand the reader browser-back as their only way out of Detail. The side
 * navigation is therefore persistent, on every route.
 */
const HIDDEN_NAV_PREFIXES = ['/detail', '/series', '/settings'];

export function AppShell() {
  const location = useLocation();
  // Once the profile is loaded, the account's saved language drives the interface (#75).
  useProfileLanguage();
  // Exactly one navigation is mounted: the bar is replaced past --bp-tablet, not stacked
  // with the column or hidden by CSS while still in the accessibility tree.
  const wide = useViewportAtLeast('tablet');
  const showBottomNav = !HIDDEN_NAV_PREFIXES.some((p) => location.pathname.startsWith(p));

  return (
    <div className={styles.viewport}>
      {/* Column with the bar under the page, row with the navigation beside it. The
          class comes from the same breakpoint that decided which navigation to render —
          `AppShell.module.css` still holds no media query of its own. */}
      <div className={wide ? `${styles.frame} ${styles.frameSideNav}` : styles.frame}>
        {wide && <SideNav />}
        <main className={`scroll-x ${styles.content}`}>
          {/* The screens behind the routes are code split (see App.tsx): this boundary
              covers the moment their chunk is on the wire, and sits inside the frame so
              the navigation does not disappear during the fetch. */}
          <Suspense fallback={<Loading />}>
            <Outlet />
          </Suspense>
        </main>
        {!wide && showBottomNav && <BottomNav />}
      </div>
    </div>
  );
}

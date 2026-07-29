import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { AppShell } from './app/AppShell';
import { ErrorBoundary } from './shared/ui/ErrorBoundary';
import { ErrorState, Loading } from './shared/ui/states';
import { useApiAuth } from './shared/api';
import { HomePage } from './features/home/HomePage';

// Home is imported eagerly: it is the landing route and the `*` fallback, so making it
// lazy would only add a round trip to the critical path. The other screens are code
// split — the first load stops paying for eight screens nobody is looking at, and the
// service worker precaches their chunks right after, so a later navigation is offline
// and instant anyway. The Suspense boundary lives in AppShell, which keeps the bottom
// bar painted while a screen loads.
const SettingsPage = lazy(() =>
  import('./features/settings/SettingsPage').then((m) => ({ default: m.SettingsPage })),
);
const CategoriesPage = lazy(() =>
  import('./features/categories/CategoriesPage').then((m) => ({ default: m.CategoriesPage })),
);
const CollectionPage = lazy(() =>
  import('./features/collection/CollectionPage').then((m) => ({ default: m.CollectionPage })),
);
const DetailPage = lazy(() =>
  import('./features/detail/DetailPage').then((m) => ({ default: m.DetailPage })),
);
const DiscoverPage = lazy(() =>
  import('./features/discover/DiscoverPage').then((m) => ({ default: m.DiscoverPage })),
);
const SeriesPage = lazy(() =>
  import('./features/series/SeriesPage').then((m) => ({ default: m.SeriesPage })),
);
const WishlistPage = lazy(() =>
  import('./features/wishlist/WishlistPage').then((m) => ({ default: m.WishlistPage })),
);
const StatsPage = lazy(() =>
  import('./features/stats/StatsPage').then((m) => ({ default: m.StatsPage })),
);
// The landing page is split like the rest, and for a stronger reason: it is the one
// screen a reader sees exactly once. Bundling it eagerly would put a marketing page in
// the payload every returning visitor waits on, which is the opposite of the point.
const LandingPage = lazy(() =>
  import('./features/landing/LandingPage').then((m) => ({ default: m.LandingPage })),
);

/**
 * What `/` is, which depends on who is asking.
 *
 * A reader with a library gets their dashboard; a stranger gets the landing page, because
 * the front door used to open onto a sign-in gate and nothing else (#80). The redirect
 * waits for the session to be resolved: until then nothing is known — not even whether
 * there is one — and jumping early would flash a marketing page at someone who is signed
 * in. During that moment Home renders, and its own `LoginGate` shows the opening screen.
 *
 * Only the root does this. Every other route keeps its gate, so a shared link to a title
 * still prompts in place and lands on that title after sign-in rather than on a pitch.
 */
function RootRoute() {
  const { authed, loading } = useApiAuth();
  if (!loading && !authed) return <Navigate to="/welcome" replace />;
  return <HomePage />;
}

function App() {
  const { t } = useTranslation();

  // The boundary sits above the routes: a render exception in any screen then shows a
  // message and a reload button rather than blanking the whole page.
  return (
    <ErrorBoundary
      fallback={
        <ErrorState
          title={t('errors.boundary.title')}
          message={t('errors.boundary.message')}
          retryLabel={t('errors.boundary.reload')}
          onRetry={() => window.location.reload()}
        />
      }
    >
      <Routes>
        {/* Outside the shell: no bottom bar, no phone card — a public page is not a
            screen of the application, and it carries its own layout. Its Suspense
            boundary lives here for the same reason, AppShell's being out of reach. */}
        <Route
          path="welcome"
          element={
            <Suspense fallback={<Loading />}>
              <LandingPage />
            </Suspense>
          }
        />
        <Route element={<AppShell />}>
          <Route index element={<RootRoute />} />
          <Route path="collection" element={<CollectionPage />} />
          <Route path="categories" element={<CategoriesPage />} />
          <Route path="discover" element={<DiscoverPage />} />
          <Route path="wishlist" element={<WishlistPage />} />
          <Route path="stats" element={<StatsPage />} />
          <Route path="detail/:id" element={<DetailPage />} />
          <Route path="series/:id" element={<SeriesPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<HomePage />} />
        </Route>
      </Routes>
    </ErrorBoundary>
  );
}

export default App;

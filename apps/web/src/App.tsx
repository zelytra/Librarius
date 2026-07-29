import { lazy } from 'react';
import { Routes, Route } from 'react-router';
import { useTranslation } from 'react-i18next';
import { AppShell } from './app/AppShell';
import { ErrorBoundary } from './shared/ui/ErrorBoundary';
import { ErrorState } from './shared/ui/states';
import { HomePage } from './features/home/HomePage';

// Home is imported eagerly: it is the landing route and the `*` fallback, so making it
// lazy would only add a round trip to the critical path. The other screens are code
// split — the first load stops paying for seven screens nobody is looking at, and the
// service worker precaches their chunks right after, so a later navigation is offline
// and instant anyway. The Suspense boundary lives in AppShell, which keeps the bottom
// bar painted while a screen loads.
const SettingsPage = lazy(() =>
  import('./features/settings/SettingsPage').then((m) => ({ default: m.SettingsPage })),
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
        <Route element={<AppShell />}>
          <Route index element={<HomePage />} />
          <Route path="collection" element={<CollectionPage />} />
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

import { Routes, Route } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AppShell } from './app/AppShell';
import { ErrorBoundary } from './shared/ui/ErrorBoundary';
import { ErrorState } from './shared/ui/states';
import { HomePage } from './features/home/HomePage';
import { SettingsPage } from './features/settings/SettingsPage';
import { CollectionPage } from './features/collection/CollectionPage';
import { DetailPage } from './features/detail/DetailPage';
import { DiscoverPage } from './features/discover/DiscoverPage';
import { WishlistPage } from './features/wishlist/WishlistPage';
import { StatsPage } from './features/stats/StatsPage';

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
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<HomePage />} />
        </Route>
      </Routes>
    </ErrorBoundary>
  );
}

export default App;

import { Routes, Route } from 'react-router-dom';
import { AppShell } from './app/AppShell';
import { HomePage } from './features/home/HomePage';
import { SettingsPage } from './features/settings/SettingsPage';
import { CollectionPage } from './features/collection/CollectionPage';
import { DetailPage } from './features/detail/DetailPage';
import { DiscoverPage } from './features/discover/DiscoverPage';
import { WishlistPage } from './features/wishlist/WishlistPage';
import { StatsPage } from './features/stats/StatsPage';

function App() {
  return (
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
  );
}

export default App;

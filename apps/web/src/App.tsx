import { Routes, Route } from 'react-router-dom';
import { AppShell } from './app/AppShell';
import { HomePage } from './features/home/HomePage';
import { SettingsPage } from './features/settings/SettingsPage';
import { CollectionPage } from './features/collection/CollectionPage';
import { DetailPage } from './features/detail/DetailPage';
import { Placeholder } from './features/Placeholder';

function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HomePage />} />
        <Route path="collection" element={<CollectionPage />} />
        <Route path="discover" element={<Placeholder titleKey="discover.title" />} />
        <Route path="wishlist" element={<Placeholder titleKey="wishlist.title" />} />
        <Route path="stats" element={<Placeholder titleKey="stats.title" />} />
        <Route path="detail/:id" element={<DetailPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="*" element={<HomePage />} />
      </Route>
    </Routes>
  );
}

export default App;

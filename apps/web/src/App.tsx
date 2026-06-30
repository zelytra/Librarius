import { Routes, Route } from 'react-router-dom';
import { AppShell } from './app/AppShell';
import { HomePage } from './features/home/HomePage';
import { SettingsPage } from './features/settings/SettingsPage';
import { Placeholder } from './features/Placeholder';

function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HomePage />} />
        <Route path="collection" element={<Placeholder titleKey="collection.title" />} />
        <Route path="discover" element={<Placeholder titleKey="discover.title" />} />
        <Route path="wishlist" element={<Placeholder titleKey="wishlist.title" />} />
        <Route path="stats" element={<Placeholder titleKey="stats.title" />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="*" element={<HomePage />} />
      </Route>
    </Routes>
  );
}

export default App;

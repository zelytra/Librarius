import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ThemeProvider } from '../shared/theme/ThemeProvider';
import '../i18n';

/**
 * Monte un écran avec les fournisseurs dont il a besoin (thème, i18n, routeur).
 *
 * L'authentification n'est pas montée ici : les fichiers de test qui en ont besoin
 * substituent `react-oidc-context` par `test/oidcMock`.
 */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', path }: { route?: string; path?: string } = {},
) {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[route]}>
        {path ? (
          <Routes>
            <Route path={path} element={ui} />
          </Routes>
        ) : (
          ui
        )}
      </MemoryRouter>
    </ThemeProvider>,
  );
}

import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ThemeProvider } from '../shared/theme/ThemeProvider';
import '../i18n';

/**
 * Mounts a screen with the providers it needs (theme, i18n, router).
 *
 * Authentication is not mounted here: the test files that need it replace
 * `react-oidc-context` with `test/oidcMock`.
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

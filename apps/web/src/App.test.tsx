import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from 'react-oidc-context';
import { QueryClientProvider } from '@tanstack/react-query';
import { expect, test } from 'vitest';
import App from './App';
import { ThemeProvider } from './shared/theme/ThemeProvider';
import { createTestQueryClient } from './test/utils';
import { oidcConfig } from './auth/oidc';
import './i18n';

function renderAt(path: string) {
  return render(
    <AuthProvider {...oidcConfig}>
      <QueryClientProvider client={createTestQueryClient()}>
        <ThemeProvider>
          <MemoryRouter initialEntries={[path]}>
            <App />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </AuthProvider>,
  );
}

test('renders the translated navigation', () => {
  renderAt('/');
  expect(screen.getByText('Accueil')).toBeInTheDocument();
  expect(screen.getByText('Collection')).toBeInTheDocument();
  expect(screen.getByText('Découvrir')).toBeInTheDocument();
});

test('the theme switcher applies the theme on <html>', () => {
  renderAt('/settings');
  fireEvent.click(screen.getByText('Nuit'));
  expect(document.documentElement.getAttribute('data-theme')).toBe('nuit');
});

test('the collection prompts for sign-in when unauthenticated', async () => {
  renderAt('/collection');
  expect(await screen.findByText(/Connecte-toi pour voir ta collection/)).toBeInTheDocument();
});

test('the wishlist prompts for sign-in when unauthenticated', async () => {
  renderAt('/wishlist');
  expect(await screen.findByText(/Connecte-toi pour voir tes souhaits/)).toBeInTheDocument();
});

test('the stats page prompts for sign-in when unauthenticated', async () => {
  renderAt('/stats');
  expect(await screen.findByText(/Connecte-toi pour voir tes statistiques/)).toBeInTheDocument();
});

test('the home page renders the header and prompts for sign-in', async () => {
  renderAt('/');
  expect(screen.getByText('Bonsoir')).toBeInTheDocument();
  expect(await screen.findByText(/Connecte-toi pour retrouver ta bibliothèque/)).toBeInTheDocument();
});

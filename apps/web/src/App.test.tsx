import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
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
  renderAt('/collection');
  expect(screen.getByText('Accueil')).toBeInTheDocument();
  expect(screen.getByText('Collection')).toBeInTheDocument();
  expect(screen.getByText('Découvrir')).toBeInTheDocument();
});

// Settings is code split, so the click has to wait for its chunk to resolve.
test('the theme switcher applies the theme on <html>', async () => {
  renderAt('/settings');
  fireEvent.click(await screen.findByText('Nuit'));
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

/**
 * The front door, and the whole of issue #80: it used to open onto a sign-in gate and
 * nothing else. What Home itself renders is covered by `features/home/HomePage.test.tsx`;
 * what is asserted here is the routing decision.
 *
 * The landing page is code split, hence `findByText` — the assertion waits for its chunk
 * as a browser would.
 */
test('a signed-out visitor at the root is shown the landing page', async () => {
  renderAt('/');
  expect(await screen.findByText('Ta bibliothèque, tome par tome.')).toBeInTheDocument();
  // Outside the shell: a public page is not a screen of the application.
  expect(screen.queryByText('Accueil')).not.toBeInTheDocument();
});

test('the landing page is reachable at its own address', async () => {
  renderAt('/welcome');
  expect(await screen.findByText("Une série n'est pas une pile de livres")).toBeInTheDocument();
});

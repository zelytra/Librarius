import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from 'react-oidc-context';
import { expect, test } from 'vitest';
import App from './App';
import { ThemeProvider } from './shared/theme/ThemeProvider';
import { oidcConfig } from './auth/oidc';
import './i18n';

function renderAt(path: string) {
  return render(
    <AuthProvider {...oidcConfig}>
      <ThemeProvider>
        <MemoryRouter initialEntries={[path]}>
          <App />
        </MemoryRouter>
      </ThemeProvider>
    </AuthProvider>,
  );
}

test('affiche la navigation traduite en français', () => {
  renderAt('/');
  expect(screen.getByText('Accueil')).toBeInTheDocument();
  expect(screen.getByText('Collection')).toBeInTheDocument();
  expect(screen.getByText('Découvrir')).toBeInTheDocument();
});

test('le commutateur de thème applique le thème sur <html>', () => {
  renderAt('/settings');
  fireEvent.click(screen.getByText('Nuit'));
  expect(document.documentElement.getAttribute('data-theme')).toBe('nuit');
});

test('la collection invite à se connecter si non authentifié', async () => {
  renderAt('/collection');
  expect(await screen.findByText(/Connecte-toi pour voir ta collection/)).toBeInTheDocument();
});

test('les souhaits invitent à se connecter si non authentifié', async () => {
  renderAt('/wishlist');
  expect(await screen.findByText(/Connecte-toi pour voir tes souhaits/)).toBeInTheDocument();
});

test('les statistiques invitent à se connecter si non authentifié', async () => {
  renderAt('/stats');
  expect(await screen.findByText(/Connecte-toi pour voir tes statistiques/)).toBeInTheDocument();
});

test('l\'accueil affiche les sections du tableau de bord', () => {
  renderAt('/');
  expect(screen.getByText('Reprendre la lecture')).toBeInTheDocument();
  expect(screen.getByText('Votre pile 2026')).toBeInTheDocument();
});

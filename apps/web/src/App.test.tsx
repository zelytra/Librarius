import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { expect, test } from 'vitest';
import App from './App';
import { ThemeProvider } from './shared/theme/ThemeProvider';
import './i18n';

function renderAt(path: string) {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </ThemeProvider>,
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

import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import App from './App';

beforeEach(() => {
  // Le client orval (fetch) lit la réponse via res.text() puis JSON.parse.
  vi.stubGlobal(
    'fetch',
    vi.fn(() =>
      Promise.resolve({
        status: 200,
        headers: new Headers(),
        text: () =>
          Promise.resolve(
            JSON.stringify({ app: 'Librarius API', message: 'Bonjour', timestamp: '2026-01-01' }),
          ),
      } as unknown as Response),
    ),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

test('affiche le titre de l\'application', async () => {
  render(<App />);
  expect(screen.getByRole('heading', { name: 'Ma Bibliothèque' })).toBeInTheDocument();
  // Laisse l'effet de connexion à l'API se résoudre pour éviter un avertissement act().
  await screen.findByText(/API connectée/);
});

test('affiche le statut de connexion à l\'API', async () => {
  render(<App />);
  await waitFor(() => {
    expect(screen.getByText(/API connectée/)).toBeInTheDocument();
  });
});

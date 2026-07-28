import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { stats } from '../../test/fixtures';
import type { StatsDto } from '../../api/generated/librarius';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { StatsPage } = await import('./StatsPage');

function statsReturns(payload: StatsDto | null, status = 200) {
  server.use(
    http.get('*/api/stats', () =>
      status === 200 ? HttpResponse.json(payload) : new HttpResponse(null, { status })),
  );
}

describe('StatsPage', () => {
  beforeEach(resetAuth);

  test('affiche les compteurs de lecture', async () => {
    statsReturns(stats());
    renderWithProviders(<StatsPage />);

    // « 12 » figure aussi dans la jauge d'objectif : on vérifie la carte, pas la page.
    const readCard = (await screen.findByText('Livres lus')).parentElement;
    expect(readCard).toHaveTextContent('12');

    expect(screen.getByText('Pages lues').parentElement).toHaveTextContent('200');
    expect(screen.getByText('Séries suivies').parentElement).toHaveTextContent('5');
    expect(screen.getByText('En cours').parentElement).toHaveTextContent('2');
  });

  test('classe les genres favoris', async () => {
    statsReturns(stats());
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText('Fantasy')).toBeInTheDocument();
    expect(screen.getByText('Science-fiction')).toBeInTheDocument();
  });

  test('renvoie vers les réglages quand aucun objectif n’est défini', async () => {
    statsReturns(stats({ goalTarget: undefined }));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText(/Définis un objectif de lecture annuel/)).toBeInTheDocument();
  });

  test('affiche le reste à lire quand un objectif est défini', async () => {
    statsReturns(stats({ goalTarget: 20, goalCurrent: 12 }));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText(/Plus que 8 titre\(s\)/)).toBeInTheDocument();
  });

  test('signale une indisponibilité plutôt qu’un écran vide', async () => {
    statsReturns(null, 500);
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText('Statistiques indisponibles.')).toBeInTheDocument();
  });

  test('invite à se connecter quand la session est absente', async () => {
    setAuthenticated(false);
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText(/Connecte-toi pour voir tes statistiques/)).toBeInTheDocument();
  });
});

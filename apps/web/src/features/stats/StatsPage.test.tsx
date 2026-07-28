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

  test('renders the reading counters', async () => {
    statsReturns(stats());
    renderWithProviders(<StatsPage />);

    // "12" also appears in the goal gauge: assert on the card, not on the whole page.
    const readCard = (await screen.findByText('Livres lus')).parentElement;
    expect(readCard).toHaveTextContent('12');

    expect(screen.getByText('Pages lues').parentElement).toHaveTextContent('200');
    expect(screen.getByText('Séries suivies').parentElement).toHaveTextContent('5');
    expect(screen.getByText('En cours').parentElement).toHaveTextContent('2');
  });

  test('ranks the favorite genres', async () => {
    statsReturns(stats());
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText('Fantasy')).toBeInTheDocument();
    expect(screen.getByText('Science-fiction')).toBeInTheDocument();
  });

  test('invites the user to set a goal rather than showing a gauge at zero', async () => {
    statsReturns(stats({ goalTarget: undefined }));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText(/Fixe-toi un objectif de lecture/)).toBeInTheDocument();
    expect(screen.getByText('Définir un objectif')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  test('renders how much is left when a goal is set', async () => {
    statsReturns(stats({ goalTarget: 20, goalCurrent: 12, goalUnit: 'PAGES' }));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText(/Encore 8 pages/)).toBeInTheDocument();
    expect(screen.getByRole('img')).toHaveAccessibleName(/12 sur 20 pages/);
  });

  test('signals an outage rather than showing an empty screen', async () => {
    statsReturns(null, 500);
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText('Statistiques indisponibles.')).toBeInTheDocument();
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText(/Connecte-toi pour voir tes statistiques/)).toBeInTheDocument();
  });
});

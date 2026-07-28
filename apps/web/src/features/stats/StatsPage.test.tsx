import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { stats, timeline } from '../../test/fixtures';
import type { StatsDto, TimelineDto } from '../../api/generated/librarius';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { StatsPage } = await import('./StatsPage');

/** The timeline defaults to the year in progress, so the fixtures follow the clock. */
const YEAR = new Date().getFullYear();

function statsReturns(payload: StatsDto | null, status = 200) {
  server.use(
    http.get('*/api/stats', () =>
      status === 200 ? HttpResponse.json(payload) : new HttpResponse(null, { status })),
  );
}

function timelineReturns(payload: TimelineDto) {
  server.use(http.get('*/api/stats/timeline', () => HttpResponse.json(payload)));
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
    expect(screen.queryByRole('img', { name: /sur .* lus en/ })).not.toBeInTheDocument();
  });

  test('renders how much is left when a goal is set', async () => {
    statsReturns(stats({ goalTarget: 20, goalCurrent: 12, goalUnit: 'PAGES' }));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText(/Encore 8 pages/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /12 sur 20 pages/ })).toBeInTheDocument();
  });

  test('signals an outage rather than showing an empty screen', async () => {
    statsReturns(null, 500);
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText('Statistiques indisponibles.')).toBeInTheDocument();
  });

  // ── Reading over time ──────────────────────────────────────────────────────

  test('charts what was read month by month, and what it adds up to', async () => {
    timelineReturns(timeline({
      points: [
        { period: `${YEAR}-01`, books: 3, pages: 300 },
        { period: `${YEAR}-03`, books: 1, pages: 150 },
      ],
      books: 4,
      pages: 450,
      pagesPerDay: 2.5,
      daysPerBook: 7,
      bestPeriod: `${YEAR}-01`,
      bestPeriodBooks: 3,
    }));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText(new RegExp(`4 titres et 450 pages terminés en ${YEAR}`)))
      .toBeInTheDocument();
    expect(screen.getByRole('img', { name: `Titres terminés chaque mois en ${YEAR}` }))
      .toBeInTheDocument();
    // The derived figures the issue asks for, next to the chart.
    expect(screen.getByText('2,5')).toBeInTheDocument();
    expect(screen.getByText('pages par jour')).toBeInTheDocument();
    expect(screen.getByText('janvier')).toBeInTheDocument();
    expect(screen.getByText('meilleur mois (3)')).toBeInTheDocument();
  });

  test('ranks the authors of the window alongside the genres', async () => {
    timelineReturns(timeline({
      books: 4,
      byAuthor: [{ label: 'Makoto Yukimura', count: 3 }, { label: 'Frank Herbert', count: 1 }],
    }));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText('Makoto Yukimura')).toBeInTheDocument();
    expect(screen.getByText('Frank Herbert')).toBeInTheDocument();
    // The genre breakdown is still there, over the whole collection.
    expect(screen.getByText('Fantasy')).toBeInTheDocument();
  });

  test('says a year holds no reading rather than drawing an empty chart', async () => {
    statsReturns(stats({ goalTarget: 30, goalUnit: 'BOOKS' }));
    timelineReturns(timeline());
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText(new RegExp(`Aucune lecture terminée en ${YEAR}`)))
      .toBeInTheDocument();
    expect(screen.queryByText('pages par jour')).not.toBeInTheDocument();
    // Not even the running total against the goal: a flat curve at zero says it twice.
    expect(screen.queryByText('Cumul et objectif')).not.toBeInTheDocument();
  });

  test('plots the running total against the goal once there is one', async () => {
    statsReturns(stats({ goalTarget: 30, goalUnit: 'BOOKS' }));
    timelineReturns(timeline({ points: [{ period: `${YEAR}-02`, books: 5, pages: 900 }], books: 5 }));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText('Cumul et objectif')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /objectif de 30 livres/ })).toBeInTheDocument();
  });

  test('signals a failing timeline without taking the counters down with it', async () => {
    server.use(http.get('*/api/stats/timeline', () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText('Historique de lecture indisponible.')).toBeInTheDocument();
    expect(screen.getByText('Livres lus')).toBeInTheDocument();
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText(/Connecte-toi pour voir tes statistiques/)).toBeInTheDocument();
  });
});

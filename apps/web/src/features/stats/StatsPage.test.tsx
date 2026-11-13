import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { stats, timeline } from '../../test/fixtures';
import type { BreakdownDto, StatsDto, TimelineDto } from '../../api/generated/librarius';
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

function breakdownReturns(payload: BreakdownDto) {
  server.use(http.get('*/api/stats/breakdown', () => HttpResponse.json(payload)));
}

describe('StatsPage', () => {
  beforeEach(resetAuth);

  /** #419: the ranks of everything read, the Bronze shelf included, dated or not. */
  test('breaks down everything read by default, every rank listed', async () => {
    breakdownReturns({
      books: 40,
      byAuthor: [],
      byPublisher: [],
      byLanguage: [],
      byRank: [
        { label: 'Or', count: 12 },
        { label: 'Argent', count: 9 },
        { label: 'Bronze', count: 4 },
      ],
    });
    renderWithProviders(<StatsPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Rangs' }));
    expect(await screen.findByText('Bronze')).toBeInTheDocument();
    expect(screen.getByText('Argent')).toBeInTheDocument();
  });

  /** #418: titles per month, a figure as soon as one title is finished. */
  test('gives the pace in titles per month', async () => {
    timelineReturns(timeline({ points: [{ period: `${YEAR}-02`, books: 6, pages: 900 }], books: 6, booksPerMonth: 1.5 }));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText('titres par mois')).toBeInTheDocument();
    expect(screen.getByText('1,5')).toBeInTheDocument();
    expect(screen.queryByText('jours par titre')).not.toBeInTheDocument();
  });

  /** #420: one running total per kind with a goal, each against its own target. */
  test('draws one cumulative curve per kind goal', async () => {
    statsReturns(stats({
      goals: [
        { kind: 'BOOK', target: 20, unit: 'BOOKS', current: 3 },
        { kind: 'MANGA', target: 50, unit: 'BOOKS', current: 8 },
      ],
    }));
    timelineReturns(timeline({
      points: [{ period: `${YEAR}-02`, books: 11, pages: 2000, byKind: { BOOK: 3, MANGA: 8 }, pagesByKind: { BOOK: 900, MANGA: 1100 } }],
      books: 11,
    }));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByRole('img', { name: /Cumul des Romans/ })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Mangas/ })).toBeInTheDocument();
    expect(screen.getByText('3 / 20 livres')).toBeInTheDocument();
    expect(screen.getByText('8 / 50 livres')).toBeInTheDocument();
  });

  /** #421: what the shelves and the wishlist are worth. */
  test('shows the value of the library and of the wishlist', async () => {
    server.use(http.get('*/api/stats/value', () => HttpResponse.json({
      currency: 'EUR', library: 1234.5, libraryOwned: 120, libraryPriced: 100,
      wishlist: 89.7, wishlistCount: 9, wishlistPriced: 9, byKind: [],
    })));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText('Valeur estimée')).toBeInTheDocument();
    expect(screen.getByText(/1\s?235\s?€/)).toBeInTheDocument();
    expect(screen.getByText('100 titres sur 120 avec un prix connu')).toBeInTheDocument();
  });

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
    expect(screen.queryByRole('progressbar', { name: /lus en/ })).not.toBeInTheDocument();
  });

  test('renders how much is left when a goal is set', async () => {
    statsReturns(stats({ goalTarget: 20, goalCurrent: 12, goalUnit: 'PAGES' }));
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText(/Encore 8 pages/)).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: /12 sur 20 pages/ })).toBeInTheDocument();
  });

  test('signals an outage rather than showing an empty screen', async () => {
    statsReturns(null, 500);
    renderWithProviders(<StatsPage />);

    expect(await screen.findByText('Statistiques indisponibles.')).toBeInTheDocument();
  });

  // ── Desktop layout (#174) ────────────────────────────────────────────────────

  test('lays the goal, the counters and the genre breakdown out in the shared dashboard grid', async () => {
    statsReturns(stats());
    renderWithProviders(<StatsPage />);

    const readCard = await screen.findByText('Livres lus');
    expect(readCard.closest('[class*="dashboard"]')).toBeInTheDocument();
  });

  // ── Reading over time ──────────────────────────────────────────────────────

  /** Every month's figure is on screen; hovering one gives its details, split by kind. */
  test('prints each month, details it on hover, and splits it by kind', async () => {
    timelineReturns(timeline({
      points: [
        { period: `${YEAR}-03`, books: 5, pages: 900, byKind: { BOOK: 2, MANGA: 3 } },
      ],
      books: 5,
      pages: 900,
    }));
    renderWithProviders(<StatsPage />);

    const march = await screen.findByRole('button', { name: /^mars : 5 livres · 900 pages — Livres 2 · Mangas 3$/ });
    expect(march).toHaveTextContent('5');

    await userEvent.hover(march);
    expect(screen.getByRole('tooltip')).toHaveTextContent('Mangas 3');

    // Split by kind from the start (#396), no click needed.
    expect(screen.getByText('Livres : 2 · Mangas : 3')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Tout' }));
    expect(screen.queryByText('Livres : 2 · Mangas : 3')).not.toBeInTheDocument();
  });

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
    expect(screen.getByRole('group', { name: `Titres terminés chaque mois en ${YEAR}` }))
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

    // The breakdown opens on everything read (#419); the year is one tap away.
    const years = await screen.findAllByRole('button', { name: String(YEAR) });
    await userEvent.click(years[years.length - 1]);
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

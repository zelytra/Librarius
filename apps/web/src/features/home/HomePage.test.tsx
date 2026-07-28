import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { catalogResult, goal, libraryItem, stats } from '../../test/fixtures';
import { http, HttpResponse, libraryReturns, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { HomePage } = await import('./HomePage');

/** The goal is set per calendar year, so the fixtures follow the clock. */
const YEAR = new Date().getFullYear();

describe('HomePage', () => {
  beforeEach(resetAuth);

  test('renders the library counters', async () => {
    server.use(http.get('*/api/stats', () => HttpResponse.json(stats({ read: 12, reading: 2, toRead: 34 }))));
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('lus')).toBeInTheDocument();
    expect(screen.getByText('en cours')).toBeInTheDocument();
    expect(screen.getByText('à lire')).toBeInTheDocument();
  });

  test('offers to resume the books being read', async () => {
    libraryReturns([libraryItem({ id: 'en-cours', status: 'READING' })]);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('Reprendre la lecture')).toBeInTheDocument();
    expect(screen.getByText('1 en cours')).toBeInTheDocument();
  });

  /**
   * The point of the carousel is picking a book back up: without the position it was a
   * shelf of titles the user had opened, and no hint of how far in they were.
   */
  test('shows how far into each book being read the user is', async () => {
    libraryReturns([
      libraryItem({ id: 'en-cours', status: 'READING', progress: { currentPage: 120, percent: 40 } }),
    ]);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('40 %')).toBeInTheDocument();
    expect(screen.getByLabelText('Progression : 40 %')).toBeInTheDocument();
  });

  test('hides the resume section when nothing is being read', async () => {
    libraryReturns([libraryItem({ status: 'READ' })]);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('Derniers lus')).toBeInTheDocument();
    expect(screen.queryByText('Reprendre la lecture')).not.toBeInTheDocument();
  });

  test('announces upcoming releases as indicative', async () => {
    server.use(
      http.get('*/api/catalog/upcoming', () =>
        HttpResponse.json([catalogResult({ title: 'Berserk 43', releaseDate: '2026-09-01' })])),
    );
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('Berserk 43')).toBeInTheDocument();
    expect(screen.getByText(/Dates indicatives/)).toBeInTheDocument();
  });

  /**
   * Emptiness is read off the counters, not off a shelf: a library made only of
   * owned-but-unread titles fills neither of the two shelves.
   */
  test('points to Discover when the library is empty', async () => {
    libraryReturns([]);
    server.use(http.get('*/api/stats', () =>
      HttpResponse.json(stats({ read: 0, reading: 0, toRead: 0 }))));
    renderWithProviders(<HomePage />);

    expect(await screen.findByText(/Ta bibliothèque est vide/)).toBeInTheDocument();
  });

  test('does not offer to add titles to a library made only of unread ones', async () => {
    libraryReturns([]);
    server.use(http.get('*/api/stats', () =>
      HttpResponse.json(stats({ read: 0, reading: 0, toRead: 7 }))));
    renderWithProviders(<HomePage />);

    await screen.findByText('à lire');
    expect(screen.queryByText(/Ta bibliothèque est vide/)).not.toBeInTheDocument();
  });

  /** Each shelf asks the server for its own status rather than for everything. */
  test('fetches only the shelves it displays', async () => {
    const statuses: (string | null)[] = [];
    server.use(http.get('*/api/library', ({ request }) => {
      statuses.push(new URL(request.url).searchParams.get('status'));
      return HttpResponse.json({ items: [], page: 0, size: 12, total: 0 });
    }));

    renderWithProviders(<HomePage />);
    await screen.findByText('lus');

    expect(statuses).toContain('READING');
    expect(statuses).toContain('READ');
    expect(statuses).not.toContain(null);
  });

  // ── Annual reading goal ────────────────────────────────────────────────────

  test('gauges the annual goal and says what pace holds it', async () => {
    server.use(http.get('*/api/stats', () =>
      HttpResponse.json(stats({ goalTarget: 30, goalCurrent: 12, goalUnit: 'BOOKS' }))));
    renderWithProviders(<HomePage />);

    expect(await screen.findByText(`Objectif ${YEAR}`)).toBeInTheDocument();
    expect(screen.getByText(/Encore 18 livres/)).toBeInTheDocument();
    expect(screen.getByText(/pour tenir le rythme/)).toBeInTheDocument();
    // A progress bar, not a picture: the value has to reach assistive tech as a value.
    const gauge = screen.getByRole('progressbar', { name: /12 sur 30 livres/ });
    expect(gauge).toHaveAttribute('aria-valuenow', '40');
    expect(gauge).toHaveAttribute('aria-valuemax', '100');
  });

  /**
   * The unit agrees with the number in front of it. Rounding the pace up made "1" a common
   * figure, and "1 livres" is the kind of wording a reader notices before anything else.
   */
  test('says one title in the singular', async () => {
    server.use(http.get('*/api/stats', () =>
      HttpResponse.json(stats({ goalTarget: 30, goalCurrent: 29, goalUnit: 'BOOKS' }))));
    renderWithProviders(<HomePage />);

    expect(await screen.findByText(/^Encore 1 livre avant la fin/)).toBeInTheDocument();
  });

  /** An empty state, not a ring stuck at zero: the two look the same and only one helps. */
  test('invites the user to set a goal rather than gauging nothing', async () => {
    server.use(http.get('*/api/stats', () => HttpResponse.json(stats({ goalTarget: undefined }))));
    renderWithProviders(<HomePage />);

    expect(await screen.findByText(/Fixe-toi un objectif de lecture/)).toBeInTheDocument();
    expect(screen.queryByRole('progressbar', { name: /lus en/ })).not.toBeInTheDocument();
  });

  /**
   * The year turns over on its own, the goal does not. Rather than an empty form on
   * 1 January, the card offers the target of the year that just ended.
   */
  test('offers to carry the previous year’s goal over', async () => {
    const saved: { year: string; body: unknown }[] = [];
    server.use(
      http.get('*/api/stats', () => HttpResponse.json(stats({ goalTarget: undefined }))),
      http.get('*/api/goals', () =>
        HttpResponse.json([goal({ year: YEAR - 1, targetCount: 24, unit: 'VOLUMES' })])),
      http.put('*/api/goals/:year', async ({ params, request }) => {
        saved.push({ year: String(params.year), body: await request.json() });
        return HttpResponse.json({ id: 'goal-1' });
      }),
    );
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('Nouvelle année, nouvel objectif')).toBeInTheDocument();
    expect(screen.getByText(new RegExp(`En ${YEAR - 1}, tu visais 24 tomes`))).toBeInTheDocument();

    await userEvent.click(screen.getByText('Reprendre 24 tomes'));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toEqual({
      year: String(YEAR),
      body: { targetCount: 24, unit: 'VOLUMES' },
    });
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText(/Connecte-toi pour retrouver ta bibliothèque/)).toBeInTheDocument();
  });
});

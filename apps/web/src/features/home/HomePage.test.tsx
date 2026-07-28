import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { catalogResult, libraryItem, stats } from '../../test/fixtures';
import { http, HttpResponse, libraryReturns, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { HomePage } = await import('./HomePage');

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

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText(/Connecte-toi pour retrouver ta bibliothèque/)).toBeInTheDocument();
  });

  // ── Annual reading goal ────────────────────────────────────────────────────

  /**
   * The pace itself moves with the day it is looked at and is pinned down on frozen
   * dates in `shared/goal.test.ts`; what is asserted here is what the screen makes of it.
   */
  test('shows the annual goal with what is left to read', async () => {
    server.use(http.get('*/api/stats', () =>
      HttpResponse.json(stats({ goalTarget: 30, goalCurrent: 12, goalUnit: 'BOOKS' }))));
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('12 / 30 livres')).toBeInTheDocument();
    expect(screen.getByText(/Encore 18 livres/)).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '40');
  });

  test('labels the goal in the unit it was set in', async () => {
    server.use(http.get('*/api/stats', () =>
      HttpResponse.json(stats({ goalTarget: 12000, goalCurrent: 4500, goalUnit: 'PAGES' }))));
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('4500 / 12000 pages')).toBeInTheDocument();
    expect(screen.getByText(/Encore 7500 pages/)).toBeInTheDocument();
  });

  test('celebrates a goal met instead of asking for a pace', async () => {
    server.use(http.get('*/api/stats', () =>
      HttpResponse.json(stats({ goalTarget: 30, goalCurrent: 31, goalUnit: 'BOOKS' }))));
    renderWithProviders(<HomePage />);

    expect(await screen.findByText(/Objectif atteint/)).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100');
  });

  /** No goal set is an invitation, never a gauge at zero — an empty bar reads as failure. */
  test('invites the user to set a goal rather than showing an empty gauge', async () => {
    server.use(http.get('*/api/stats', () =>
      HttpResponse.json(stats({ goalTarget: undefined, goalCurrent: 0 }))));
    renderWithProviders(<HomePage />);

    expect(await screen.findByText(/Aucun objectif cette année/)).toBeInTheDocument();
    expect(screen.getByText('Définir')).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });
});

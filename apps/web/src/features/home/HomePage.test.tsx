import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders, TestProviders } from '../../test/utils';
import { dashboardLayout, goal, libraryItem, stats, upcomingRelease } from '../../test/fixtures';
import { http, HttpResponse, libraryReturns, server, upcomingReleasesReturn } from '../../test/server';
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

  // ── To-read pile (#166) ─────────────────────────────────────────────────────

  test('shelves the titles the reader owns and has never opened', async () => {
    libraryReturns([
      libraryItem({ id: 'pal-1', status: 'OWNED', book: { kind: 'BOOK', title: 'La Horde du Contrevent' } }),
    ]);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('Ta pile à lire')).toBeInTheDocument();
    expect(screen.getByText('La Horde du Contrevent')).toBeInTheDocument();
  });

  test('hides the pile when nothing is waiting to be read', async () => {
    libraryReturns([libraryItem({ id: 'lu', status: 'READ' })]);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('Derniers lus')).toBeInTheDocument();
    expect(screen.queryByText('Ta pile à lire')).not.toBeInTheDocument();
  });

  /**
   * The pile is what is *waiting*, and a title given up on is not waiting for anything
   * (#163). The four statuses being exclusive, asking for `OWNED` is enough — this test is
   * what keeps that true the day someone widens the filter.
   */
  test('leaves the titles the reader gave up on out of the pile', async () => {
    libraryReturns([
      libraryItem({ id: 'pal-1', status: 'OWNED', book: { kind: 'BOOK', title: 'Titre qui attend' } }),
      libraryItem({ id: 'abandonne', status: 'ABANDONED', book: { kind: 'BOOK', title: 'Titre abandonné' } }),
    ]);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('Titre qui attend')).toBeInTheDocument();
    expect(screen.queryByText('Titre abandonné')).not.toBeInTheDocument();
  });

  /**
   * The shelf holds a dozen covers, the pile can hold hundreds: the header reports the
   * server's count of the whole thing, not the length of the page it drew.
   */
  test('announces the size of the whole pile, not of the shelf', async () => {
    libraryReturns(Array.from({ length: 20 }, (_, i) =>
      libraryItem({ id: `pal-${i}`, status: 'OWNED', book: { kind: 'BOOK', title: `Titre ${i}` } })));
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('20 en attente')).toBeInTheDocument();
    // The shelf itself is capped, so the twentieth title is not on it.
    expect(screen.queryByText('Titre 19')).not.toBeInTheDocument();
  });

  test('opens a title from the pile on its detail screen', async () => {
    libraryReturns([
      libraryItem({ id: 'pal-1', status: 'OWNED', book: { kind: 'BOOK', title: 'Titre qui attend' } }),
    ]);
    render(
      <TestProviders>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/detail/:id" element={<p>écran du titre</p>} />
        </Routes>
      </TestProviders>,
    );

    await userEvent.click(await screen.findByText('Titre qui attend'));

    expect(await screen.findByText('écran du titre')).toBeInTheDocument();
  });

  // ── Personalised upcoming releases ──────────────────────────────────────────

  test('shows the releases of the series the reader has a stake in, dated and labelled', async () => {
    upcomingReleasesReturn([
      upcomingRelease({
        seriesTitle: 'Vinland Saga',
        volumeNumber: 28,
        releaseDate: '2026-09-01',
        datePrecision: 'DAY',
        region: 'FR',
        source: 'manual',
        confidence: 'CONFIRMED',
      }),
    ]);
    renderWithProviders(<HomePage />);

    expect(await screen.findByText('Vinland Saga')).toBeInTheDocument();
    expect(screen.getByText('Tome 28')).toBeInTheDocument();
    // The region and the source travel with the date, never a bare figure on its own.
    expect(screen.getByText('Édition française')).toBeInTheDocument();
    expect(screen.getByText('1 septembre 2026')).toBeInTheDocument();
    expect(screen.getByText("Confirmé par l'éditeur")).toBeInTheDocument();
  });

  test('invites the reader to follow a series when nothing is coming', async () => {
    // Default stats are not all-zero, so the big empty-state at the bottom of the
    // dashboard does not already cover this invitation.
    renderWithProviders(<HomePage />);

    expect(await screen.findByText("Aucune sortie à venir pour l'instant.")).toBeInTheDocument();
    expect(screen.getByText('Voir mes séries')).toBeInTheDocument();
  });

  /**
   * A brand-new account already gets the big invitation at the bottom of the dashboard —
   * a second, smaller one for the same "go find something" message would just be noise.
   */
  test('does not repeat the invitation when the whole dashboard is already empty', async () => {
    libraryReturns([]);
    server.use(http.get('*/api/stats', () =>
      HttpResponse.json(stats({ read: 0, reading: 0, toRead: 0 }))));
    renderWithProviders(<HomePage />);

    expect(await screen.findByText(/Ta bibliothèque est vide/)).toBeInTheDocument();
    expect(screen.queryByText("Aucune sortie à venir pour l'instant.")).not.toBeInTheDocument();
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

  /**
   * Nor to one made only of titles the reader gave up on. Abandoned titles are counted
   * apart from the three others, so a sum that left them out would call this collection
   * empty and invite the reader to start the one they already have.
   */
  test('does not offer to add titles to a library made only of abandoned ones', async () => {
    libraryReturns([]);
    server.use(http.get('*/api/stats', () =>
      HttpResponse.json(stats({ read: 0, reading: 0, toRead: 0, abandoned: 3 }))));
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
    // The pile asks for `OWNED` alone: it is a shelf of what is waiting, so it neither
    // downloads the collection to sort it here nor scoops up the abandoned titles.
    expect(statuses).toContain('OWNED');
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

  // ── Customizable dashboard (#54) ──────────────────────────────────────────

  test('renders the sections in the order the saved layout gives, not the hard-coded one', async () => {
    server.use(http.get('*/api/dashboard/layout', () => HttpResponse.json(dashboardLayout({
      sections: [
        { code: 'recentlyRead', hidden: false },
        { code: 'counters', hidden: false },
        { code: 'goal', hidden: false },
        { code: 'upcoming', hidden: false },
        { code: 'resumeReading', hidden: false },
      ],
    }))));
    libraryReturns([
      libraryItem({ id: 'en-cours', status: 'READING' }),
      libraryItem({ id: 'lu', status: 'READ' }),
    ]);
    const { container } = renderWithProviders(<HomePage />);

    await screen.findByText('Derniers lus');
    await screen.findByText('Reprendre la lecture');
    const text = container.textContent ?? '';
    expect(text.indexOf('Derniers lus')).toBeLessThan(text.indexOf('Reprendre la lecture'));
  });

  test('does not render a section the user hid', async () => {
    server.use(http.get('*/api/dashboard/layout', () => HttpResponse.json(dashboardLayout({
      sections: [
        { code: 'resumeReading', hidden: false },
        { code: 'counters', hidden: false },
        { code: 'goal', hidden: true },
        { code: 'upcoming', hidden: false },
        { code: 'recentlyRead', hidden: false },
      ],
    }))));
    server.use(http.get('*/api/stats', () =>
      HttpResponse.json(stats({ goalTarget: 30, goalCurrent: 12, goalUnit: 'BOOKS' }))));
    renderWithProviders(<HomePage />);

    await screen.findByText('lus');
    // The layout query is not part of the loading gate (by design, see DashboardSections):
    // "lus" can paint from the default order before the real layout has arrived, so the
    // hidden section's absence is an eventual condition, not an immediate one.
    await waitFor(() => expect(screen.queryByText(`Objectif ${YEAR}`)).not.toBeInTheDocument());
  });

  /**
   * The point of #54's requirement 2: hiding a section is not the same as deleting it. The
   * panel that reorders and hides sections must go on listing a hidden one, or there would
   * be no way back to it short of guessing it used to exist.
   */
  test('a hidden section stays listed, and marked, in the customize panel', async () => {
    server.use(http.get('*/api/dashboard/layout', () => HttpResponse.json(dashboardLayout({
      sections: [
        { code: 'resumeReading', hidden: false },
        { code: 'counters', hidden: false },
        { code: 'goal', hidden: true },
        { code: 'upcoming', hidden: false },
        { code: 'recentlyRead', hidden: false },
      ],
    }))));
    renderWithProviders(<HomePage />);

    await screen.findByText('lus');
    await userEvent.click(screen.getByRole('button', { name: "Personnaliser l'accueil" }));

    expect(await screen.findByText('Objectif de lecture annuel')).toBeInTheDocument();
    expect(screen.getByText('Masquée')).toBeInTheDocument();
  });

  test('unhiding a section in the panel and saving brings it back on the dashboard', async () => {
    const saved: unknown[] = [];
    server.use(
      http.get('*/api/dashboard/layout', () => HttpResponse.json(dashboardLayout({
        sections: [
          { code: 'resumeReading', hidden: false },
          { code: 'counters', hidden: false },
          { code: 'goal', hidden: true },
          { code: 'upcoming', hidden: false },
          { code: 'recentlyRead', hidden: false },
        ],
      }))),
      http.put('*/api/dashboard/layout', async ({ request }) => {
        const body = await request.json();
        saved.push(body);
        return HttpResponse.json(body);
      }),
    );
    server.use(http.get('*/api/stats', () =>
      HttpResponse.json(stats({ goalTarget: 30, goalCurrent: 12, goalUnit: 'BOOKS' }))));
    renderWithProviders(<HomePage />);

    await screen.findByText('lus');
    await waitFor(() => expect(screen.queryByText(`Objectif ${YEAR}`)).not.toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: "Personnaliser l'accueil" }));
    await userEvent.click(await screen.findByRole('button', {
      name: "Afficher « Objectif de lecture annuel »",
    }));
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toMatchObject({
      sections: expect.arrayContaining([{ code: 'goal', hidden: false }]),
    });
    expect(await screen.findByText(`Objectif ${YEAR}`)).toBeInTheDocument();
  });

  test('moving a section up in the panel changes the order it is saved in', async () => {
    const saved: { sections?: { code: string }[] }[] = [];
    server.use(
      http.get('*/api/dashboard/layout', () => HttpResponse.json(dashboardLayout())),
      http.put('*/api/dashboard/layout', async ({ request }) => {
        const body = (await request.json()) as { sections?: { code: string }[] };
        saved.push(body);
        return HttpResponse.json(body);
      }),
    );
    renderWithProviders(<HomePage />);

    await screen.findByText('lus');
    await userEvent.click(screen.getByRole('button', { name: "Personnaliser l'accueil" }));
    // "Compteurs de lecture" is the third row by default order; moving it up swaps it
    // with "Ta pile à lire".
    await userEvent.click(await screen.findByRole('button', {
      name: "Déplacer « Compteurs de lecture » vers le haut",
    }));
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0].sections?.map((s) => s.code)).toEqual([
      'resumeReading', 'counters', 'toRead', 'goal', 'upcoming', 'recentlyRead',
    ]);
  });

  test('cancelling the customize panel discards the changes', async () => {
    let putCalled = false;
    server.use(http.put('*/api/dashboard/layout', () => {
      putCalled = true;
      return HttpResponse.json(dashboardLayout());
    }));
    renderWithProviders(<HomePage />);

    await screen.findByText('lus');
    await userEvent.click(screen.getByRole('button', { name: "Personnaliser l'accueil" }));
    await screen.findByText(/Réordonne les sections/);
    await userEvent.click(screen.getByRole('button', { name: 'Annuler' }));

    expect(screen.queryByText(/Réordonne les sections/)).not.toBeInTheDocument();
    expect(putCalled).toBe(false);
  });
});

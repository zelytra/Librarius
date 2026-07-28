import { render, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { TestProviders, renderWithProviders } from '../../test/utils';
import { BUILTIN_CATEGORIES, libraryItem, seriesSummary } from '../../test/fixtures';
import { http, HttpResponse, libraryItemReturns, seriesReturns, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { DetailPage } = await import('./DetailPage');

const ITEM = libraryItem({ id: 'item-1' });

/** A title being read, three hundred pages long so the percentages come out whole. */
const READING = libraryItem({
  id: 'item-1',
  status: 'READING',
  book: { kind: 'BOOK', title: 'Le Nom du vent', authors: 'Patrick Rothfuss', pageCount: 300 },
  progress: { currentPage: 30, percent: 10 },
});

function renderDetail(id = 'item-1') {
  return renderWithProviders(<DetailPage />, { route: `/detail/${id}`, path: '/detail/:id' });
}

/**
 * Serves `/api/library/{id}` from a single mutable item, so a test can check that the
 * screen re-reads it after a mutation instead of patching its own state.
 */
function servesMutableItem() {
  let item = { ...ITEM };
  server.use(http.get('*/api/library/:id', ({ params }) =>
    params.id === ITEM.id ? HttpResponse.json(item) : new HttpResponse(null, { status: 404 })));
  return {
    current: () => item,
    set: (next: typeof ITEM) => {
      item = next;
    },
  };
}

describe('DetailPage', () => {
  beforeEach(resetAuth);

  test('renders the title details', async () => {
    libraryItemReturns(ITEM);
    renderDetail();

    // The title shows up twice: in the header, and on the fallback cover.
    expect(await screen.findByRole('heading', { name: 'Le Nom du vent' })).toBeInTheDocument();
    expect(screen.getByText('Patrick Rothfuss')).toBeInTheDocument();
    expect(screen.getByText('720')).toBeInTheDocument();
    expect(screen.getByText('Chronique du tueur de roi')).toBeInTheDocument();
  });

  test('offers the three ranks', async () => {
    libraryItemReturns(ITEM);
    renderDetail();

    expect(await screen.findByText('Or')).toBeInTheDocument();
    expect(screen.getByText('Argent')).toBeInTheDocument();
    expect(screen.getByText('Bronze')).toBeInTheDocument();
  });

  test('assigning a rank is reflected immediately', async () => {
    // The screen re-reads the item after the mutation instead of patching its own
    // state, so the handler has to record the new rank.
    const item = servesMutableItem();
    server.use(http.put('*/api/library/:id/rank', async ({ request }) => {
      const body = (await request.json()) as { categoryId?: string };
      const category = BUILTIN_CATEGORIES.find((c) => c.id === body.categoryId);
      item.set({ ...item.current(), rankCode: category?.code });
      return HttpResponse.json(item.current());
    }));
    renderDetail();

    await userEvent.click(await screen.findByText('Or'));

    // The button switches to the selected state: the accent border is applied.
    await waitFor(() =>
      expect(screen.getByText('Or').closest('button')).toHaveStyle({ borderColor: '#d9b94e' }));
  });

  test('marking as read toggles the label', async () => {
    const item = servesMutableItem();
    server.use(http.put('*/api/library/:id/progress', async ({ request }) => {
      const body = (await request.json()) as { status?: string };
      item.set({ ...item.current(), status: body.status });
      return new HttpResponse(null, { status: 204 });
    }));
    renderDetail();

    await userEvent.click(await screen.findByText('Marquer comme lu'));

    expect(await screen.findByText('✓ Lu')).toBeInTheDocument();
  });

  /** An unknown identifier answers 404, as does one belonging to another user. */
  test('signals a title that cannot be found', async () => {
    libraryItemReturns(ITEM);
    renderDetail('inconnu');

    expect(await screen.findByText('Titre introuvable.')).toBeInTheDocument();
  });

  /** The paginated collection is never downloaded to display one title. */
  test('fetches the single title rather than the collection', async () => {
    const urls: string[] = [];
    server.use(http.get('*/api/library/:id', ({ request }) => {
      urls.push(request.url);
      return HttpResponse.json(ITEM);
    }));
    renderDetail();

    await screen.findByRole('heading', { name: 'Le Nom du vent' });
    expect(urls.some((url) => url.endsWith('/api/library/item-1'))).toBe(true);
  });

  /**
   * `BookView` carries the series title but no identifier, so the way into the series
   * screen is resolved against the series the user has a stake in.
   */
  test('opens the series of the volume', async () => {
    libraryItemReturns(ITEM);
    seriesReturns([
      seriesSummary({ id: 'series-9', kind: 'BOOK', title: 'Chronique du tueur de roi' }),
    ]);
    render(
      <TestProviders route="/detail/item-1">
        <Routes>
          <Route path="/detail/:id" element={<DetailPage />} />
          <Route path="/series/:id" element={<p>écran de la série</p>} />
        </Routes>
      </TestProviders>,
    );

    await userEvent.click(await screen.findByText('Chronique du tueur de roi'));

    expect(await screen.findByText('écran de la série')).toBeInTheDocument();
  });

  test('leaves the series inert when the user has no series of that name', async () => {
    libraryItemReturns(ITEM);
    renderDetail();

    // No `/api/series` entry matches, so there is nothing to open.
    const series = await screen.findByText('Chronique du tueur de roi');
    expect(series.closest('button')).toBeNull();
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderDetail();

    expect(await screen.findByText(/Connecte-toi pour voir ce titre/)).toBeInTheDocument();
  });

  // ── Reading progress ───────────────────────────────────────────────────────

  /** Page 120 of a 300-page book is 40 %, and the user only has to type one of them. */
  test('derives the percentage from the page that is typed in', async () => {
    libraryItemReturns(READING);
    renderDetail();

    const page = await screen.findByLabelText('Page');
    await userEvent.clear(page);
    await userEvent.type(page, '120');

    expect(screen.getByLabelText('Pourcentage')).toHaveValue(40);
    expect(screen.getByText('40 % · page 120 sur 300')).toBeInTheDocument();
  });

  test('derives the page from the percentage that is typed in', async () => {
    libraryItemReturns(READING);
    renderDetail();

    const percent = await screen.findByLabelText('Pourcentage');
    await userEvent.clear(percent);
    await userEvent.type(percent, '50');

    expect(screen.getByLabelText('Page')).toHaveValue(150);
  });

  test('sends the position and the dates to the server', async () => {
    const bodies: Record<string, unknown>[] = [];
    libraryItemReturns(READING);
    server.use(http.put('*/api/library/:id/progress', async ({ request }) => {
      bodies.push((await request.json()) as Record<string, unknown>);
      return new HttpResponse(null, { status: 204 });
    }));
    renderDetail();

    const page = await screen.findByLabelText('Page');
    await userEvent.clear(page);
    await userEvent.type(page, '120');
    await userEvent.click(screen.getByText('Enregistrer ma progression'));

    await waitFor(() => expect(bodies[0]).toMatchObject({ currentPage: 120, percent: 40 }));
  });

  /** Nothing to show about a book nobody has opened: the buttons below start it. */
  test('offers no progress form on a title that was never opened', async () => {
    libraryItemReturns(ITEM);
    renderDetail();

    await screen.findByRole('heading', { name: 'Le Nom du vent' });
    expect(screen.queryByText('Ma progression')).not.toBeInTheDocument();
  });

  // ── Rating and private review ──────────────────────────────────────────────

  test('records a rating out of five', async () => {
    const item = servesMutableItem();
    server.use(http.put('*/api/library/:id/review', async ({ request }) => {
      const body = (await request.json()) as { rating?: number; review?: string };
      item.set({ ...item.current(), rating: body.rating, review: body.review });
      return HttpResponse.json(item.current());
    }));
    renderDetail();

    await userEvent.click(await screen.findByLabelText('Noter 4 sur 5'));

    // The fourth star now offers to take the rating back: it is the one in force.
    expect(await screen.findByLabelText('Retirer ma note')).toBeInTheDocument();
    expect(item.current().rating).toBe(4);
  });

  test('saves the review when the field is left', async () => {
    const item = servesMutableItem();
    server.use(http.put('*/api/library/:id/review', async ({ request }) => {
      const body = (await request.json()) as { rating?: number; review?: string };
      item.set({ ...item.current(), rating: body.rating, review: body.review });
      return HttpResponse.json(item.current());
    }));
    renderDetail();

    await userEvent.type(await screen.findByLabelText('Ma note'), 'Kvothe est insupportable.');
    await userEvent.tab();

    await waitFor(() => expect(item.current().review).toBe('Kvothe est insupportable.'));
  });

  /** The user is told, on the screen itself, that none of this is shared. */
  test('states that the rating and the review stay private', async () => {
    libraryItemReturns(ITEM);
    renderDetail();

    expect(await screen.findByText(/strictement privés/)).toBeInTheDocument();
  });

  // ── Alternate editions ─────────────────────────────────────────────────────

  /** The item as the API returns it once the work carries more than one edition. */
  const OWNED_EDITION = 'edition-pocket';
  const OTHER_EDITION = 'edition-relie';

  const MULTI_EDITION = libraryItem({
    id: 'item-1',
    book: {
      ...libraryItem().book,
      workId: 'work-1',
      editionId: OWNED_EDITION,
      publisher: 'Pocket',
      pageCount: 512,
    },
  });

  /** Serves `/api/works/{id}/editions`, the way the API scopes it to one work. */
  function editionsReturn(editions: Record<string, unknown>[]) {
    server.use(http.get('*/api/works/:id/editions', ({ params }) =>
      params.id === 'work-1' ? HttpResponse.json(editions) : new HttpResponse(null, { status: 404 })));
  }

  const TWO_EDITIONS = [
    { id: OWNED_EDITION, publisher: 'Pocket', language: 'fr', pageCount: 512, owned: true },
    {
      id: OTHER_EDITION,
      publisher: 'Robert Laffont',
      language: 'fr',
      format: 'Relié',
      pageCount: 640,
      isbn13: '9782221252000',
      releaseDate: '2019-10-03',
      owned: false,
    },
  ];

  /** Most works are known in a single edition: an "other editions" heading over nothing. */
  test('hides the editions section when the work is known in one edition', async () => {
    libraryItemReturns(MULTI_EDITION);
    editionsReturn([TWO_EDITIONS[0]]);
    renderDetail();

    await screen.findByRole('heading', { name: 'Le Nom du vent' });
    expect(screen.queryByText('Autres éditions')).not.toBeInTheDocument();
  });

  test('lists the other editions with what tells them apart', async () => {
    libraryItemReturns(MULTI_EDITION);
    editionsReturn(TWO_EDITIONS);
    renderDetail();

    expect(await screen.findByText('Autres éditions')).toBeInTheDocument();
    expect(screen.getByText('Robert Laffont · français · Relié')).toBeInTheDocument();
    expect(screen.getByText(/640 pages/)).toBeInTheDocument();
    expect(screen.getByText(/9782221252000/)).toBeInTheDocument();
    // The one already on the shelf is named as the reference, not offered again.
    expect(screen.getByText(/Pocket · français/)).toBeInTheDocument();
  });

  test('switches the collection row to the chosen edition', async () => {
    const bodies: Record<string, unknown>[] = [];
    libraryItemReturns(MULTI_EDITION);
    editionsReturn(TWO_EDITIONS);
    server.use(http.put('*/api/library/:id/edition', async ({ request }) => {
      bodies.push((await request.json()) as Record<string, unknown>);
      return HttpResponse.json(MULTI_EDITION);
    }));
    renderDetail();

    await userEvent.click(
      await screen.findByRole('button', { name: /Je possède l'édition Robert Laffont/ }));

    await waitFor(() => expect(bodies[0]).toEqual({ editionId: OTHER_EDITION }));
  });

  /** Owning the same edition twice is what `UNIQUE(user, edition)` forbids. */
  test('offers no switch onto an edition already in the collection', async () => {
    libraryItemReturns(MULTI_EDITION);
    editionsReturn([
      TWO_EDITIONS[0],
      { ...TWO_EDITIONS[1], owned: true },
    ]);
    renderDetail();

    expect(await screen.findByText('Déjà dans ta collection')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Je possède l'édition/ })).not.toBeInTheDocument();
  });

  /** A stale list can still send a switch the server refuses: the user is told why. */
  test('explains a switch refused because the edition is already owned', async () => {
    libraryItemReturns(MULTI_EDITION);
    editionsReturn(TWO_EDITIONS);
    server.use(http.put('*/api/library/:id/edition', () =>
      HttpResponse.json({ message: 'déjà' }, { status: 409 })));
    renderDetail();

    await userEvent.click(
      await screen.findByRole('button', { name: /Je possède l'édition Robert Laffont/ }));

    expect(await screen.findByText(/déjà cette édition dans ta collection/)).toBeInTheDocument();
  });

  /** What a change of edition does to the position is not obvious, so the screen says it. */
  test('states what a change of edition does to the reading progress', async () => {
    libraryItemReturns(MULTI_EDITION);
    editionsReturn(TWO_EDITIONS);
    renderDetail();

    expect(await screen.findByText(/reprise en pourcentage/)).toBeInTheDocument();
  });
});

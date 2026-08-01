import { render, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { TestProviders, mockViewportWidth, renderWithProviders } from '../../test/utils';
import { BUILTIN_CATEGORIES, customCategory, libraryItem, seriesSummary } from '../../test/fixtures';
import { http, HttpResponse, libraryReturns, seriesReturns, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { CollectionPage } = await import('./CollectionPage');

const ROMAN = libraryItem({ id: 'roman-1', book: { kind: 'BOOK', title: 'Le Nom du vent', authors: 'Patrick Rothfuss' } });
const MANGA = libraryItem({ id: 'manga-1', book: { kind: 'MANGA', title: 'Vinland Saga', authors: 'Makoto Yukimura' } });

/** Thirty books, i.e. more than the twenty-four of a page. */
const MANY = Array.from({ length: 30 }, (_, i) =>
  libraryItem({ id: `book-${i}`, book: { kind: 'BOOK', title: `Titre ${String(i).padStart(2, '0')}` } }));

/** A run with volumes still to buy, and one the user owns from end to end. */
const SAGA = seriesSummary({ id: 'series-1', title: 'Vinland Saga', totalVolumes: 27, ownedCount: 12 });
const COMPLETE = seriesSummary({ id: 'series-2', title: 'Berserk', totalVolumes: 41, ownedCount: 41 });

describe('CollectionPage', () => {
  beforeEach(resetAuth);

  test('renders the collection titles', async () => {
    libraryReturns([ROMAN]);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText('Le Nom du vent')).toBeInTheDocument();
  });

  test('shows only the selected kind and switches to the manga shelf', async () => {
    libraryReturns([ROMAN, MANGA]);
    renderWithProviders(<CollectionPage />);

    // Default "Bibliothèque" view: the manga is hidden.
    expect(await screen.findByText('Le Nom du vent')).toBeInTheDocument();
    expect(screen.queryByText('Vinland Saga')).not.toBeInTheDocument();

    await userEvent.click(screen.getByText('Mangathèque'));

    expect(await screen.findByText('Vinland Saga')).toBeInTheDocument();
    expect(screen.queryByText('Le Nom du vent')).not.toBeInTheDocument();
  });

  /** #183: the kind switch is driven by the taxonomy, not a hardcoded Books/Manga pair. */
  test('offers a shelf for every kind the user owns, comics and audiobooks included', async () => {
    libraryReturns([
      ROMAN,
      libraryItem({ id: 'comic-1', book: { kind: 'COMIC', title: 'Le Chat du rabbin' } }),
      libraryItem({ id: 'graphic-1', book: { kind: 'GRAPHIC_NOVEL', title: 'Watchmen' } }),
      libraryItem({ id: 'audio-1', book: { kind: 'AUDIOBOOK', title: 'Dune' } }),
    ]);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText('Bibliothèque')).toBeInTheDocument();
    expect(await screen.findByText('Bandes dessinées')).toBeInTheDocument();
    expect(await screen.findByText('Romans graphiques')).toBeInTheDocument();
    expect(await screen.findByText('Livres audio')).toBeInTheDocument();
    // Nothing owned of this kind: no shelf offered for it.
    expect(screen.queryByText('Mangathèque')).not.toBeInTheDocument();
  });

  /**
   * #183 acceptance criterion: the shelf that opens by default is the one the user owns
   * the most of, not a hardcoded 'BOOK' — here two comics against one book and one manga.
   */
  test('defaults to the kind the user owns the most of', async () => {
    libraryReturns([
      ROMAN,
      MANGA,
      libraryItem({ id: 'comic-1', book: { kind: 'COMIC', title: 'Le Chat du rabbin' } }),
      libraryItem({ id: 'comic-2', book: { kind: 'COMIC', title: 'Corto Maltese' } }),
    ]);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText('Le Chat du rabbin')).toBeInTheDocument();
    expect(screen.getByText('Corto Maltese')).toBeInTheDocument();
    expect(screen.queryByText('Le Nom du vent')).not.toBeInTheDocument();
    expect(screen.queryByText('Vinland Saga')).not.toBeInTheDocument();
  });

  /** A tie falls back to the first kind of the taxonomy — 'BOOK', same as an empty library. */
  test('falls back to the first kind of the taxonomy on a tie', async () => {
    libraryReturns([ROMAN, MANGA]);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText('Le Nom du vent')).toBeInTheDocument();
    expect(screen.queryByText('Vinland Saga')).not.toBeInTheDocument();
  });

  /** Every kind gets its own cover tag, no generic fallback for the three newer ones. */
  test('tags each cover with the label of its own kind', async () => {
    libraryReturns([
      libraryItem({ id: 'comic-1', book: { kind: 'COMIC', title: 'Le Chat du rabbin', volumeNumber: undefined } }),
    ]);
    renderWithProviders(<CollectionPage />);

    await userEvent.click(await screen.findByText('Bandes dessinées'));

    expect(await screen.findByText('BD')).toBeInTheDocument();
  });

  test('counts the displayed titles', async () => {
    libraryReturns([ROMAN, libraryItem({ id: 'roman-2', book: { kind: 'BOOK', title: 'La Peur du sage' } })]);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText('2 titres')).toBeInTheDocument();
  });

  test('filters by rank', async () => {
    libraryReturns([
      libraryItem({ id: 'or-1', rankCode: 'or', book: { kind: 'BOOK', title: 'Titre doré' } }),
      libraryItem({ id: 'sans', book: { kind: 'BOOK', title: 'Titre sans rang' } }),
    ]);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText('Titre sans rang')).toBeInTheDocument();

    // The chips are the categories the API returns, so the row appears with them.
    await userEvent.click(await screen.findByText('Or'));

    expect(await screen.findByText('Titre doré')).toBeInTheDocument();
    expect(screen.queryByText('Titre sans rang')).not.toBeInTheDocument();
  });

  /**
   * The acceptance criterion of #51: a category created on the management screen becomes a
   * shelf here, without this screen naming it.
   */
  test('turns a custom category into a filter chip', async () => {
    server.use(http.get('*/api/categories', () =>
      HttpResponse.json([...BUILTIN_CATEGORIES, customCategory()])));
    libraryReturns([
      libraryItem({ id: 'dore-1', rankCode: 'dore', book: { kind: 'BOOK', title: 'Titre classé' } }),
      libraryItem({ id: 'sans', book: { kind: 'BOOK', title: 'Titre sans rang' } }),
    ]);
    renderWithProviders(<CollectionPage />);

    await userEvent.click(await screen.findByText('Doré'));

    expect(await screen.findByText('Titre classé')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Titre sans rang')).not.toBeInTheDocument());
  });

  test('sends the code of the selected category to the server', async () => {
    const ranks: (string | null)[] = [];
    server.use(
      http.get('*/api/categories', () =>
        HttpResponse.json([...BUILTIN_CATEGORIES, customCategory()])),
      http.get('*/api/library', ({ request }) => {
        ranks.push(new URL(request.url).searchParams.get('rank'));
        return HttpResponse.json({ items: [], page: 0, size: 24, total: 0 });
      }),
    );
    renderWithProviders(<CollectionPage />);

    await userEvent.click(await screen.findByText('Doré'));

    await waitFor(() => expect(ranks).toContain('dore'));
  });

  test('opens the category management screen from the shelf row', async () => {
    libraryReturns([ROMAN]);
    render(
      <TestProviders route="/collection">
        <Routes>
          <Route path="/collection" element={<CollectionPage />} />
          <Route path="/categories" element={<p>écran des catégories</p>} />
        </Routes>
      </TestProviders>,
    );

    await userEvent.click(await screen.findByText('Gérer mes catégories'));

    expect(await screen.findByText('écran des catégories')).toBeInTheDocument();
  });

  /** "My favourites" is the rating filter at four, applied by the server like the rest. */
  test('narrows the shelf down to the favourites', async () => {
    libraryReturns([
      libraryItem({ id: 'adore', rating: 5, book: { kind: 'BOOK', title: 'Titre adoré' } }),
      libraryItem({ id: 'ordinaire', rating: 2, book: { kind: 'BOOK', title: 'Titre ordinaire' } }),
    ]);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText('Titre ordinaire')).toBeInTheDocument();

    await userEvent.click(screen.getByText('Mes coups de cœur'));

    expect(await screen.findByText('Titre adoré')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Titre ordinaire')).not.toBeInTheDocument());
  });

  test('removing a title drops it from the list', async () => {
    // The list is re-read from the server after the mutation, so the handler has to
    // actually apply the deletion — the screen no longer patches its own state.
    let items = [ROMAN];
    server.use(
      http.get('*/api/library', () =>
        HttpResponse.json({ items, page: 0, size: 24, total: items.length })),
      http.delete('*/api/library/:id', ({ params }) => {
        items = items.filter((it) => it.id !== params.id);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<CollectionPage />);

    await screen.findByText('Le Nom du vent');
    await userEvent.click(screen.getByLabelText('Retirer'));

    await waitFor(() => expect(screen.queryByText('Le Nom du vent')).not.toBeInTheDocument());
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText(/Connecte-toi pour voir ta collection/)).toBeInTheDocument();
  });

  // ── Server-side pagination, sorting and search ─────────────────────────────

  test('asks the server for a single page instead of the whole collection', async () => {
    const urls: string[] = [];
    server.use(http.get('*/api/library', ({ request }) => {
      urls.push(request.url);
      const params = new URL(request.url).searchParams;
      const size = Number(params.get('size'));
      const page = Number(params.get('page'));
      return HttpResponse.json({
        items: MANY.slice(page * size, page * size + size),
        page,
        size,
        total: MANY.length,
      });
    }));

    renderWithProviders(<CollectionPage />);
    await screen.findByText('Titre 00');

    expect(urls.some((url) => url.includes('size=24') && url.includes('page=0'))).toBe(true);
    // Only the page has been rendered, not the thirty titles.
    expect(screen.queryByText('Titre 29')).not.toBeInTheDocument();
  });

  test('announces the server total rather than the number of loaded titles', async () => {
    libraryReturns(MANY);
    renderWithProviders(<CollectionPage />);

    expect(await screen.findByText('30 titres')).toBeInTheDocument();
    expect(screen.queryByText('Titre 29')).not.toBeInTheDocument();
  });

  test('the load-more button appends the next page', async () => {
    libraryReturns(MANY);
    renderWithProviders(<CollectionPage />);

    await screen.findByText('Titre 00');
    await userEvent.click(await screen.findByText('Voir plus (24 / 30)'));

    expect(await screen.findByText('Titre 29')).toBeInTheDocument();
    // The first page is still there: the pages accumulate.
    expect(screen.getByText('Titre 00')).toBeInTheDocument();
    // Everything is loaded, the button is gone.
    await waitFor(() => expect(screen.queryByText(/Voir plus/)).not.toBeInTheDocument());
  });

  test('delegates the sorting to the server', async () => {
    const sorts: (string | null)[] = [];
    server.use(http.get('*/api/library', ({ request }) => {
      sorts.push(new URL(request.url).searchParams.get('sort'));
      return HttpResponse.json({ items: [ROMAN], page: 0, size: 24, total: 1 });
    }));

    renderWithProviders(<CollectionPage />);
    await screen.findByText('Le Nom du vent');
    await userEvent.click(screen.getByText('Titre'));

    await waitFor(() => expect(sorts).toContain('title'));
  });

  test('forwards the search term to the server', async () => {
    libraryReturns([ROMAN, libraryItem({ id: 'autre', book: { kind: 'BOOK', title: 'Le Trône de fer' } })]);
    renderWithProviders(<CollectionPage />);

    await screen.findByText('Le Nom du vent');
    await userEvent.type(screen.getByLabelText('Rechercher dans ma collection…'), 'trône');

    expect(await screen.findByText('Le Trône de fer')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Le Nom du vent')).not.toBeInTheDocument());
  });

  // ── Series view ────────────────────────────────────────────────────────────

  /**
   * Switches to the Series tab, the manga shelf being where the runs live. The kind switch
   * only offers a shelf the user owns something of, so every caller needs at least one
   * manga item in the library — {@link MANGA} unless the test seeds its own.
   */
  async function openSeriesView() {
    await userEvent.click(await screen.findByText('Mangathèque'));
    await userEvent.click(screen.getByText('Séries'));
  }

  test('lists the series of the collection with their progress', async () => {
    libraryReturns([MANGA]);
    seriesReturns([SAGA, COMPLETE]);
    renderWithProviders(<CollectionPage />);
    await openSeriesView();

    expect(await screen.findByText('Vinland Saga')).toBeInTheDocument();
    expect(screen.getByText('12 / 27 tomes')).toBeInTheDocument();
    expect(screen.getByText('2 séries')).toBeInTheDocument();
  });

  /** The acceptance criterion of #46: an incomplete run stands out from a finished one. */
  test('flags the incomplete series only', async () => {
    libraryReturns([MANGA]);
    seriesReturns([SAGA, COMPLETE]);
    renderWithProviders(<CollectionPage />);
    await openSeriesView();

    await screen.findByText('Vinland Saga');
    const badges = screen.getAllByText('Incomplète');
    expect(badges).toHaveLength(1);
    expect(badges[0].closest('button')).toHaveTextContent('Vinland Saga');
  });

  test('orders by progress, then by title', async () => {
    libraryReturns([MANGA]);
    seriesReturns([SAGA, COMPLETE]);
    renderWithProviders(<CollectionPage />);
    await openSeriesView();

    // Progress is the default: the run that still has volumes to buy comes first.
    await screen.findByText('Vinland Saga');
    const byProgress = screen.getAllByRole('button', { name: /tomes/ });
    expect(byProgress[0]).toHaveTextContent('Vinland Saga');

    await userEvent.click(screen.getByText('Titre'));

    const byTitle = screen.getAllByRole('button', { name: /tomes/ });
    expect(byTitle[0]).toHaveTextContent('Berserk');
  });

  test('keeps the kind filter and the search across the toggle', async () => {
    libraryReturns([ROMAN, MANGA]);
    seriesReturns([SAGA, seriesSummary({ id: 'series-3', kind: 'BOOK', title: 'Le Trône de fer' })]);
    renderWithProviders(<CollectionPage />);

    await openSeriesView();
    // The Manga toggle still applies: the book series is out.
    expect(await screen.findByText('Vinland Saga')).toBeInTheDocument();
    expect(screen.queryByText('Le Trône de fer')).not.toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Rechercher dans ma collection…'), 'berserk');
    await waitFor(() => expect(screen.queryByText('Vinland Saga')).not.toBeInTheDocument());

    // Back to the list: the kind and the search are still the ones that were set.
    await userEvent.click(screen.getByText('Liste'));
    expect(screen.getByLabelText('Rechercher dans ma collection…')).toHaveValue('berserk');
    await userEvent.click(screen.getByText('Séries'));
    expect(screen.queryByText('Le Trône de fer')).not.toBeInTheDocument();
  });

  test('opens the series screen when a row is clicked', async () => {
    libraryReturns([MANGA]);
    seriesReturns([SAGA]);
    render(
      <TestProviders route="/collection">
        <Routes>
          <Route path="/collection" element={<CollectionPage />} />
          <Route path="/series/:id" element={<p>écran de la série</p>} />
        </Routes>
      </TestProviders>,
    );
    await openSeriesView();

    await userEvent.click(await screen.findByText('Vinland Saga'));

    expect(await screen.findByText('écran de la série')).toBeInTheDocument();
  });

  test('invites the user to fill an empty series shelf', async () => {
    libraryReturns([MANGA]);
    seriesReturns([]);
    renderWithProviders(<CollectionPage />);
    await openSeriesView();

    expect(await screen.findByText('Aucune série ici.')).toBeInTheDocument();
  });

  test('offers a retry when the series cannot be loaded', async () => {
    libraryReturns([MANGA]);
    let attempts = 0;
    server.use(http.get('*/api/series', () => {
      attempts += 1;
      return attempts === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([SAGA]);
    }));
    renderWithProviders(<CollectionPage />);
    await openSeriesView();

    await userEvent.click(await screen.findByText('Réessayer'));

    expect(await screen.findByText('Vinland Saga')).toBeInTheDocument();
  });

  // ── Desktop layout (#173) ────────────────────────────────────────────────────

  describe('the chip and sort rows', () => {
    afterEach(() => {
      Reflect.deleteProperty(window, 'matchMedia');
    });

    test('below the tablet breakpoint they stay the horizontal scrollers they ship today', async () => {
      mockViewportWidth(390);
      libraryReturns([ROMAN]);
      renderWithProviders(<CollectionPage />);

      const chipRow = (await screen.findByText('Or')).closest('[class*="chipRow"]');
      const sortRow = screen.getByText('Trier').closest('[class*="sortRow"]');
      expect(chipRow?.className).toMatch(/\bscroll-x\b/);
      expect(chipRow?.className).not.toMatch(/rowWide/);
      expect(sortRow?.className).toMatch(/\bscroll-x\b/);
      expect(sortRow?.className).not.toMatch(/rowWide/);
    });

    test('at the tablet breakpoint they wrap instead of scrolling', async () => {
      mockViewportWidth(600);
      libraryReturns([ROMAN]);
      renderWithProviders(<CollectionPage />);

      const chipRow = (await screen.findByText('Or')).closest('[class*="chipRow"]');
      const sortRow = screen.getByText('Trier').closest('[class*="sortRow"]');
      expect(chipRow?.className).toMatch(/rowWide/);
      expect(chipRow?.className).not.toMatch(/\bscroll-x\b/);
      expect(sortRow?.className).toMatch(/rowWide/);
      expect(sortRow?.className).not.toMatch(/\bscroll-x\b/);
    });
  });

  test('the flat view opts into the shared cover grid', async () => {
    libraryReturns([ROMAN]);
    renderWithProviders(<CollectionPage />);

    const cover = await screen.findByText('Le Nom du vent');
    expect(cover.closest('[class*="gridCover"]')).toBeInTheDocument();
  });
});

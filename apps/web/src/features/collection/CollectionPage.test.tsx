import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { libraryItem } from '../../test/fixtures';
import { http, HttpResponse, libraryReturns, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { CollectionPage } = await import('./CollectionPage');

const ROMAN = libraryItem({ id: 'roman-1', book: { kind: 'BOOK', title: 'Le Nom du vent', authors: 'Patrick Rothfuss' } });
const MANGA = libraryItem({ id: 'manga-1', book: { kind: 'MANGA', title: 'Vinland Saga', authors: 'Makoto Yukimura' } });

/** Thirty books, i.e. more than the twenty-four of a page. */
const MANY = Array.from({ length: 30 }, (_, i) =>
  libraryItem({ id: `book-${i}`, book: { kind: 'BOOK', title: `Titre ${String(i).padStart(2, '0')}` } }));

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

    await userEvent.click(screen.getByText('Or'));

    expect(await screen.findByText('Titre doré')).toBeInTheDocument();
    expect(screen.queryByText('Titre sans rang')).not.toBeInTheDocument();
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
});

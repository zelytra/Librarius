import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { catalogResult } from '../../test/fixtures';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { DiscoverPage } = await import('./DiscoverPage');

function searchReturns(results: unknown[], status = 200) {
  server.use(
    http.get('*/api/catalog/search', () =>
      status === 200 ? HttpResponse.json(results) : new HttpResponse(null, { status })),
  );
}

/** Records the query string of every catalog search the screen sends. */
function capturedSearches(results: unknown[] = []): URLSearchParams[] {
  const calls: URLSearchParams[] = [];
  server.use(
    http.get('*/api/catalog/search', ({ request }) => {
      calls.push(new URL(request.url).searchParams);
      return HttpResponse.json(results);
    }),
  );
  return calls;
}

/** Records the bodies posted to the collection. */
function capturedLibraryPosts(): Record<string, unknown>[] {
  const posts: Record<string, unknown>[] = [];
  server.use(
    http.post('*/api/library', async ({ request }) => {
      posts.push((await request.json()) as Record<string, unknown>);
      return HttpResponse.json({ id: 'nouveau' }, { status: 201 });
    }),
  );
  return posts;
}

async function search(term = 'fourth wing') {
  await userEvent.type(screen.getByPlaceholderText(/Rechercher un titre/), term);
  await userEvent.click(screen.getByLabelText('Rechercher'));
}

async function openAdvanced() {
  await userEvent.click(screen.getByText('Recherche avancée'));
}

async function openManualForm() {
  await userEvent.click(await screen.findByText('Ajouter manuellement'));
}

describe('DiscoverPage', () => {
  beforeEach(resetAuth);

  test('invites the user to start a search on first render', async () => {
    renderWithProviders(<DiscoverPage />);

    expect(await screen.findByText(/Lancez une recherche/)).toBeInTheDocument();
  });

  test('renders the catalog results', async () => {
    searchReturns([catalogResult()]);
    renderWithProviders(<DiscoverPage />);

    await search();

    // The title is rendered twice: in the card, and on the fallback cover.
    expect((await screen.findAllByText('Fourth Wing')).length).toBeGreaterThan(0);
    expect(screen.getByText(/Rebecca Yarros · 2023/)).toBeInTheDocument();
  });

  /**
   * A mixed feed no longer says what a result is through a screen-wide toggle (#194): each
   * one has to name its own medium instead.
   */
  test('labels each result with its own medium', async () => {
    searchReturns([
      catalogResult({ kind: 'BOOK', title: 'Fourth Wing' }),
      catalogResult({
        kind: 'MANGA',
        title: 'One Piece',
        authors: 'Eiichiro Oda',
        year: 1997,
        provider: 'anilist',
        providerRef: 'AL1',
      }),
    ]);
    renderWithProviders(<DiscoverPage />);

    await search();

    // Each title is rendered twice: in the card, and on the fallback cover.
    expect((await screen.findAllByText('Fourth Wing')).length).toBeGreaterThan(0);
    expect((await screen.findAllByText('One Piece')).length).toBeGreaterThan(0);
    expect(screen.getByText('Livre')).toBeInTheDocument();
    expect(screen.getByText('Manga')).toBeInTheDocument();
  });

  // ── Desktop layout (#174) ────────────────────────────────────────────────────

  test('opts the search field into the reading-measure column and the results into the panel grid', async () => {
    searchReturns([catalogResult()]);
    renderWithProviders(<DiscoverPage />);

    const searchInput = screen.getByPlaceholderText(/Rechercher un titre/);
    expect(searchInput.closest('[class*="searchColumn"]')).toBeInTheDocument();

    await search();

    const resultTitle = (await screen.findAllByText('Fourth Wing'))[0];
    expect(resultTitle.closest('[class*="results"]')).toBeInTheDocument();
  });

  test('adds a result to the collection', async () => {
    searchReturns([catalogResult()]);
    renderWithProviders(<DiscoverPage />);

    await search();
    await userEvent.click(await screen.findByText('Collection'));

    expect(await screen.findByText('✓ Ajouté à la collection')).toBeInTheDocument();
  });

  /**
   * The card knows which record it is showing, and it is the only place that does: dropped
   * here, no provider can ever be asked about the title again (#184).
   */
  test('carries the provider reference of the result it adds', async () => {
    const posts = capturedLibraryPosts();
    searchReturns([catalogResult()]);
    renderWithProviders(<DiscoverPage />);

    await search();
    await userEvent.click(await screen.findByText('Collection'));

    expect(await screen.findByText('✓ Ajouté à la collection')).toBeInTheDocument();
    expect(posts[0].book).toMatchObject({ provider: 'openlibrary', providerRef: 'OL123W' });
  });

  /**
   * The series, tome number and page count the enriched catalog result now carries have to
   * reach the collection, so a title added from a search lands in its series and knows its
   * length instead of showing "—".
   */
  test('carries the series, volume and page count of the result it adds', async () => {
    const posts = capturedLibraryPosts();
    searchReturns([catalogResult({ seriesTitle: 'Vinland Saga', volumeNumber: 3, pageCount: 456 })]);
    renderWithProviders(<DiscoverPage />);

    await search();
    await userEvent.click(await screen.findByText('Collection'));

    expect(await screen.findByText('✓ Ajouté à la collection')).toBeInTheDocument();
    expect(posts[0].book).toMatchObject({
      seriesTitle: 'Vinland Saga',
      volumeNumber: 3,
      pageCount: 456,
    });
  });

  test('adds a result to the wishlist', async () => {
    searchReturns([catalogResult()]);
    renderWithProviders(<DiscoverPage />);

    await search();
    await userEvent.click(await screen.findByText('Souhaits'));

    expect(await screen.findByText('✓ Ajouté aux souhaits')).toBeInTheDocument();
  });

  test('signals a failed search', async () => {
    searchReturns([], 500);
    renderWithProviders(<DiscoverPage />);

    await search();

    expect(await screen.findByText(/Erreur 500/)).toBeInTheDocument();
  });

  test('tells the user to wait when the catalog quota is exhausted', async () => {
    searchReturns([], 429);
    renderWithProviders(<DiscoverPage />);

    await search();

    // "Error 429" would be useless: what matters is that waiting fixes it.
    expect(await screen.findByText(/Réessaie dans une minute/)).toBeInTheDocument();
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderWithProviders(<DiscoverPage />);

    expect(await screen.findByText(/Connecte-toi pour rechercher/)).toBeInTheDocument();
  });

  describe('ISBN detection', () => {
    test('searches a pasted ISBN13 on the ISBN field rather than as keywords', async () => {
      const calls = capturedSearches();
      renderWithProviders(<DiscoverPage />);

      await search('978-0-441-01359-3');

      expect(await screen.findByText(/ISBN reconnu/)).toBeInTheDocument();
      expect(calls).toHaveLength(1);
      expect(calls[0].get('isbn')).toBe('9780441013593');
      expect(calls[0].get('q')).toBeNull();
    });

    test('leaves an ordinary search on the keyword field', async () => {
      const calls = capturedSearches();
      renderWithProviders(<DiscoverPage />);

      await search('fourth wing');

      expect(screen.queryByText(/ISBN reconnu/)).not.toBeInTheDocument();
      expect(calls[0].get('q')).toBe('fourth wing');
      expect(calls[0].get('isbn')).toBeNull();
    });
  });

  describe('advanced search', () => {
    test('carries every advanced criterion into the query', async () => {
      const calls = capturedSearches();
      renderWithProviders(<DiscoverPage />);

      await openAdvanced();
      await userEvent.type(screen.getByLabelText('Auteur'), 'Frank Herbert');
      await userEvent.type(screen.getByLabelText('Année'), '1965');
      await userEvent.type(screen.getByLabelText('Éditeur'), 'Pocket');
      await userEvent.selectOptions(screen.getByLabelText('Langue'), 'fr');
      await search('dune');

      expect(calls).toHaveLength(1);
      expect(calls[0].get('q')).toBe('dune');
      expect(calls[0].get('author')).toBe('Frank Herbert');
      expect(calls[0].get('year')).toBe('1965');
      expect(calls[0].get('publisher')).toBe('Pocket');
      expect(calls[0].get('language')).toBe('fr');
    });

    test('searches on an advanced criterion alone, with no keyword', async () => {
      const calls = capturedSearches();
      renderWithProviders(<DiscoverPage />);

      await openAdvanced();
      await userEvent.type(screen.getByLabelText('Auteur'), 'Rebecca Yarros');
      await userEvent.click(screen.getByLabelText('Rechercher'));

      expect(calls).toHaveLength(1);
      expect(calls[0].get('author')).toBe('Rebecca Yarros');
      expect(calls[0].get('q')).toBeNull();
    });

    test('never searches on a keystroke, only on submit', async () => {
      // Each miss costs a call to a rate-limited third-party provider.
      const calls = capturedSearches();
      renderWithProviders(<DiscoverPage />);

      await openAdvanced();
      await userEvent.type(screen.getByPlaceholderText(/Rechercher un titre/), 'dune');
      await userEvent.type(screen.getByLabelText('Auteur'), 'Herbert');

      expect(calls).toHaveLength(0);
    });

    test('sends nothing when every field is empty', async () => {
      const calls = capturedSearches();
      renderWithProviders(<DiscoverPage />);

      await userEvent.click(screen.getByLabelText('Rechercher'));

      expect(calls).toHaveLength(0);
    });
  });

  describe('medium filter (#194)', () => {
    test('reaches every registered provider when no medium is chosen', async () => {
      const calls = capturedSearches();
      renderWithProviders(<DiscoverPage />);

      await search('dune');

      expect(calls[0].getAll('kind')).toEqual([]);
    });

    test('narrows the search to the medium selected', async () => {
      const calls = capturedSearches();
      renderWithProviders(<DiscoverPage />);

      await openAdvanced();
      await userEvent.click(screen.getByText('Manga'));
      await search('dune');

      expect(calls[0].getAll('kind')).toEqual(['MANGA']);
    });

    test('sends every medium selected, repeatably', async () => {
      const calls = capturedSearches();
      renderWithProviders(<DiscoverPage />);

      await openAdvanced();
      await userEvent.click(screen.getByText('Livre'));
      await userEvent.click(screen.getByText('Manga'));
      await search('dune');

      expect(calls[0].getAll('kind')).toEqual(['BOOK', 'MANGA']);
    });

    test('clicking a selected medium again removes it from the query', async () => {
      const calls = capturedSearches();
      renderWithProviders(<DiscoverPage />);

      await openAdvanced();
      await userEvent.click(screen.getByText('Manga'));
      await userEvent.click(screen.getByText('Manga'));
      await search('dune');

      expect(calls[0].getAll('kind')).toEqual([]);
    });

    test('reset clears the medium filter along with the rest of the panel', async () => {
      const calls = capturedSearches();
      renderWithProviders(<DiscoverPage />);

      await openAdvanced();
      await userEvent.click(screen.getByText('Manga'));
      await userEvent.click(screen.getByText('Réinitialiser'));
      await search('dune');

      expect(calls[0].getAll('kind')).toEqual([]);
    });
  });

  describe('manual add', () => {
    test('offers the manual entry when a search comes back empty', async () => {
      searchReturns([]);
      renderWithProviders(<DiscoverPage />);

      await search('un fanzine introuvable');

      expect(await screen.findByText(/Aucun titre ne correspond/)).toBeInTheDocument();
      expect(screen.getByText('Ajouter manuellement')).toBeInTheDocument();
    });

    test('posts the typed book to the collection', async () => {
      const posts = capturedLibraryPosts();
      renderWithProviders(<DiscoverPage />);

      await openManualForm();
      await userEvent.type(screen.getByLabelText('Titre'), 'Le Fanzine du dimanche');
      await userEvent.type(screen.getByLabelText('Auteur(s)'), 'Collectif');
      await userEvent.type(screen.getByLabelText('Série'), 'Dimanche');
      await userEvent.type(screen.getByLabelText('Tome'), '3');
      await userEvent.type(screen.getByLabelText('ISBN'), '9780441013593');
      await userEvent.type(screen.getByLabelText('Éditeur'), 'Autoédition');
      await userEvent.type(screen.getByLabelText('Pages'), '48');
      await userEvent.type(screen.getByLabelText('URL de la couverture'), 'https://cover.test/1.jpg');
      await userEvent.click(screen.getByText('Ajouter à la collection'));

      expect(await screen.findByText(/« Le Fanzine du dimanche » ajouté/)).toBeInTheDocument();
      expect(posts).toHaveLength(1);
      expect(posts[0]).toEqual({
        book: {
          kind: 'BOOK',
          title: 'Le Fanzine du dimanche',
          authors: 'Collectif',
          seriesTitle: 'Dimanche',
          volumeNumber: 3,
          isbn13: '9780441013593',
          publisher: 'Autoédition',
          pageCount: 48,
          coverUrl: 'https://cover.test/1.jpg',
        },
        status: 'OWNED',
      });
    });

    test('defaults to book, and records the medium chosen instead', async () => {
      const posts = capturedLibraryPosts();
      renderWithProviders(<DiscoverPage />);

      await openManualForm();
      expect(screen.getByLabelText('Support')).toHaveValue('BOOK');

      await userEvent.selectOptions(screen.getByLabelText('Support'), 'MANGA');
      await userEvent.type(screen.getByLabelText('Titre'), 'Un doujinshi');
      await userEvent.click(screen.getByText('Ajouter à la collection'));

      expect(await screen.findByText(/« Un doujinshi » ajouté/)).toBeInTheDocument();
      expect((posts[0].book as { kind: string }).kind).toBe('MANGA');
    });

    /** The submit button used to say "…" while it waited; it now keeps its label. */
    test('shows the compact indicator while the manual add is in flight', async () => {
      server.use(http.post('*/api/library', () => new Promise<Response>(() => {})));
      renderWithProviders(<DiscoverPage />);

      await openManualForm();
      await userEvent.type(screen.getByLabelText('Titre'), 'Un titre');
      await userEvent.click(screen.getByText('Ajouter à la collection'));

      expect(await screen.findByRole('status', undefined, { timeout: 3000 })).toBeInTheDocument();
      // The label stays put — the indicator is added to the button, not swapped in for it.
      expect(screen.getByRole('button', { name: /Ajouter à la collection/ })).toBeDisabled();
    });

    test('reports a rejected manual add', async () => {
      server.use(http.post('*/api/library', () => new HttpResponse(null, { status: 500 })));
      renderWithProviders(<DiscoverPage />);

      await openManualForm();
      await userEvent.type(screen.getByLabelText('Titre'), 'Un titre');
      await userEvent.click(screen.getByText('Ajouter à la collection'));

      expect(await screen.findByText(/Ajout impossible/)).toBeInTheDocument();
    });
  });
});

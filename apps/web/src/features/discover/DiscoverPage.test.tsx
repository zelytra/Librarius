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

  test('adds a result to the collection', async () => {
    searchReturns([catalogResult()]);
    renderWithProviders(<DiscoverPage />);

    await search();
    await userEvent.click(await screen.findByText('Collection'));

    expect(await screen.findByText('✓ Ajouté à la collection')).toBeInTheDocument();
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

    test('records the kind the screen is set to', async () => {
      const posts = capturedLibraryPosts();
      renderWithProviders(<DiscoverPage />);

      await userEvent.click(screen.getByText('Mangathèque'));
      await openManualForm();
      await userEvent.type(screen.getByLabelText('Titre'), 'Un doujinshi');
      await userEvent.click(screen.getByText('Ajouter à la collection'));

      expect(await screen.findByText(/« Un doujinshi » ajouté/)).toBeInTheDocument();
      expect((posts[0].book as { kind: string }).kind).toBe('MANGA');
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

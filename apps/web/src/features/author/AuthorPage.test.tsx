import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { render } from '@testing-library/react';
import { Route, Routes, useLocation } from 'react-router';
import { renderWithProviders, TestProviders } from '../../test/utils';
import { authorDetail, authorWork } from '../../test/fixtures';
import { authorDetailReturns, http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { AuthorPage } = await import('./AuthorPage');

const AUTHOR = authorDetail();

function renderAuthor(id = 'author-1') {
  return renderWithProviders(<AuthorPage />, { route: `/authors/${id}`, path: '/authors/:id' });
}

/**
 * Serves `/api/authors/{id}` from a single mutable payload, so a test can check that the
 * screen re-reads the author after a mutation instead of patching its own state.
 */
function servesMutableAuthor() {
  let detail = AUTHOR;
  server.use(http.get('*/api/authors/:id', ({ params }) =>
    params.id === detail.id ? HttpResponse.json(detail) : new HttpResponse(null, { status: 404 })));
  return {
    current: () => detail,
    set: (next: typeof AUTHOR) => {
      detail = next;
    },
  };
}

describe('AuthorPage', () => {
  beforeEach(resetAuth);

  test('renders the author, their photo fallback and their bibliography', async () => {
    authorDetailReturns(AUTHOR);
    renderAuthor();

    expect(await screen.findByRole('heading', { name: 'Patrick Rothfuss' })).toBeInTheDocument();
    // No photoUrl on the fixture: the fallback is the initials.
    expect(screen.getByText('PR')).toBeInTheDocument();
    expect(screen.getByText('1 œuvre')).toBeInTheDocument();
    expect(screen.getByText('Le Nom du vent')).toBeInTheDocument();
    expect(screen.getByText('Chronique du tueur de roi — 1')).toBeInTheDocument();
  });

  test('states an author with nothing in the local catalog yet', async () => {
    authorDetailReturns(authorDetail({ works: [] }));
    renderAuthor();

    expect(await screen.findByRole('heading', { name: 'Patrick Rothfuss' })).toBeInTheDocument();
    expect(screen.getByText('0 œuvre')).toBeInTheDocument();
    expect(screen.getByText('Aucune œuvre connue pour cet auteur.')).toBeInTheDocument();
  });

  test('following the author flips the button and survives a reload', async () => {
    const author = servesMutableAuthor();
    server.use(http.put('*/api/authors/:id/follow', () => {
      author.set(authorDetail({ followed: true }));
      return new HttpResponse(null, { status: 204 });
    }));
    renderAuthor();

    await userEvent.click(await screen.findByText('Suivre cet auteur'));

    // The state comes from re-reading the author, not from a local toggle.
    expect(await screen.findByText('✓ Auteur suivi')).toBeInTheDocument();
  });

  test('unfollowing the author flips the button back', async () => {
    const author = servesMutableAuthor();
    author.set(authorDetail({ followed: true }));
    server.use(http.delete('*/api/authors/:id/follow', () => {
      author.set(authorDetail({ followed: false }));
      return new HttpResponse(null, { status: 204 });
    }));
    renderAuthor();

    await userEvent.click(await screen.findByText('✓ Auteur suivi'));

    expect(await screen.findByText('Suivre cet auteur')).toBeInTheDocument();
  });

  /** A known author is never 404, whatever the caller owns — unlike a series. */
  test('signals an author that cannot be found', async () => {
    authorDetailReturns(AUTHOR);
    renderAuthor('inconnu');

    expect(await screen.findByText('Auteur introuvable.')).toBeInTheDocument();
  });

  test('offers a retry when the author cannot be loaded', async () => {
    let attempts = 0;
    server.use(http.get('*/api/authors/:id', () => {
      attempts += 1;
      return attempts === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(AUTHOR);
    }));
    renderAuthor();

    await userEvent.click(await screen.findByText('Réessayer'));

    expect(await screen.findByRole('heading', { name: 'Patrick Rothfuss' })).toBeInTheDocument();
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderAuthor();

    expect(await screen.findByText(/Connecte-toi pour voir cet auteur/)).toBeInTheDocument();
  });

  test('opens the catalog page of a work nobody here owns', async () => {
    const unowned = authorWork({
      workId: undefined,
      libraryItemId: undefined,
      seriesId: undefined,
      seriesTitle: undefined,
      volumeNumber: undefined,
      title: 'Les Portes de pierre',
      originalYear: 2027,
    });
    authorDetailReturns(authorDetail({ works: [unowned] }));
    function CatalogProbe() {
      const location = useLocation();
      const state = location.state as { result?: { title?: string } } | null;
      return <p>{`catalogue: ${state?.result?.title ?? '—'}`}</p>;
    }
    render(
      <TestProviders route="/authors/author-1">
        <Routes>
          <Route path="/authors/:id" element={<AuthorPage />} />
          <Route path="/catalog/:provider/:ref" element={<CatalogProbe />} />
        </Routes>
      </TestProviders>,
    );

    // The year, not the kind the badge already shows, sits under the title.
    expect(await screen.findByText('2027')).toBeInTheDocument();
    await userEvent.click(screen.getByText('Les Portes de pierre'));

    expect(await screen.findByText('catalogue: Les Portes de pierre')).toBeInTheDocument();
  });

  test('draws a series as one tile, with the volumes the reader owns of it', async () => {
    const volume = (n: number, owned: boolean) =>
      authorWork({
        workId: `w-${n}`,
        title: `One Piece Tome ${n}`,
        seriesTitle: 'One Piece',
        volumeNumber: n,
        seriesId: 'series-op',
        libraryItemId: owned ? `item-${n}` : undefined,
        kind: 'MANGA',
      });
    authorDetailReturns(
      authorDetail({
        name: 'Eiichiro Oda',
        works: [
          volume(1, true),
          volume(2, true),
          volume(45, false),
          // The series as a catalogue lists it, with no volume: part of the group.
          authorWork({ workId: 'op', title: 'ONE PIECE', seriesTitle: undefined, volumeNumber: undefined }),
          authorWork({ title: 'Wanted!', seriesTitle: undefined, volumeNumber: undefined }),
        ],
      }),
    );
    renderAuthor();

    expect(await screen.findByText('One Piece')).toBeInTheDocument();
    expect(screen.getByText('2 possédés sur 3 tomes connus')).toBeInTheDocument();
    expect(screen.queryByText('One Piece Tome 45')).not.toBeInTheDocument();
    expect(screen.queryByText('ONE PIECE')).not.toBeInTheDocument();
    expect(screen.getByText('Wanted!')).toBeInTheDocument();
    // The count is of what the page lists: one series and one standalone title.
    expect(screen.getByText('2 œuvres')).toBeInTheDocument();
  });
});

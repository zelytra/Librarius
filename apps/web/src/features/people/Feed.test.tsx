import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';
import { Toaster } from '../../shared/ui/Toaster';
import { resetToasts } from '../../shared/ui/toast';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { Feed } = await import('./Feed');

const BOB = { id: 'bob', displayName: 'Bob', trusted: false };
const book = (title: string) => ({ editionId: `e-${title}`, workId: `w-${title}`, kind: 'BOOK', title, authors: 'Une Autrice' });

const FINISHED = {
  id: 'ev1', type: 'FINISHED_READING', createdAt: '2026-11-15T10:00:00Z', actor: BOB, book: book('Dune'),
  reactions: { LOVE: 2 }, myReaction: null, comments: [
    { id: 'c1', author: { id: 'carol', displayName: 'Carol' }, comment: 'Un classique !', createdAt: '2026-11-15T11:00:00Z', mine: false },
  ],
};
const REVIEW = {
  id: 'ev2', type: 'REVIEW_SHARED', createdAt: '2026-11-14T10:00:00Z', actor: BOB, book: book('Monster'),
  rating: 4.5, review: 'Haletant.', reactions: {}, myReaction: 'CLAP', comments: [
    { id: 'c2', author: { id: 'me', displayName: 'Moi' }, comment: 'Je note !', createdAt: '2026-11-14T11:00:00Z', mine: true },
  ],
};
const WISH = {
  id: 'ev3', type: 'WISHLIST_ADDED', createdAt: '2026-11-13T10:00:00Z', actor: BOB, book: book('Akira'),
  reactions: {}, myReaction: null, comments: [],
};

function feedIs(items: unknown[]) {
  server.use(http.get('*/api/feed', () => HttpResponse.json({ items, page: 0, size: 20, hasMore: false })));
}

function renderFeed() {
  return renderWithProviders(<><Feed onFindPeople={onFindPeople} /><Toaster /></>);
}

const onFindPeople = vi.fn();

/** #210: the feed of what followed readers did, with reactions and comments. */
describe('Feed', () => {
  beforeEach(() => {
    setAuthenticated(true);
    server.use(http.get('*/api/releases/upcoming', () => HttpResponse.json([])));
  });
  afterEach(() => {
    resetAuth();
    resetToasts();
    onFindPeople.mockClear();
  });

  test('tells what each followed reader did', async () => {
    feedIs([FINISHED, REVIEW, WISH]);
    renderFeed();

    const finished = (await screen.findByText('Dune')).closest('article')!;
    expect(within(finished).getByText(/a terminé/)).toBeInTheDocument();
    // #513: the book opens its screen — the catalog page, the reader holding no copy.
    expect(within(finished).getByRole('link', { name: /Dune/ })).toHaveAttribute('href', '/catalog/librarius/e-Dune');
    expect(within(finished).getByRole('link', { name: 'Bob' })).toHaveAttribute('href', '/people/bob');
    expect(within(finished).getByText('Un classique !')).toBeInTheDocument();

    const review = screen.getByText('Monster').closest('article')!;
    expect(within(review).getByText(/a partagé son avis/)).toBeInTheDocument();
    expect(within(review).getByText('Haletant.')).toBeInTheDocument();

    const wish = screen.getByText('Akira').closest('article')!;
    expect(within(wish).getByText(/a ajouté à ses souhaits/)).toBeInTheDocument();
  });

  test('says what to do when there is nothing yet', async () => {
    feedIs([]);
    renderFeed();

    expect(await screen.findByText('Rien de neuf pour l’instant')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Trouver des lecteurs' }));
    expect(onFindPeople).toHaveBeenCalled();
  });

  test('reacts to an event, and taking the same reaction back removes it', async () => {
    feedIs([FINISHED]);
    const calls: string[] = [];
    server.use(
      http.put('*/api/feed/:id/reaction', async ({ params, request }) => {
        calls.push(`PUT ${params.id} ${(await request.json() as { emote: string }).emote}`);
        return new HttpResponse(null, { status: 204 });
      }),
      http.delete('*/api/feed/:id/reaction', ({ params }) => {
        calls.push(`DELETE ${params.id}`);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderFeed();

    const event = (await screen.findByText('Dune')).closest('article')!;
    const love = within(event).getByRole('button', { name: "J'adore (2)" });
    expect(love).toHaveAttribute('aria-pressed', 'false');
    await userEvent.click(love);
    await waitFor(() => expect(calls).toEqual(['PUT ev1 LOVE']));
    expect(within(event).getByRole('button', { name: "J'adore (3)" })).toHaveAttribute('aria-pressed', 'true');

    await userEvent.click(within(event).getByRole('button', { name: "J'adore (3)" }));
    await waitFor(() => expect(calls).toEqual(['PUT ev1 LOVE', 'DELETE ev1']));
  });

  test('comments on an event', async () => {
    feedIs([WISH]);
    let sent: unknown;
    server.use(
      http.post('*/api/feed/:id/comments', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ id: 'c9' }, { status: 201 });
      }),
    );
    renderFeed();

    const event = (await screen.findByText('Akira')).closest('article')!;
    await userEvent.type(within(event).getByRole('textbox', { name: 'Commenter' }), 'Bon choix');
    await userEvent.click(within(event).getByRole('button', { name: 'Envoyer' }));

    await waitFor(() => expect(sent).toEqual({ comment: 'Bon choix' }));
    expect(await screen.findByText('Commentaire publié.')).toBeInTheDocument();
    expect(within(event).getByRole('textbox', { name: 'Commenter' })).toHaveValue('');
  });

  test('deletes your own comment, only yours', async () => {
    feedIs([FINISHED, REVIEW]);
    let deleted = '';
    server.use(
      http.delete('*/api/feed/:id/comments/:comment', ({ params }) => {
        deleted = `${params.id}/${params.comment}`;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderFeed();

    const finished = (await screen.findByText('Dune')).closest('article')!;
    expect(within(finished).queryByRole('button', { name: /Supprimer mon commentaire/ })).not.toBeInTheDocument();
    const review = screen.getByText('Monster').closest('article')!;
    await userEvent.click(within(review).getByRole('button', { name: 'Supprimer mon commentaire' }));

    await waitFor(() => expect(deleted).toBe('ev2/c2'));
    expect(await screen.findByText('Commentaire supprimé.')).toBeInTheDocument();
  });

  test('says so when a reaction fails, and puts it back', async () => {
    feedIs([FINISHED]);
    server.use(http.put('*/api/feed/:id/reaction', () => new HttpResponse(null, { status: 404 })));
    renderFeed();

    const event = (await screen.findByText('Dune')).closest('article')!;
    await userEvent.click(within(event).getByRole('button', { name: 'Bravo (0)' }));

    expect(await screen.findByText("L'action n'a pas abouti. Réessaie.")).toBeInTheDocument();
    expect(within(event).getByRole('button', { name: 'Bravo (0)' })).toHaveAttribute('aria-pressed', 'false');
  });

  test('offers a retry when the feed cannot be loaded', async () => {
    let calls = 0;
    server.use(
      http.get('*/api/feed', () => {
        calls += 1;
        return calls === 1
          ? new HttpResponse(null, { status: 500 })
          : HttpResponse.json({ items: [WISH], page: 0, size: 20, hasMore: false });
      }),
    );
    renderFeed();

    await userEvent.click(await screen.findByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByText('Akira')).toBeInTheDocument();
  });

  test('shows the coming releases apart, with nothing to react to', async () => {
    feedIs([]);
    server.use(
      http.get('*/api/releases/upcoming', () =>
        HttpResponse.json([{ id: 'r1', seriesId: 's1', seriesTitle: 'One Piece', volumeNumber: 110, releaseDate: '2026-12-03', datePrecision: 'DAY', region: 'FR' }]),
      ),
    );
    renderFeed();

    const card = (await screen.findByText('One Piece')).closest('section')!;
    expect(within(card).getByRole('heading', { name: 'Sorties à venir' })).toBeInTheDocument();
    expect(within(card).queryByRole('button', { name: /J'adore/ })).not.toBeInTheDocument();
  });
});

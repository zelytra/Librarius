import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';
import { Toaster } from '../../shared/ui/Toaster';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { NotificationsPage } = await import('./NotificationsPage');
const { NotificationBell } = await import('./NotificationBell');
const { EventPage } = await import('./EventPage');

const BOB = { id: 'bob', displayName: 'Bob' };
const DUNE = { editionId: 'e1', title: 'Dune' };
const ITEMS = [
  { id: 'n1', type: 'ACTIVITY', eventType: 'FINISHED_READING', actor: BOB, eventId: 'ev1', book: DUNE, createdAt: '2026-11-16T10:00:00Z' },
  { id: 'n2', type: 'REACTION', emote: 'LOVE', actor: BOB, eventId: 'ev2', book: DUNE, createdAt: '2026-11-16T09:00:00Z' },
  { id: 'n3', type: 'COMMENT', comment: 'Super !', actor: BOB, eventId: 'ev2', book: DUNE, createdAt: '2026-11-16T08:00:00Z', readAt: '2026-11-16T08:30:00Z' },
];

/** #516, #211: the bell, its list, and the screen a notification opens. */
describe('notifications', () => {
  beforeEach(() => setAuthenticated(true));
  afterEach(() => resetAuth());

  test('lists what happened, each entry opening its screen, and marks everything read', async () => {
    let read = 0;
    server.use(
      http.get('*/api/notifications', () => HttpResponse.json({ items: ITEMS, page: 0, size: 30, hasMore: false })),
      http.post('*/api/notifications/read', () => {
        read += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<NotificationsPage />);

    const activity = await screen.findByRole('link', { name: /Bob a terminé « Dune »/ });
    expect(activity).toHaveAttribute('href', '/feed/ev1');
    expect(screen.getByRole('link', { name: /Bob a réagi ❤️ à ton activité « Dune »/ })).toHaveAttribute('href', '/feed/ev2');
    const comment = screen.getByRole('link', { name: /Bob a commenté ton activité « Dune »/ });
    expect(within(comment).getByText('« Super ! »')).toBeInTheDocument();
    // The two unread ones say so, in words and not only with a dot.
    expect(screen.getAllByText('Non lue')).toHaveLength(2);
    await waitFor(() => expect(read).toBe(1));
  });

  /** #618: what became of a catalog edit proposal, opening the book or the series. */
  test('tells a member what an admin decided on their proposals', async () => {
    const ALICE = { id: 'alice', displayName: 'Alice' };
    server.use(
      http.get('*/api/notifications', () => HttpResponse.json({
        items: [
          { id: 'n20', type: 'EDIT', actor: ALICE, createdAt: '2026-11-16T12:00:00Z',
            edit: { id: 'ce1', status: 'APPLIED', targetType: 'EDITION', targetId: 'e1', targetTitle: 'Dune', libraryItemId: 'item-1' } },
          { id: 'n21', type: 'EDIT', actor: ALICE, createdAt: '2026-11-16T11:00:00Z',
            edit: { id: 'ce2', status: 'REJECTED', targetType: 'SERIES', targetId: 's1', targetTitle: 'One Piece', note: 'Déjà juste.' } },
          { id: 'n22', type: 'EDIT', actor: ALICE, createdAt: '2026-11-16T10:00:00Z',
            edit: { id: 'ce3', status: 'REVERTED', targetType: 'EDITION', targetId: 'e2', targetTitle: 'Hypérion' } },
        ],
        page: 0, size: 30, hasMore: false,
      })),
      http.post('*/api/notifications/read', () => new HttpResponse(null, { status: 204 })),
    );
    renderWithProviders(<NotificationsPage />);

    expect(await screen.findByRole('link', { name: /Ta modification de « Dune » a été acceptée/ }))
      .toHaveAttribute('href', '/detail/item-1');
    const rejected = screen.getByRole('link', { name: /Ta modification de « One Piece » a été refusée/ });
    expect(rejected).toHaveAttribute('href', '/series/s1');
    expect(within(rejected).getByText('« Déjà juste. »')).toBeInTheDocument();
    // Not in the library: nothing to open, the news is still told.
    expect(screen.getByText('Ta modification de « Hypérion » a été annulée par un administrateur.')).toBeInTheDocument();
  });

  /** #634: an automatic outcome is told in words of what decided it. */
  test('words the automatic outcomes of a catalog edit', async () => {
    const SELF = { id: 'me', displayName: 'Moi' };
    const edit = (id: string, status: string, targetTitle: string) =>
      ({ id, type: 'EDIT', actor: SELF, createdAt: '2026-11-16T12:00:00Z', edit: { id, status, targetType: 'EDITION', targetId: 'e', targetTitle } });
    server.use(
      http.get('*/api/notifications', () => HttpResponse.json({
        items: [edit('a', 'VERIFIED', 'Dune'), edit('b', 'TRUSTED', 'Hypérion'), edit('c', 'UNVERIFIED', 'Fondation')],
        page: 0, size: 30, hasMore: false,
      })),
      http.post('*/api/notifications/read', () => new HttpResponse(null, { status: 204 })),
    );
    renderWithProviders(<NotificationsPage />);

    expect(await screen.findByText('Ta modification de « Dune » est appliquée : la page du magasin confirme le livre.')).toBeInTheDocument();
    expect(screen.getByText('Ta modification de « Hypérion » est appliquée : tu es membre de confiance.')).toBeInTheDocument();
    expect(screen.getByText('Ta modification de « Fondation » attend un administrateur : le magasin n’a pas pu confirmer le livre.')).toBeInTheDocument();
  });

  /** #629: deleting a notification, or all of them behind a confirmation. */
  test('deletes one notification, then all of them once confirmed', async () => {
    let items = [...ITEMS];
    const deleted: string[] = [];
    server.use(
      http.get('*/api/notifications', () => HttpResponse.json({ items, page: 0, size: 30, hasMore: false })),
      http.post('*/api/notifications/read', () => new HttpResponse(null, { status: 204 })),
      http.delete('*/api/notifications/:id', ({ params }) => {
        deleted.push(String(params.id));
        items = items.filter((item) => item.id !== params.id);
        return new HttpResponse(null, { status: 204 });
      }),
      http.delete('*/api/notifications', () => {
        deleted.push('ALL');
        items = [];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<><NotificationsPage /><Toaster /></>);

    const first = (await screen.findByRole('link', { name: /Bob a terminé « Dune »/ })).closest('li')!;
    await userEvent.click(within(first).getByRole('button', { name: 'Supprimer cette notification' }));
    await waitFor(() => expect(deleted).toEqual(['n1']));
    await waitFor(() => expect(screen.queryByRole('link', { name: /Bob a terminé « Dune »/ })).not.toBeInTheDocument());
    expect(await screen.findByText('Notification supprimée.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Tout supprimer' }));
    const dialog = within(await screen.findByRole('dialog', { name: 'Supprimer toutes les notifications ?' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Tout supprimer' }));
    await waitFor(() => expect(deleted).toEqual(['n1', 'ALL']));
    expect(await screen.findByText('Aucune notification pour l’instant')).toBeInTheDocument();
  });

  /** #544: a new follower — their profile one tap away, and a way to follow them back. */
  test('announces a new follower, opens their profile and follows them back', async () => {
    const followed: string[] = [];
    let following = false;
    server.use(
      http.get('*/api/notifications', () => HttpResponse.json({
        items: [{ id: 'n9', type: 'FOLLOW', actor: BOB, followingActor: following, createdAt: '2026-11-16T11:00:00Z' }],
        page: 0, size: 30, hasMore: false,
      })),
      http.post('*/api/notifications/read', () => new HttpResponse(null, { status: 204 })),
      http.put('*/api/users/:id/follow', ({ params }) => {
        followed.push(String(params.id));
        following = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<NotificationsPage />);

    expect(await screen.findByRole('link', { name: /Bob s’est abonné à toi/ })).toHaveAttribute('href', '/people/bob');
    await userEvent.click(screen.getByRole('button', { name: 'Suivre Bob en retour' }));

    await waitFor(() => expect(followed).toEqual(['bob']));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Suivre Bob en retour' })).not.toBeInTheDocument());
    expect(screen.getByText('Vous vous suivez')).toBeInTheDocument();
  });

  /** #615: a volume announced for a followed author or series — nobody's doing, it opens the series. */
  test('announces an upcoming volume, opening its series', async () => {
    server.use(
      http.get('*/api/notifications', () => HttpResponse.json({
        items: [{
          id: 'n10', type: 'RELEASE', followingActor: false, createdAt: '2026-11-16T12:00:00Z',
          release: {
            id: 'r1', seriesId: 'series-1', seriesTitle: 'Vinland Saga', kind: 'MANGA', volumeNumber: 28,
            releaseDate: '2026-12-10', datePrecision: 'DAY', region: 'FR', source: 'manual', confidence: 'CONFIRMED',
          },
        }],
        page: 0, size: 30, hasMore: false,
      })),
      http.post('*/api/notifications/read', () => new HttpResponse(null, { status: 204 })),
    );
    renderWithProviders(<NotificationsPage />);

    const entry = await screen.findByRole('link', { name: /Tome 28 de « Vinland Saga » annoncé pour le 10 décembre 2026/ });
    expect(entry).toHaveAttribute('href', '/series/series-1');
  });

  test('offers no follow-back to a follower already followed', async () => {
    server.use(
      http.get('*/api/notifications', () => HttpResponse.json({
        items: [{ id: 'n9', type: 'FOLLOW', actor: BOB, followingActor: true, createdAt: '2026-11-16T11:00:00Z' }],
        page: 0, size: 30, hasMore: false,
      })),
      http.post('*/api/notifications/read', () => new HttpResponse(null, { status: 204 })),
    );
    renderWithProviders(<NotificationsPage />);

    await screen.findByRole('link', { name: /Bob s’est abonné à toi/ });
    expect(screen.queryByRole('button', { name: 'Suivre Bob en retour' })).not.toBeInTheDocument();
  });

  test('says when there is nothing yet', async () => {
    server.use(
      http.get('*/api/notifications', () => HttpResponse.json({ items: [], page: 0, size: 30, hasMore: false })),
      http.post('*/api/notifications/read', () => new HttpResponse(null, { status: 204 })),
    );
    renderWithProviders(<NotificationsPage />);

    expect(await screen.findByText('Aucune notification pour l’instant')).toBeInTheDocument();
  });

  test('offers a retry when the list cannot be loaded', async () => {
    let calls = 0;
    server.use(
      http.get('*/api/notifications', () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json({ items: ITEMS, page: 0, size: 30, hasMore: false });
      }),
      http.post('*/api/notifications/read', () => new HttpResponse(null, { status: 204 })),
    );
    renderWithProviders(<NotificationsPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByRole('link', { name: /Bob a terminé/ })).toBeInTheDocument();
  });

  test('the bell says how many are unread, and leads to the list', async () => {
    server.use(http.get('*/api/notifications/unread', () => HttpResponse.json({ count: 3 })));
    renderWithProviders(<NotificationBell />);

    const bell = await screen.findByRole('link', { name: 'Notifications, 3 non lues' });
    expect(bell).toHaveAttribute('href', '/notifications');
    expect(within(bell).getByText('3')).toBeInTheDocument();
  });

  test('the bell shows no count when everything is read', async () => {
    let asked = false;
    server.use(http.get('*/api/notifications/unread', () => {
      asked = true;
      return HttpResponse.json({ count: 0 });
    }));
    renderWithProviders(<NotificationBell />);

    await waitFor(() => expect(asked).toBe(true));
    expect(screen.getByRole('link', { name: 'Notifications' })).toBeInTheDocument();
  });

  test('opens the event a notification is about, with its comments', async () => {
    server.use(
      http.get('*/api/feed/ev1', () => HttpResponse.json({
        id: 'ev1', type: 'FINISHED_READING', createdAt: '2026-11-16T10:00:00Z', actor: BOB, book: DUNE,
        reactions: {}, comments: [{ id: 'c1', author: BOB, comment: 'Lu en deux jours', createdAt: '2026-11-16T10:00:00Z' }],
      })),
      http.get('*/api/releases/upcoming', () => HttpResponse.json([])),
    );
    renderWithProviders(
      <Routes>
        <Route path="/feed/:eventId" element={<EventPage />} />
      </Routes>,
      { route: '/feed/ev1' },
    );

    expect(await screen.findByText('Lu en deux jours')).toBeInTheDocument();
    expect(screen.getByText(/a terminé/)).toBeInTheDocument();
  });

  test('says an event out of reach is no longer available', async () => {
    server.use(http.get('*/api/feed/gone', () => new HttpResponse(null, { status: 404 })));
    renderWithProviders(
      <Routes>
        <Route path="/feed/:eventId" element={<EventPage />} />
      </Routes>,
      { route: '/feed/gone' },
    );

    expect(await screen.findByText('Cette activité n’est plus disponible')).toBeInTheDocument();
  });
});

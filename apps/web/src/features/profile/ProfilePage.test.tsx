import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';
import { Toaster } from '../../shared/ui/Toaster';
import { resetToasts } from '../../shared/ui/toast';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { ProfilePage } = await import('./ProfilePage');

const BOB = {
  id: 'bob', displayName: 'Bob', trusted: true, memberSince: '2025-03-02T10:00:00Z', self: false,
  following: true, followsYou: true, followers: 12, followingCount: 4, visible: true,
  reading: {
    read: 42, reading: 2, toRead: 7, abandoned: 1, wished: 5, pagesRead: 12345, readThisYear: 9,
    topGenres: ['Fantasy', 'Policier'],
    currentlyReading: [{ book: { editionId: 'e1', workId: 'w1', kind: 'BOOK', title: 'Le Nom du vent', authors: 'Patrick Rothfuss', myItemId: 'mine-1' }, status: 'READING', percent: 40 }],
    recentlyFinished: [{ book: { editionId: 'e2', workId: 'w2', kind: 'MANGA', title: 'Monster', authors: 'Naoki Urasawa', provider: 'anilist', providerRef: '30001' }, status: 'READ', finishedAt: '2026-11-01', rating: 4.5 }],
    reviews: [{ book: { editionId: 'e2', workId: 'w2', kind: 'MANGA', title: 'Monster', authors: 'Naoki Urasawa' }, status: 'READ', rating: 4.5, review: 'Haletant de bout en bout.' }],
  },
};

function renderProfile(id = 'bob') {
  return renderWithProviders(
    <>
      <Routes>
        <Route path="/people/:id" element={<ProfilePage />} />
        <Route path="/people" element={<p>Liste des lecteurs</p>} />
      </Routes>
      <Toaster />
    </>,
    { route: `/people/${id}` },
  );
}

/** #497: a member's profile page. */
describe('ProfilePage', () => {
  beforeEach(() => setAuthenticated(true));
  afterEach(() => {
    resetAuth();
    resetToasts();
  });

  test('shows who the member is and what they read', async () => {
    server.use(http.get('*/api/users/bob/profile', () => HttpResponse.json(BOB)));
    renderProfile();

    expect(await screen.findByRole('heading', { name: /Bob/ })).toBeInTheDocument();
    expect(screen.getByText('12 abonnés')).toBeInTheDocument();
    expect(screen.getByText('Vous vous suivez')).toBeInTheDocument();
    const stats = screen.getByRole('list', { name: 'Ses lectures en chiffres' });
    expect(within(stats).getByText('42')).toBeInTheDocument();
    expect(within(stats).getByText('Lus')).toBeInTheDocument();
    expect(screen.getByText('Fantasy')).toBeInTheDocument();
    expect(screen.getByText('Le Nom du vent')).toBeInTheDocument();
    expect(screen.getByText('40 %')).toBeInTheDocument();
    expect(screen.getByText('Haletant de bout en bout.')).toBeInTheDocument();
  });

  test('says the reading is private when the account is out of reach', async () => {
    server.use(
      http.get('*/api/users/bob/profile', () =>
        HttpResponse.json({ ...BOB, following: false, followsYou: false, visible: false, reading: null }),
      ),
    );
    renderProfile();

    expect(await screen.findByText('Profil privé')).toBeInTheDocument();
    expect(screen.queryByText('Le Nom du vent')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Suivre Bob' })).toHaveAttribute('aria-pressed', 'false');
  });

  test('follows the member and says so', async () => {
    let followed = false;
    server.use(
      http.get('*/api/users/bob/profile', () =>
        HttpResponse.json({ ...BOB, following: followed, visible: false, reading: null }),
      ),
      http.put('*/api/users/bob/follow', () => {
        followed = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderProfile();

    await userEvent.click(await screen.findByRole('button', { name: 'Suivre Bob' }));

    expect(await screen.findByText('Tu suis Bob.')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Ne plus suivre Bob' })).toHaveAttribute('aria-pressed', 'true'),
    );
  });

  test('blocks the member after a confirmation, then leaves the profile', async () => {
    let blocked = false;
    server.use(
      http.get('*/api/users/bob/profile', () => HttpResponse.json(BOB)),
      http.put('*/api/users/bob/block', () => {
        blocked = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderProfile();

    await userEvent.click(await screen.findByRole('button', { name: 'Bloquer' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Bloquer Bob' }));

    await waitFor(() => expect(blocked).toBe(true));
    expect(await screen.findByText('Bob est bloqué.')).toBeInTheDocument();
    expect(await screen.findByText('Liste des lecteurs')).toBeInTheDocument();
  });

  test('offers no follow nor block on your own profile', async () => {
    server.use(http.get('*/api/users/me-id/profile', () => HttpResponse.json({ ...BOB, id: 'me-id', self: true })));
    renderProfile('me-id');

    await screen.findByRole('heading', { name: /Bob/ });
    expect(screen.queryByRole('button', { name: /Suivre/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Bloquer' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Modifier mon profil' })).toHaveAttribute('href', '/settings/profile');
  });

  test('says a profile out of reach is unavailable', async () => {
    server.use(http.get('*/api/users/ghost/profile', () => new HttpResponse(null, { status: 404 })));
    renderProfile('ghost');

    expect(await screen.findByText("Ce profil n'est pas disponible")).toBeInTheDocument();
  });

  test('offers a retry when the profile cannot be loaded', async () => {
    let calls = 0;
    server.use(
      http.get('*/api/users/bob/profile', () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(BOB);
      }),
    );
    renderProfile();

    await userEvent.click(await screen.findByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByRole('heading', { name: /Bob/ })).toBeInTheDocument();
  });

  /** #513: a book opens the reader's own Detail when they hold it, the catalog page otherwise. */
  test("opens a book on the reader's own screen or on the catalog page", async () => {
    server.use(http.get('*/api/users/bob/profile', () => HttpResponse.json(BOB)));
    renderProfile();

    expect(await screen.findByRole('link', { name: /Le Nom du vent/ })).toHaveAttribute('href', '/detail/mine-1');
    expect(screen.getAllByRole('link', { name: /Monster/ })[0]).toHaveAttribute('href', '/catalog/anilist/30001');
  });

  /** #514: the member's shelves, a tab each. */
  test("browses the member's wishlist, a page at a time", async () => {
    const asked: string[] = [];
    server.use(
      http.get('*/api/users/bob/profile', () => HttpResponse.json(BOB)),
      http.get('*/api/users/bob/shelf', ({ request }) => {
        const url = new URL(request.url);
        asked.push(`${url.searchParams.get('shelf')}:${url.searchParams.get('page')}`);
        const page = Number(url.searchParams.get('page'));
        return HttpResponse.json({
          page, size: 30, total: 2,
          items: [{ book: { editionId: `w${page}`, title: page === 0 ? 'Akira' : 'Berserk' } }],
        });
      }),
    );
    renderProfile();

    await userEvent.click(await screen.findByRole('button', { name: 'Souhaits' }));
    expect(await screen.findByText('Akira')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Voir plus' }));
    expect(await screen.findByText('Berserk')).toBeInTheDocument();
    expect(asked).toEqual(['wishlist:0', 'wishlist:1']);
  });

  test('says when a shelf is empty', async () => {
    server.use(
      http.get('*/api/users/bob/profile', () => HttpResponse.json(BOB)),
      http.get('*/api/users/bob/shelf', () => HttpResponse.json({ page: 0, size: 30, total: 0, items: [] })),
    );
    renderProfile();

    await userEvent.click(await screen.findByRole('button', { name: 'À lire' }));
    expect(await screen.findByText('Rien sur cette étagère.')).toBeInTheDocument();
  });

  test('offers no shelf of a private profile', async () => {
    server.use(
      http.get('*/api/users/bob/profile', () =>
        HttpResponse.json({ ...BOB, following: false, followsYou: false, visible: false, reading: null }),
      ),
    );
    renderProfile();

    await screen.findByText('Profil privé');
    expect(screen.queryByRole('button', { name: 'Souhaits' })).not.toBeInTheDocument();
  });

  /** #516: a followed member's activity notifies by default; the profile switches it off. */
  test("switches off the notifications of a followed member's activity", async () => {
    let sent: unknown;
    let notify = true;
    server.use(
      http.get('*/api/users/bob/profile', () => HttpResponse.json({ ...BOB, notifyActivity: notify })),
      http.put('*/api/users/bob/follow/notify', async ({ request }) => {
        sent = await request.json();
        notify = false;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderProfile();

    const toggle = await screen.findByRole('switch', { name: 'Me notifier de ses activités' });
    expect(toggle).toHaveAttribute('aria-checked', 'true');
    await userEvent.click(toggle);

    await waitFor(() => expect(sent).toEqual({ notifyActivity: false }));
    expect(await screen.findByText('Tu ne seras plus notifié des activités de Bob.')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Me notifier de ses activités' })).toHaveAttribute('aria-checked', 'false'));
  });

  test('offers no switch for a member one does not follow', async () => {
    server.use(http.get('*/api/users/bob/profile', () => HttpResponse.json({ ...BOB, following: false, notifyActivity: null })));
    renderProfile();

    await screen.findByRole('heading', { name: /Bob/ });
    expect(screen.queryByRole('switch')).not.toBeInTheDocument();
  });
});

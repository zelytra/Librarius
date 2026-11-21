import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';
import { Toaster } from '../../shared/ui/Toaster';
import { resetToasts } from '../../shared/ui/toast';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { AdminPage } = await import('./AdminPage');

const REPORTS = {
  page: 0,
  size: 50,
  total: 2,
  items: [
    {
      id: 'f1', kind: 'BUG', source: 'USER', message: 'Le tri ne marche plus', page: '/collection',
      appVersion: '1.4.0', reporterName: 'Bob', occurrences: 1,
      createdAt: '2026-11-15T10:00:00Z', lastSeenAt: '2026-11-15T10:00:00Z', status: 'NEW',
    },
    {
      id: 'f2', kind: 'ERROR', source: 'API', message: 'IllegalStateException: boom', page: 'GET /api/library',
      reporterName: null, occurrences: 7,
      createdAt: '2026-11-14T08:00:00Z', lastSeenAt: '2026-11-15T09:00:00Z', status: 'HANDLED',
    },
  ],
};

const MONITORING = {
  activeNow: 3,
  days: [
    { day: '2026-11-14', reports: 1, errors: 4, activeUsers: 5 },
    { day: '2026-11-15', reports: 2, errors: 0, activeUsers: 6 },
  ],
  services: [
    { name: 'database', up: true },
    { name: 'keycloak', up: false, detail: 'HTTP 503' },
  ],
};

function meIs(admin: boolean) {
  server.use(http.get('*/api/me', () => HttpResponse.json({ id: 'alice', displayName: 'Alice', admin })));
}

/** #488–#491: the admin screens, for the admin role alone. */
describe('AdminPage', () => {
  beforeEach(() => {
    setAuthenticated(true);
    server.use(
      http.get('*/api/admin/feedback', () => HttpResponse.json(REPORTS)),
      http.get('*/api/admin/feedback/:id', ({ params }) => HttpResponse.json({
        ...REPORTS.items.find((r) => r.id === params.id), userAgent: 'Firefox 140', details: 'GET /api/library → 500',
      })),
      http.get('*/api/admin/monitoring', () => HttpResponse.json(MONITORING)),
    );
  });
  afterEach(() => {
    resetAuth();
    resetToasts();
  });

  test('is closed to a member without the admin role', async () => {
    meIs(false);
    renderWithProviders(<AdminPage />, { route: '/admin' });

    expect(await screen.findByText('Réservé aux administrateurs')).toBeInTheDocument();
    expect(screen.queryByText('Le tri ne marche plus')).not.toBeInTheDocument();
  });

  test('lists the reports with their kind, who sent them and how often an error came back', async () => {
    meIs(true);
    renderWithProviders(<AdminPage />, { route: '/admin' });

    const bug = (await screen.findByText('Le tri ne marche plus')).closest('li')!;
    expect(within(bug).getByText('Problème')).toBeInTheDocument();
    expect(within(bug).getByText(/Bob/)).toBeInTheDocument();
    const error = screen.getByText('IllegalStateException: boom').closest('li')!;
    expect(within(error).getByText('Erreur API')).toBeInTheDocument();
    expect(within(error).getByText('×7')).toBeInTheDocument();
  });

  test('opens a report with its logs and the browser it came from', async () => {
    meIs(true);
    renderWithProviders(<AdminPage />, { route: '/admin' });

    await userEvent.click(await screen.findByRole('button', { name: /Le tri ne marche plus/ }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText('GET /api/library → 500')).toBeInTheDocument();
    expect(within(dialog).getByText('Firefox 140')).toBeInTheDocument();
    expect(within(dialog).getByText('/collection')).toBeInTheDocument();
  });

  /** #495: where an admin stands with each report. */
  test('shows the triage status of each report', async () => {
    meIs(true);
    renderWithProviders(<AdminPage />, { route: '/admin' });

    const bug = (await screen.findByText('Le tri ne marche plus')).closest('li')!;
    expect(within(bug).getByText('Nouveau')).toBeInTheDocument();
    const error = screen.getByText('IllegalStateException: boom').closest('li')!;
    expect(within(error).getByText('Traité')).toBeInTheDocument();
  });

  test('filters the reports on a status', async () => {
    meIs(true);
    const asked: (string | null)[] = [];
    server.use(
      http.get('*/api/admin/feedback', ({ request }) => {
        const status = new URL(request.url).searchParams.get('status');
        asked.push(status);
        return HttpResponse.json(status === 'HANDLED' ? { ...REPORTS, total: 1, items: [REPORTS.items[1]] } : REPORTS);
      }),
    );
    renderWithProviders(<AdminPage />, { route: '/admin' });

    await screen.findByText('Le tri ne marche plus');
    await userEvent.click(screen.getByRole('button', { name: 'Traités' }));

    await waitFor(() => expect(screen.queryByText('Le tri ne marche plus')).not.toBeInTheDocument());
    expect(screen.getByText('IllegalStateException: boom')).toBeInTheDocument();
    expect(asked).toContain('HANDLED');
    expect(screen.getByRole('button', { name: 'Traités' })).toHaveAttribute('aria-pressed', 'true');
  });

  test('marks an opened report as handled, and says so', async () => {
    meIs(true);
    let sent: unknown;
    server.use(
      http.put('*/api/admin/feedback/:id/status', async ({ request, params }) => {
        sent = { id: params.id, ...(await request.json() as object) };
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<><AdminPage /><Toaster /></>, { route: '/admin' });

    await userEvent.click(await screen.findByRole('button', { name: /Le tri ne marche plus/ }));
    const dialog = await screen.findByRole('dialog');
    const handled = await within(dialog).findByRole('button', { name: 'Marquer traité' });
    expect(handled).toHaveAttribute('aria-pressed', 'false');
    await userEvent.click(handled);

    await waitFor(() => expect(sent).toEqual({ id: 'f1', status: 'HANDLED' }));
    expect(await screen.findByText('Signalement marqué traité.')).toBeInTheDocument();
  });

  test('says so when the status cannot be saved', async () => {
    meIs(true);
    server.use(http.put('*/api/admin/feedback/:id/status', () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<><AdminPage /><Toaster /></>, { route: '/admin' });

    await userEvent.click(await screen.findByRole('button', { name: /Le tri ne marche plus/ }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(await within(dialog).findByRole('button', { name: 'Marquer non pertinent' }));

    expect(await screen.findByText("Le statut n'a pas pu être enregistré.")).toBeInTheDocument();
  });

  test('shows the monitoring: members active now, the days, the services', async () => {
    meIs(true);
    renderWithProviders(<AdminPage />, { route: '/admin?tab=monitoring' });

    const active = (await screen.findByText('actifs maintenant')).parentElement!;
    expect(active).toHaveTextContent('3');
    expect(screen.getByRole('figure', { name: /Signalements par jour/ })).toBeInTheDocument();
    expect(screen.getByRole('figure', { name: /Membres actifs par jour/ })).toBeInTheDocument();
    const keycloak = screen.getByText('Keycloak').closest('li')!;
    expect(within(keycloak).getByText(/Indisponible/)).toBeInTheDocument();
    expect(within(keycloak).getByText(/HTTP 503/)).toBeInTheDocument();
    const database = screen.getByText('Base de données').closest('li')!;
    expect(within(database).getByText('Opérationnel')).toBeInTheDocument();
  });

  /** #574: every chart has its axes, and a column says what it holds on hover and on focus. */
  test('draws axes on the charts and reads a column out on hover or focus', async () => {
    meIs(true);
    renderWithProviders(<AdminPage />, { route: '/admin?tab=monitoring' });

    const reports = await screen.findByRole('figure', { name: /Signalements par jour/ });
    // The value axis runs from 0 to the busiest day (1 + 4), the time axis from the first day to the last.
    const values = within(reports).getByTestId('y-axis');
    expect(within(values).getByText('5')).toBeInTheDocument();
    expect(within(values).getByText('0')).toBeInTheDocument();
    const dates = within(reports).getByTestId('x-axis');
    expect(within(dates).getByText('14 nov.')).toBeInTheDocument();
    expect(within(dates).getByText('15 nov.')).toBeInTheDocument();

    const first = within(reports).getByRole('button', { name: '14 nov. · Membres 1 · Erreurs 4' });
    await userEvent.hover(first);
    expect(within(reports).getByRole('tooltip')).toHaveTextContent('14 nov. · Membres 1 · Erreurs 4');
    await userEvent.unhover(first);
    expect(within(reports).queryByRole('tooltip')).not.toBeInTheDocument();

    const active = screen.getByRole('figure', { name: /Membres actifs par jour/ });
    within(active).getByRole('button', { name: '15 nov. · Membres actifs 6' }).focus();
    expect(await within(active).findByRole('tooltip')).toHaveTextContent('15 nov. · Membres actifs 6');
  });

  test('switches between the reports and the monitoring', async () => {
    meIs(true);
    renderWithProviders(<AdminPage />, { route: '/admin' });

    await screen.findByText('Le tri ne marche plus');
    await userEvent.click(screen.getByRole('button', { name: 'Supervision' }));
    await waitFor(() => expect(screen.getByText('actifs maintenant')).toBeInTheDocument());
  });

  /** #499: the members, and deleting one with everything they own. */
  describe('users', () => {
    const USERS = {
      page: 0, size: 50, total: 2,
      items: [
        { id: 'u-bob', username: 'bob', displayName: 'Bob Lecteur', email: 'bob@x.fr', createdAt: '2026-03-02T10:00:00Z', libraryItems: 12, avatarVersion: null },
        { id: 'u-eve', username: 'eve', displayName: 'Ève', createdAt: '2026-05-01T10:00:00Z', libraryItems: 0, avatarVersion: null },
      ],
    };

    test('lists the members with their username and creation date', async () => {
      meIs(true);
      server.use(http.get('*/api/admin/users', () => HttpResponse.json(USERS)));
      renderWithProviders(<AdminPage />, { route: '/admin?tab=users' });

      const bob = (await screen.findByText('Bob Lecteur')).closest('li')!;
      expect(within(bob).getByText('@bob')).toBeInTheDocument();
      expect(within(bob).getByText(/Inscrit le 2 mars 2026/)).toBeInTheDocument();
      expect(within(bob).getByText(/12 titres/)).toBeInTheDocument();
    });

    /** #562: a member opens their profile. */
    test("opens a member's profile from the list", async () => {
      meIs(true);
      server.use(http.get('*/api/admin/users', () => HttpResponse.json(USERS)));
      renderWithProviders(<AdminPage />, { route: '/admin?tab=users' });

      expect(await screen.findByRole('link', { name: /Bob Lecteur/ })).toHaveAttribute('href', '/people/u-bob');
      expect(screen.getByRole('link', { name: /Ève/ })).toHaveAttribute('href', '/people/u-eve');
    });

    test('deletes a member once their username is typed, and says so', async () => {
      meIs(true);
      let deleted = '';
      server.use(
        http.get('*/api/admin/users', () => HttpResponse.json(USERS)),
        http.delete('*/api/admin/users/:id', ({ params }) => {
          deleted = String(params.id);
          return new HttpResponse(null, { status: 204 });
        }),
      );
      renderWithProviders(<><AdminPage /><Toaster /></>, { route: '/admin?tab=users' });

      await userEvent.click(await screen.findByRole('button', { name: 'Supprimer Bob Lecteur' }));
      const dialog = await screen.findByRole('dialog');
      const confirm = within(dialog).getByRole('button', { name: 'Supprimer définitivement' });
      expect(confirm).toBeDisabled();
      await userEvent.type(within(dialog).getByLabelText('Tape « bob » pour confirmer'), 'bob');
      await userEvent.click(confirm);

      await waitFor(() => expect(deleted).toBe('u-bob'));
      expect(await screen.findByText('Bob Lecteur a été supprimé, avec toutes ses données.')).toBeInTheDocument();
    });

    test('says so when the deletion fails, with nothing erased', async () => {
      meIs(true);
      server.use(
        http.get('*/api/admin/users', () => HttpResponse.json(USERS)),
        http.delete('*/api/admin/users/:id', () => new HttpResponse(null, { status: 503 })),
      );
      renderWithProviders(<><AdminPage /><Toaster /></>, { route: '/admin?tab=users' });

      await userEvent.click(await screen.findByRole('button', { name: 'Supprimer Bob Lecteur' }));
      const dialog = await screen.findByRole('dialog');
      await userEvent.type(within(dialog).getByLabelText('Tape « bob » pour confirmer'), 'bob');
      await userEvent.click(within(dialog).getByRole('button', { name: 'Supprimer définitivement' }));

      expect(await screen.findByText('La suppression a échoué : rien n’a été effacé.')).toBeInTheDocument();
    });
  });
});

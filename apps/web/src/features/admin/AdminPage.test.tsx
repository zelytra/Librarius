import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

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
      createdAt: '2026-11-15T10:00:00Z', lastSeenAt: '2026-11-15T10:00:00Z',
    },
    {
      id: 'f2', kind: 'ERROR', source: 'API', message: 'IllegalStateException: boom', page: 'GET /api/library',
      reporterName: null, occurrences: 7,
      createdAt: '2026-11-14T08:00:00Z', lastSeenAt: '2026-11-15T09:00:00Z',
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
  afterEach(() => resetAuth());

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

  test('shows the monitoring: members active now, the days, the services', async () => {
    meIs(true);
    renderWithProviders(<AdminPage />, { route: '/admin?tab=monitoring' });

    const active = (await screen.findByText('actifs maintenant')).parentElement!;
    expect(active).toHaveTextContent('3');
    expect(screen.getByRole('img', { name: /Signalements par jour/ })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Membres actifs par jour/ })).toBeInTheDocument();
    const keycloak = screen.getByText('Keycloak').closest('li')!;
    expect(within(keycloak).getByText(/Indisponible/)).toBeInTheDocument();
    expect(within(keycloak).getByText(/HTTP 503/)).toBeInTheDocument();
    const database = screen.getByText('Base de données').closest('li')!;
    expect(within(database).getByText('Opérationnel')).toBeInTheDocument();
  });

  test('switches between the reports and the monitoring', async () => {
    meIs(true);
    renderWithProviders(<AdminPage />, { route: '/admin' });

    await screen.findByText('Le tri ne marche plus');
    await userEvent.click(screen.getByRole('button', { name: 'Supervision' }));
    await waitFor(() => expect(screen.getByText('actifs maintenant')).toBeInTheDocument());
  });
});

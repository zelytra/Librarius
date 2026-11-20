import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { http, HttpResponse, server } from '../test/server';
import { renderWithProviders } from '../test/utils';
import { resetAuth, setAuthenticated } from '../test/oidcMock';
import { BottomNav } from './BottomNav';
import { SideNav } from './SideNav';

vi.mock('react-oidc-context', () => import('../test/oidcMock'));

/**
 * #496: the administration is a destination of the navigation, for the admin role only —
 * it used to hide behind a link in Settings. Both navigations read the same list.
 */
/** Opens the bottom bar's « Plus » sheet when the navigation has one. */
async function openMore() {
  const more = screen.queryByRole('button', { name: 'Plus' });
  if (more) await userEvent.click(more);
}

describe.each([
  ['bottom bar', BottomNav],
  ['side navigation', SideNav],
])('the %s', (_, Nav) => {
  afterEach(() => resetAuth());

  test('offers the administration to an admin', async () => {
    server.use(http.get('*/api/me', () => HttpResponse.json({ id: 'alice', displayName: 'Alice', admin: true })));
    renderWithProviders(<Nav />);
    await screen.findByRole('link', { name: 'Accueil' });
    await new Promise((resolve) => setTimeout(resolve, 50));
    await openMore();

    expect(await screen.findByRole('link', { name: 'Admin' })).toHaveAttribute('href', '/admin');
  });

  test('says nothing of it to a member', async () => {
    let asked = false;
    server.use(
      http.get('*/api/me', () => {
        asked = true;
        return HttpResponse.json({ id: 'bob', displayName: 'Bob', admin: false });
      }),
    );
    renderWithProviders(<Nav />);

    await vi.waitFor(() => expect(asked).toBe(true));
    await openMore();
    expect(screen.getByRole('link', { name: 'Accueil' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
  });

  test('says nothing of it to a signed-out visitor, without asking the API', () => {
    setAuthenticated(false);
    renderWithProviders(<Nav />);

    expect(screen.getByRole('link', { name: 'Accueil' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
  });
});

/** #504: on a phone, four main pages in the bar, every other one a tap away in « Plus ». */
describe('the bottom bar on a phone', () => {
  afterEach(() => resetAuth());

  test('keeps four main pages and puts the others in « Plus »', async () => {
    server.use(http.get('*/api/me', () => HttpResponse.json({ id: 'bob', displayName: 'Bob', admin: false })));
    renderWithProviders(<BottomNav />);

    const bar = screen.getByRole('navigation');
    expect(within(bar).getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual([
      '/', '/collection', '/discover', '/wishlist',
    ]);
    await userEvent.click(screen.getByRole('button', { name: 'Plus' }));

    const sheet = screen.getByRole('dialog', { name: 'Plus' });
    // The reader's own profile (#555) sits just above Settings.
    await within(sheet).findByRole('link', { name: 'Mon profil · Bob' });
    expect(within(sheet).getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual([
      '/to-read', '/stats', '/people', '/notifications', '/people/bob', '/settings',
    ]);
    expect(screen.getByRole('button', { name: 'Plus' })).toHaveAttribute('aria-expanded', 'true');
  });

  test('closes on Escape and gives the focus back', async () => {
    renderWithProviders(<BottomNav />);
    const more = screen.getByRole('button', { name: 'Plus' });

    await userEvent.click(more);
    expect(within(screen.getByRole('dialog')).getAllByRole('link')[0]).toHaveFocus();
    await userEvent.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(more).toHaveFocus();
  });

  test('closes once a page is chosen', async () => {
    renderWithProviders(<BottomNav />);

    await userEvent.click(screen.getByRole('button', { name: 'Plus' }));
    await userEvent.click(screen.getByRole('link', { name: 'Stats' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  test('shows « Plus » as the current place when the page is one of its own', () => {
    renderWithProviders(<BottomNav />, { route: '/stats' });

    expect(screen.getByRole('button', { name: 'Plus' })).toHaveAttribute('data-current', 'true');
  });
});

/**
 * #555, #556: the reader's own profile — their picture and name — and, for an admin, the
 * administration both sit at the foot of the navigation, just above Settings.
 */
describe('the foot of the navigation', () => {
  afterEach(() => resetAuth());

  test('on a wide screen, ends on the administration, the profile and Settings', async () => {
    server.use(http.get('*/api/me', () => HttpResponse.json({ id: 'alice', displayName: 'Alice', admin: true })));
    renderWithProviders(<SideNav />);

    const profile = await screen.findByRole('link', { name: 'Mon profil · Alice' });
    expect(profile).toHaveAttribute('href', '/people/alice');
    expect(within(profile).getByText('Alice')).toBeInTheDocument();
    const links = screen.getAllByRole('link').map((link) => link.getAttribute('href'));
    expect(links.slice(-3)).toEqual(['/admin', '/people/alice', '/settings']);
  });

  test('on a phone, puts the administration and the profile just above Settings', async () => {
    server.use(http.get('*/api/me', () => HttpResponse.json({ id: 'alice', displayName: 'Alice', admin: true })));
    renderWithProviders(<BottomNav />);
    await new Promise((resolve) => setTimeout(resolve, 50));
    await userEvent.click(screen.getByRole('button', { name: 'Plus' }));

    const sheet = screen.getByRole('dialog', { name: 'Plus' });
    await within(sheet).findByRole('link', { name: 'Mon profil · Alice' });
    const links = within(sheet).getAllByRole('link').map((link) => link.getAttribute('href'));
    expect(links.slice(-3)).toEqual(['/admin', '/people/alice', '/settings']);
  });

  test('offers no profile to a signed-out visitor', () => {
    setAuthenticated(false);
    renderWithProviders(<SideNav />);

    expect(screen.queryByRole('link', { name: /Mon profil/ })).not.toBeInTheDocument();
  });
});

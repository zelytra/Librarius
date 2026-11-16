import { screen } from '@testing-library/react';
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
describe.each([
  ['bottom bar', BottomNav],
  ['side navigation', SideNav],
])('the %s', (_, Nav) => {
  afterEach(() => resetAuth());

  test('offers the administration to an admin', async () => {
    server.use(http.get('*/api/me', () => HttpResponse.json({ id: 'alice', displayName: 'Alice', admin: true })));
    renderWithProviders(<Nav />);

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

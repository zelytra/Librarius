import { screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import type { ReactElement } from 'react';
import { renderWithProviders } from '../../test/utils';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';
// The stylesheets as written: jsdom lays nothing out (see `app/safeAreas.test.ts`).
import primitivesCss from './primitives.module.css?raw';
import tokensCss from '../styles/tokens.css?raw';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { SettingsPage } = await import('../../features/settings/SettingsPage');
const { PeoplePage } = await import('../../features/people/PeoplePage');
const { NotificationsPage } = await import('../../features/notifications/NotificationsPage');
const { EventPage } = await import('../../features/notifications/EventPage');
const { ProfilePage } = await import('../../features/profile/ProfilePage');
const { CategoriesPage } = await import('../../features/categories/CategoriesPage');

/**
 * #605: past the tablet breakpoint these screens capped their column at a reading width
 * without centring it, so it hugged the left of a 1140px frame. They now share one reading
 * measure, owned by `Screen`, which centres the whole page — header included.
 */
const READING_SCREENS: [string, ReactElement, { route?: string; path?: string }][] = [
  ['Settings', <SettingsPage />, { route: '/settings', path: '/settings/:category?' }],
  ['a settings category', <SettingsPage />, { route: '/settings/profile', path: '/settings/:category?' }],
  ['People', <PeoplePage />, {}],
  ['Notifications', <NotificationsPage />, {}],
  ['an event', <EventPage />, { route: '/feed/e-1', path: '/feed/:eventId' }],
  ['a profile', <ProfilePage />, { route: '/people/u-3', path: '/people/:id' }],
  ['Categories', <CategoriesPage />, {}],
];

describe('reading-width screens', () => {
  beforeEach(() => setAuthenticated(true));
  afterEach(() => resetAuth());

  test.each(READING_SCREENS)('%s sits in the centred reading-width screen', async (_, page, options) => {
    const { container } = renderWithProviders(page, options);
    expect(await screen.findByRole('button', { name: /^Retour/ })).toBeInTheDocument();
    expect(container.firstElementChild).toHaveClass(/screenReading/);
  });

  test('the reading measure is capped and centred past the tablet breakpoint only', () => {
    expect(primitivesCss).toMatch(/\.screenReading\s*\{[^}]*max-width:\s*var\(--reading-max\)/);
    expect(primitivesCss).toMatch(/\.screen\s*\{[^}]*margin-inline:\s*auto/);
    // `none` on a phone, where the frame is already the cap.
    expect(tokensCss).toMatch(/--reading-max:\s*none/);
    expect(tokensCss).toMatch(/--reading-max:\s*calc\(760px/);
  });
});

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

const native = { value: true };
const enablePushOnDevice = vi.fn<() => Promise<boolean>>();

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));
vi.mock('../../shared/push', () => ({ pushSupported: () => native.value, enablePushOnDevice }));

const { PushSection } = await import('./PushSection');

function servesPrefs(initial = { releases: false, wishlist: false, goal: false }) {
  let prefs = initial;
  const saved: unknown[] = [];
  server.use(
    http.get('*/api/me/push', () => HttpResponse.json(prefs)),
    http.put('*/api/me/push', async ({ request }) => {
      prefs = (await request.json()) as typeof prefs;
      saved.push(prefs);
      return HttpResponse.json(prefs);
    }),
  );
  return saved;
}

/** #69: push notifications, every category off until the reader switches it on. */
describe('PushSection', () => {
  beforeEach(() => {
    resetAuth();
    setAuthenticated(true);
    native.value = true;
    enablePushOnDevice.mockReset();
  });

  test('starts with every category off', async () => {
    servesPrefs();
    renderWithProviders(<PushSection />);

    for (const name of [/Sorties/, /Souhaits/, /Objectif/]) {
      expect(await screen.findByRole('checkbox', { name })).not.toBeChecked();
    }
  });

  test('asks the system first, then switches the category on', async () => {
    const saved = servesPrefs();
    enablePushOnDevice.mockResolvedValue(true);
    renderWithProviders(<PushSection />);

    await userEvent.click(await screen.findByRole('checkbox', { name: /Sorties/ }));

    await waitFor(() => expect(saved).toEqual([{ releases: true, wishlist: false, goal: false }]));
    expect(enablePushOnDevice).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole('checkbox', { name: /Sorties/ })).toBeChecked();
  });

  test('stays off, and says why, when the system refuses', async () => {
    const saved = servesPrefs();
    enablePushOnDevice.mockResolvedValue(false);
    renderWithProviders(<PushSection />);

    await userEvent.click(await screen.findByRole('checkbox', { name: /Souhaits/ }));

    expect(await screen.findByText(/autorise les notifications/)).toBeInTheDocument();
    expect(saved).toEqual([]);
    expect(screen.getByRole('checkbox', { name: /Souhaits/ })).not.toBeChecked();
  });

  test('switches a category off without asking the system', async () => {
    const saved = servesPrefs({ releases: true, wishlist: false, goal: true });
    renderWithProviders(<PushSection />);

    await userEvent.click(await screen.findByRole('checkbox', { name: /Objectif/ }));

    await waitFor(() => expect(saved).toEqual([{ releases: true, wishlist: false, goal: false }]));
    expect(enablePushOnDevice).not.toHaveBeenCalled();
  });

  test('on the web, says push lives in the mobile app', () => {
    native.value = false;
    servesPrefs();
    renderWithProviders(<PushSection />);

    expect(screen.getByText(/application mobile/)).toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });
});

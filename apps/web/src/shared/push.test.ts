import { beforeEach, describe, expect, test, vi } from 'vitest';
import { http, HttpResponse, server } from '../test/server';

type Listener = (payload: unknown) => void;
const listeners = new Map<string, Listener>();
const plugin = {
  checkPermissions: vi.fn(),
  requestPermissions: vi.fn(),
  register: vi.fn(() => Promise.resolve()),
  addListener: vi.fn((event: string, listener: Listener) => {
    listeners.set(event, listener);
    return Promise.resolve({ remove: () => Promise.resolve(listeners.delete(event)) });
  }),
};

vi.mock('@capacitor/push-notifications', () => ({ PushNotifications: plugin }));
vi.mock('@capacitor/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@capacitor/core')>();
  return { ...actual, Capacitor: { ...actual.Capacitor, isNativePlatform: () => true, getPlatform: () => 'android' } };
});

const { enablePushOnDevice, listenToPush } = await import('./push');

/** #69: the system's side of push — the permission, the token, and what a tap opens. */
describe('push', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listeners.clear();
  });

  test('asks the permission when it was never given, then registers', async () => {
    plugin.checkPermissions.mockResolvedValue({ receive: 'prompt' });
    plugin.requestPermissions.mockResolvedValue({ receive: 'granted' });

    await expect(enablePushOnDevice()).resolves.toBe(true);
    expect(plugin.register).toHaveBeenCalled();
  });

  test('registers nothing when the reader refuses', async () => {
    plugin.checkPermissions.mockResolvedValue({ receive: 'prompt' });
    plugin.requestPermissions.mockResolvedValue({ receive: 'denied' });

    await expect(enablePushOnDevice()).resolves.toBe(false);
    expect(plugin.register).not.toHaveBeenCalled();
  });

  test('sends the token the system gives to the account', async () => {
    const sent: unknown[] = [];
    server.use(
      http.put('*/api/me/push/devices', async ({ request }) => {
        sent.push(await request.json());
        return new HttpResponse(null, { status: 204 });
      }),
    );
    listenToPush(() => {});
    await vi.waitFor(() => expect(listeners.has('registration')).toBe(true));

    listeners.get('registration')!({ value: 'fcm-token-1' });

    await vi.waitFor(() => expect(sent).toEqual([{ token: 'fcm-token-1', platform: 'android' }]));
  });

  test('opens the in-app path a tapped notification names, and nothing else', async () => {
    const open = vi.fn();
    listenToPush(open);
    await vi.waitFor(() => expect(listeners.has('pushNotificationActionPerformed')).toBe(true));
    const tap = listeners.get('pushNotificationActionPerformed')!;

    tap({ notification: { data: { path: '/series/s1' } } });
    tap({ notification: { data: { path: 'https://evil.example' } } });
    tap({ notification: { data: { path: '//evil.example' } } });
    tap({ notification: { data: {} } });

    expect(open.mock.calls).toEqual([['/series/s1']]);
  });
});

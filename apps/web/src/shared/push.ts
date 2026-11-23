import { Capacitor } from '@capacitor/core';
import { PushNotifications } from '@capacitor/push-notifications';
import { putApiMePushDevices } from '../api/generated/librarius';

/**
 * The system's side of push notifications (#69). Only the native app receives them: a browser
 * tab is not a device the reader carries, and the web build never asks.
 */
export function pushSupported(): boolean {
  return Capacitor.isNativePlatform();
}

/**
 * Asks the system for the permission when it was never given, then registers the device —
 * its token arrives through {@link listenToPush}. Resolves whether notifications are allowed.
 */
export async function enablePushOnDevice(): Promise<boolean> {
  let status = await PushNotifications.checkPermissions();
  if (status.receive === 'prompt' || status.receive === 'prompt-with-rationale') {
    status = await PushNotifications.requestPermissions();
  }
  if (status.receive !== 'granted') return false;
  await PushNotifications.register();
  return true;
}

/** A path inside the app, never a link out of it — a notification's data is not trusted blindly. */
const inAppPath = (value: unknown): value is string =>
  typeof value === 'string' && value.startsWith('/') && !value.startsWith('//');

/**
 * Sends every token the system hands out to the account, and opens the screen a tapped
 * notification names. Returns what removes the listeners.
 */
export function listenToPush(open: (path: string) => void): () => void {
  const handles = [
    PushNotifications.addListener('registration', ({ value }) => {
      putApiMePushDevices({ token: value, platform: Capacitor.getPlatform() === 'ios' ? 'ios' : 'android' }).catch(
        () => {
          /* the next launch registers again */
        },
      );
    }),
    PushNotifications.addListener('pushNotificationActionPerformed', ({ notification }) => {
      const path: unknown = (notification.data as { path?: unknown } | undefined)?.path;
      if (inAppPath(path)) open(path);
    }),
  ];
  return () => {
    for (const handle of handles) void handle.then((h) => h.remove());
  };
}

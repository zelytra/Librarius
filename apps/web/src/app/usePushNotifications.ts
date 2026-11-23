import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { useApiAuth } from '../shared/api';
import { useGetApiMePush } from '../api/generated/librarius';
import { listenToPush, pushSupported } from '../shared/push';
import { PushNotifications } from '@capacitor/push-notifications';

/**
 * On the native app, for a signed-in reader (#69): listens for the device's token and for taps
 * on a notification, and — when one category is on — registers again at every launch, so a
 * token the system rotated reaches the account. Nothing is asked of the reader here: the
 * permission is requested in Settings, when they switch a category on.
 */
export function usePushNotifications(): void {
  const { authed } = useApiAuth();
  const navigate = useNavigate();
  const supported = pushSupported();
  const { data: prefs } = useGetApiMePush({ query: { enabled: supported && authed } });
  const anyOn = prefs != null && (prefs.releases || prefs.wishlist || prefs.goal);

  useEffect(() => {
    if (!supported || !authed) return;
    return listenToPush((path) => void navigate(path));
  }, [supported, authed, navigate]);

  useEffect(() => {
    if (!supported || !anyOn) return;
    void PushNotifications.checkPermissions().then(({ receive }) => {
      if (receive === 'granted') void PushNotifications.register();
    });
  }, [supported, anyOn]);
}

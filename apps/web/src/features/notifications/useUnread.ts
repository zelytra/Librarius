import { useGetApiNotificationsUnread } from '../../api/generated/librarius';
import { useApiAuth } from '../../shared/api';

/** How often the bell asks whether something new happened, while the app is open. */
const POLL_MS = 60_000;

/** The unread notifications count (#516): asked on focus and every minute, signed in only. */
export function useUnreadCount(): number {
  const { authed } = useApiAuth();
  const { data } = useGetApiNotificationsUnread({
    query: { enabled: authed, refetchInterval: POLL_MS, refetchOnWindowFocus: true },
  });
  return data?.count ?? 0;
}

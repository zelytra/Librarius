import { useGetApiMe, type MeDto } from '../api/generated/librarius';
import { useApiAuth } from '../shared/api';
import { ADMIN_DESTINATION, NAV_DESTINATIONS, NOTIFICATIONS_DESTINATION, PEOPLE_DESTINATION, type Destination } from './navigation';

/**
 * The destinations both navigations draw: the six of the application — plus the readers,
 * for the side navigation only (`withPeople`), and the notifications once signed in (#516).
 */
export function useNavDestinations(withPeople = false): readonly Destination[] {
  const { authed } = useApiAuth();
  return [
    ...NAV_DESTINATIONS,
    ...(withPeople ? [PEOPLE_DESTINATION] : []),
    ...(withPeople && authed ? [NOTIFICATIONS_DESTINATION] : []),
  ];
}

/**
 * What the foot of either navigation shows above Settings (#555, #556): the administration
 * for an admin (#496 — the API checks the role on every call, hiding the entry only spares
 * everyone else a screen they cannot use), then the reader's own profile. Nothing is asked of
 * the API while signed out, and both appear once `/api/me` has answered.
 */
export function useAccountEntries(): { admin: Destination | null; me: MeDto | null } {
  const { authed } = useApiAuth();
  const { data: me } = useGetApiMe({ query: { enabled: authed } });
  if (!authed || !me?.id) return { admin: null, me: null };
  return { admin: me.admin ? ADMIN_DESTINATION : null, me };
}

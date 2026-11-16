import { useGetApiMe } from '../api/generated/librarius';
import { useApiAuth } from '../shared/api';
import { ADMIN_DESTINATION, NAV_DESTINATIONS, PEOPLE_DESTINATION, type Destination } from './navigation';

/**
 * The destinations both navigations draw: the six of the application — plus the readers,
 * for the side navigation only (`withPeople`) — and the administration last for an admin
 * (#496). Nothing is asked of the API while signed out, and the entry appears once
 * `/api/me` has answered — never for anyone else.
 */
export function useNavDestinations(withPeople = false): readonly Destination[] {
  const { authed } = useApiAuth();
  const { data: me } = useGetApiMe({ query: { enabled: authed } });
  return [
    ...NAV_DESTINATIONS,
    ...(withPeople ? [PEOPLE_DESTINATION] : []),
    ...(authed && me?.admin ? [ADMIN_DESTINATION] : []),
  ];
}

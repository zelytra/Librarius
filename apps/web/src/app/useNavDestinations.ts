import { useGetApiMe } from '../api/generated/librarius';
import { useApiAuth } from '../shared/api';
import { ADMIN_DESTINATION, NAV_DESTINATIONS, type Destination } from './navigation';

/**
 * The destinations both navigations draw: the six of the application, and the
 * administration after them for an admin (#496). Nothing is asked of the API while signed
 * out, and the entry appears once `/api/me` has answered — never for anyone else.
 */
export function useNavDestinations(): readonly Destination[] {
  const { authed } = useApiAuth();
  const { data: me } = useGetApiMe({ query: { enabled: authed } });
  return authed && me?.admin ? [...NAV_DESTINATIONS, ADMIN_DESTINATION] : NAV_DESTINATIONS;
}

import { useGetApiAdminPending, type AdminPendingDto } from '../../api/generated/librarius';

/** As often as the bell asks (#516). */
const POLL_MS = 60_000;

/**
 * What waits for the admins (#565, #635): unread tickets, catalog edits to review and open
 * catalog reports, and their sum — the Admin entry's badge and each tab's number. Asked for an
 * admin only; the API refuses everyone else.
 */
export function useAdminPending(admin: boolean): AdminPendingDto | undefined {
  const { data } = useGetApiAdminPending({
    query: { enabled: admin, refetchInterval: POLL_MS, refetchOnWindowFocus: true },
  });
  return admin ? data : undefined;
}

/** The sum, for the navigation's badge. */
export function useUnreadReports(admin: boolean): number {
  return useAdminPending(admin)?.total ?? 0;
}

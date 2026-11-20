import { useGetApiAdminFeedback } from '../../api/generated/librarius';

/** As often as the bell asks (#516). */
const POLL_MS = 60_000;

/**
 * The reports nobody has opened yet (#565), for the Admin entry's badge: a `NEW` page of one,
 * of which only the total is read. Asked for an admin only — the API refuses everyone else.
 */
export function useUnreadReports(admin: boolean): number {
  const { data } = useGetApiAdminFeedback(
    { status: 'NEW', size: 1 },
    { query: { enabled: admin, refetchInterval: POLL_MS, refetchOnWindowFocus: true } },
  );
  return admin ? (data?.total ?? 0) : 0;
}

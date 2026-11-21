import { lazy, Suspense } from 'react';
import { useGetApiMe } from '../../api/generated/librarius';
import { useApiAuth } from '../../shared/api';

const TermsGate = lazy(() => import('./TermsGate').then((m) => ({ default: m.TermsGate })));

/**
 * Asks a signed-in member to accept the terms (#74) when they have accepted none yet, or an
 * older version than the one in force. The version is the API's (`currentTermsVersion`), so
 * raising it there is all it takes to ask everyone again. The request itself is a separate
 * chunk: almost every visit has nothing to ask.
 */
export function TermsWatcher() {
  const { authed } = useApiAuth();
  const { data: me } = useGetApiMe({ query: { enabled: authed } });
  if (!authed || !me?.currentTermsVersion) return null;
  if ((me.termsVersion ?? 0) >= me.currentTermsVersion) return null;
  return (
    <Suspense fallback={null}>
      <TermsGate version={me.currentTermsVersion} again={me.termsVersion != null} />
    </Suspense>
  );
}

import { useEffect, useId, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getGetApiMeQueryKey, usePutApiMeTerms } from '../../api/generated/librarius';
import { useApiAuth } from '../../shared/api';
import { Button } from '../../shared/ui/primitives';
import { toast } from '../../shared/ui/toast';
import { LegalLinks } from './LegalLinks';
import styles from './Legal.module.css';

/**
 * The acceptance request (#74). It cannot be dismissed — the service is not used without the
 * terms — but it never traps anyone: the documents open on their own pages, and declining
 * signs the member out. What is recorded is the version shown, at the server's time.
 */
export function TermsGate({ version, again }: { version: number; again: boolean }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { signOutFully } = useApiAuth();
  const headingId = useId();
  const panel = useRef<HTMLDivElement>(null);
  const accept = usePutApiMeTerms({
    mutation: {
      onSuccess: (me) => {
        queryClient.setQueryData(getGetApiMeQueryKey(), me);
        toast.success(t('legal.gate.accepted'));
      },
    },
  });

  useEffect(() => panel.current?.focus(), []);

  return (
    <div className={styles.scrim}>
      <div ref={panel} role="dialog" aria-modal="true" aria-labelledby={headingId} tabIndex={-1} className={styles.panel}>
        <h2 id={headingId} className={styles.gateTitle}>
          {t('legal.gate.title')}
        </h2>
        <p className={styles.paragraph}>{t(again ? 'legal.gate.changed' : 'legal.gate.intro')}</p>
        <LegalLinks docs={['terms', 'privacy']} />
        {accept.isError && (
          <p role="alert" className={styles.failure}>
            {t('legal.gate.failed')}
          </p>
        )}
        <div className={styles.actions}>
          <Button onClick={() => accept.mutate({ data: { version } })} disabled={accept.isPending}>
            {t('legal.gate.accept')}
          </Button>
          <Button variant="ghost" onClick={signOutFully}>
            {t('legal.gate.decline')}
          </Button>
        </div>
      </div>
    </div>
  );
}

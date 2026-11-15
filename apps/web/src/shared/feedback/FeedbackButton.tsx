import { lazy, Suspense, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useApiAuth } from '../api';
import { Icon } from '../ui/Icon';
import styles from './FeedbackButton.module.css';

// Split off: every screen carries the button, few readers ever open the dialog.
const FeedbackDialog = lazy(() => import('./FeedbackDialog').then((m) => ({ default: m.FeedbackDialog })));

/**
 * The report button (#489): always at hand in the bottom-right corner for a signed-in member,
 * it opens a dialog to report a problem, suggest an idea or say anything else. The screen,
 * the app version, the browser and the recent log go with it unless the member unticks them —
 * what the admins need to reproduce a bug without asking.
 *
 * <p>`raised` lifts it above the bottom navigation bar on a phone.
 */
export function FeedbackButton({ raised = false }: { raised?: boolean }) {
  const { t } = useTranslation();
  const { authed } = useApiAuth();
  const [open, setOpen] = useState(false);
  if (!authed) return null;

  return (
    <>
      <button
        type="button"
        className={`${styles.trigger} ${raised ? styles.raised : ''}`}
        aria-label={t('feedback.open')}
        title={t('feedback.open')}
        onClick={() => setOpen(true)}
      >
        <Icon name="bug_report" size={22} color="var(--on-accent)" />
      </button>
      {open && (
        <Suspense fallback={null}>
          <FeedbackDialog onClose={() => setOpen(false)} />
        </Suspense>
      )}
    </>
  );
}

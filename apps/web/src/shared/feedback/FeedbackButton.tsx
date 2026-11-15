import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { usePostApiFeedback } from '../../api/generated/librarius';
import { useApiAuth } from '../api';
import { Icon } from '../ui/Icon';
import { Modal } from '../ui/Modal';
import { Button, Segmented } from '../ui/primitives';
import { Loading } from '../ui/states';
import { toast } from '../ui/toast';
import { context, recentLog } from './diagnostics';
import styles from './FeedbackButton.module.css';

type Kind = 'BUG' | 'IDEA' | 'OTHER';

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
      {open && <FeedbackDialog onClose={() => setOpen(false)} />}
    </>
  );
}

function FeedbackDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const descriptionId = useId();
  const [kind, setKind] = useState<Kind>('BUG');
  const [message, setMessage] = useState('');
  const [attach, setAttach] = useState(true);
  const [failed, setFailed] = useState(false);
  // Read once, when the dialog opens: what the member is reporting about.
  const [snapshot] = useState(() => ({ ...context(), logs: recentLog() }));
  const { mutate, isPending } = usePostApiFeedback({
    mutation: {
      onSuccess: () => {
        toast.success(t('feedback.sent'), 'check_circle');
        onClose();
      },
      onError: () => setFailed(true),
    },
  });

  const submit = () => {
    setFailed(false);
    mutate({
      data: {
        kind,
        message: message.trim(),
        page: snapshot.page,
        ...(attach && { appVersion: snapshot.appVersion, userAgent: snapshot.userAgent, logs: snapshot.logs || undefined }),
      },
    });
  };

  return (
    <Modal title={t('feedback.open')} description={t('feedback.intro')} onClose={onClose}>
      <div className={styles.form}>
        <Segmented<Kind>
          value={kind}
          onChange={setKind}
          options={[
            { id: 'BUG', label: t('feedback.kinds.BUG') },
            { id: 'IDEA', label: t('feedback.kinds.IDEA') },
            { id: 'OTHER', label: t('feedback.kinds.OTHER') },
          ]}
        />
        <label htmlFor={descriptionId} className={styles.label}>
          {t('feedback.description')}
        </label>
        <textarea
          id={descriptionId}
          value={message}
          rows={5}
          maxLength={5000}
          placeholder={t(`feedback.placeholders.${kind}`)}
          className={styles.text}
          onChange={(event) => setMessage(event.target.value)}
        />
        <label className={styles.attach}>
          <input type="checkbox" checked={attach} onChange={(event) => setAttach(event.target.checked)} />
          {t('feedback.attach')}
        </label>
        {attach && (
          <details className={styles.details}>
            <summary>{t('feedback.preview')}</summary>
            <pre className={styles.log}>
              {[snapshot.page, snapshot.appVersion, snapshot.userAgent, snapshot.logs].filter(Boolean).join('\n')}
            </pre>
          </details>
        )}
        {failed && (
          <p role="alert" className={styles.error}>
            {t('feedback.error')}
          </p>
        )}
        <Button variant="primary" size="block" disabled={!message.trim() || isPending} onClick={submit}>
          {t('feedback.send')}
          <Loading size="compact" pending={isPending} />
        </Button>
      </div>
    </Modal>
  );
}

import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { usePostApiFeedback } from '../../api/generated/librarius';
import { Modal } from '../ui/Modal';
import { Button, Segmented } from '../ui/primitives';
import { Loading } from '../ui/states';
import { toast } from '../ui/toast';
import { context, recentLog } from './diagnostics';
import styles from './FeedbackButton.module.css';

type Kind = 'BUG' | 'IDEA' | 'OTHER';

/** The report dialog (#489), loaded the first time the button is pressed. */
export function FeedbackDialog({ onClose }: { onClose: () => void }) {
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

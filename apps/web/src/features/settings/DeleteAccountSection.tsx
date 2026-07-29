import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useMutation } from '@tanstack/react-query';
import { Button } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import { ApiError } from '../../shared/apiClient';
import { useApiAuth } from '../../shared/api';
import { useDeleteApiMe, useGetApiMe } from '../../api/generated/librarius';
import { requestExport, saveExport } from './exportRequest';
import styles from './DeleteAccountSection.module.css';

/** The API reports a refused deletion through the message of its 503. */
function failureMessage(t: TFunction, error: unknown): string {
  if (error instanceof ApiError) {
    try {
      const parsed = JSON.parse(String(error.body)) as { message?: string };
      if (parsed.message) return parsed.message;
    } catch {
      // Not a JSON payload: fall through to the status.
    }
    return t('common.errorWithStatus', { status: error.status });
  }
  return t('settings.deleteAccount.failed');
}

/**
 * Deletes the account and everything in it (GDPR art. 17).
 *
 * Three things stand between a stray tap and an irreversible action: the section is
 * collapsed until asked for, the user has to type their own name, and the export is offered
 * right there — a user who is leaving should not have to find the button two sections up.
 */
export function DeleteAccountSection() {
  const { t } = useTranslation();
  const auth = useApiAuth();
  const { data: me } = useGetApiMe({ query: { enabled: auth.authed } });
  const [open, setOpen] = useState(false);
  const [typed, setTyped] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [deleted, setDeleted] = useState(false);

  const username = me?.displayName ?? '';
  const confirmed = username !== '' && typed.trim() === username;

  const { mutate: exportFirst, isPending: exporting } = useMutation({
    mutationFn: () => requestExport('json'),
    onSuccess: saveExport,
    onError: (e: unknown) => setError(failureMessage(t, e)),
  });

  const { mutate: deleteAccount, isPending: deleting } = useDeleteApiMe({
    mutation: {
      onSuccess: () => {
        setDeleted(true);
        // The Keycloak account no longer exists, but the token in memory is still valid
        // until it expires: drop the local session rather than wait it out.
        auth.signOut();
      },
      onError: (e: unknown) => setError(failureMessage(t, e)),
    },
  });

  // Rendered before the sign-in gate below: the session has just been dropped on purpose,
  // and the user must still be told that it worked.
  if (deleted) {
    return (
      <>
        <h3 className={styles.title}>{t('settings.deleteAccount.title')}</h3>
        <p className={styles.done}>{t('settings.deleteAccount.done')}</p>
      </>
    );
  }

  if (!auth.authed) {
    return null;
  }

  return (
    <>
      <h3 className={styles.title}>{t('settings.deleteAccount.title')}</h3>
      <p className={styles.intro}>{t('settings.deleteAccount.description')}</p>

      {!open ? (
        <button className={styles.reveal} onClick={() => setOpen(true)}>
          <Icon name="delete_forever" size={18} color="var(--rose)" />
          {t('settings.deleteAccount.start')}
        </button>
      ) : (
        <div className={styles.panel}>
          <p className={styles.warning}>{t('settings.deleteAccount.warning')}</p>
          <p className={styles.detail}>{t('settings.deleteAccount.kept')}</p>
          <p className={styles.detail}>{t('settings.deleteAccount.delay')}</p>

          <button
            className={styles.exportFirst}
            onClick={() => exportFirst()}
            disabled={exporting || deleting}
          >
            <Icon name="download" size={16} color="var(--accent-deep)" />
            {t(exporting ? 'common.working' : 'settings.deleteAccount.exportFirst')}
          </button>

          <label className={styles.label} htmlFor="delete-account-confirm">
            {t('settings.deleteAccount.confirmLabel', { username })}
          </label>
          <input
            id="delete-account-confirm"
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            autoComplete="off"
            placeholder={username}
            className={styles.input}
          />

          <div className={styles.buttons}>
            <Button
              variant="secondary"
              size="compact"
              onClick={() => {
                setOpen(false);
                setTyped('');
                setError(null);
              }}
              disabled={deleting}
            >
              {t('settings.deleteAccount.cancel')}
            </Button>
            <Button
              variant="primary"
              size="compact"
              className={styles.confirmButton}
              disabled={!confirmed || deleting}
              onClick={() => deleteAccount()}
            >
              {t(deleting ? 'common.working' : 'settings.deleteAccount.confirm')}
            </Button>
          </div>

          {error && <p className={styles.failure}>{error}</p>}
        </div>
      )}
    </>
  );
}

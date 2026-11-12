import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import { useDeleteApiMeLibrary } from '../../api/generated/librarius';
import { requestExport, saveExport } from './exportRequest';
import styles from './DeleteAccountSection.module.css';

/**
 * Empties the library and keeps the account (#382): titles, readings, wishes, goals, own
 * categories, followed series and authors — and every statistic drawn from them.
 *
 * Guarded like the account deletion: collapsed until asked for, the export offered right
 * there, and a word to type before the button wakes up.
 */
export function ResetLibrarySection() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [typed, setTyped] = useState('');
  const [failed, setFailed] = useState(false);
  const [done, setDone] = useState(false);

  const word = t('settings.resetLibrary.word');
  const confirmed = typed.trim().toLocaleUpperCase('fr') === word.toLocaleUpperCase('fr');

  const { mutate: exportFirst, isPending: exporting } = useMutation({
    mutationFn: () => requestExport('json'),
    onSuccess: saveExport,
    onError: () => setFailed(true),
  });

  const { mutate: reset, isPending: resetting } = useDeleteApiMeLibrary({
    mutation: {
      onSuccess: () => {
        setDone(true);
        setOpen(false);
        setTyped('');
        // Every screen reads the library, the stats or the wishes: none of it is true any more.
        void queryClient.invalidateQueries();
      },
      onError: () => setFailed(true),
    },
  });

  return (
    <>
      <h3 className={styles.title}>{t('settings.resetLibrary.title')}</h3>
      <p className={styles.intro}>{t('settings.resetLibrary.description')}</p>
      {done && <p className={styles.done}>{t('settings.resetLibrary.done')}</p>}

      {!open ? (
        <button className={styles.reveal} onClick={() => { setOpen(true); setDone(false); }}>
          <Icon name="delete_forever" size={18} color="var(--rose)" />
          {t('settings.resetLibrary.start')}
        </button>
      ) : (
        <div className={styles.panel}>
          <p className={styles.warning}>{t('settings.resetLibrary.warning')}</p>
          <p className={styles.detail}>{t('settings.resetLibrary.kept')}</p>

          <button
            className={styles.exportFirst}
            onClick={() => exportFirst()}
            disabled={exporting || resetting}
          >
            <Icon name="download" size={16} color="var(--accent-deep)" />
            {t(exporting ? 'common.working' : 'settings.resetLibrary.exportFirst')}
          </button>

          <label className={styles.label} htmlFor="reset-library-confirm">
            {t('settings.resetLibrary.confirmLabel', { word })}
          </label>
          <input
            id="reset-library-confirm"
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            autoComplete="off"
            placeholder={word}
            className={styles.input}
          />

          <div className={styles.buttons}>
            <Button
              variant="secondary"
              size="compact"
              onClick={() => {
                setOpen(false);
                setTyped('');
                setFailed(false);
              }}
              disabled={resetting}
            >
              {t('settings.resetLibrary.cancel')}
            </Button>
            <Button
              variant="primary"
              size="compact"
              className={styles.confirmButton}
              disabled={!confirmed || resetting}
              onClick={() => {
                setFailed(false);
                reset();
              }}
            >
              {t(resetting ? 'common.working' : 'settings.resetLibrary.confirm')}
            </Button>
          </div>

          {failed && <p className={styles.failure}>{t('settings.resetLibrary.failed')}</p>}
        </div>
      )}
    </>
  );
}

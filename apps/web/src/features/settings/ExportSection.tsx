import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useMutation } from '@tanstack/react-query';
import { Button } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import { ApiError } from '../../shared/apiClient';
import { useApiAuth } from '../../shared/api';
import { requestExport, saveExport, type ExportFormat } from './exportRequest';
import styles from './ExportSection.module.css';

function failureMessage(t: TFunction, error: unknown): string {
  if (error instanceof ApiError) {
    return t('common.errorWithStatus', { status: error.status });
  }
  return t('settings.export.failed');
}

/**
 * Gets the user's data out (GDPR art. 20).
 *
 * Two formats because they answer two different questions: the JSON archive is complete and
 * can be re-imported here, the CSV opens in a spreadsheet and carries the column names the
 * other reading trackers understand.
 */
export function ExportSection() {
  const { t } = useTranslation();
  const auth = useApiAuth();
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<ExportFormat | null>(null);

  const { mutate: download, isPending, variables } = useMutation({
    mutationFn: (format: ExportFormat) => requestExport(format),
    onMutate: (format: ExportFormat) => {
      setError(null);
      setDone(null);
      return format;
    },
    onSuccess: (file, format) => {
      saveExport(file);
      setDone(format);
    },
    onError: (e: unknown) => setError(failureMessage(t, e)),
  });

  return (
    <>
      <h3 className={styles.title}>{t('settings.export.title')}</h3>
      <p className={styles.intro}>{t('settings.export.description')}</p>

      {!auth.authed ? (
        <Button variant="secondary" onClick={auth.login}>
          <Icon name="login" size={18} color="var(--ink-soft)" />
          {t('settings.export.signIn')}
        </Button>
      ) : (
        <div className={styles.actions}>
          <div className={styles.buttons}>
            <Button
              variant="secondary"
              size="compact"
              disabled={isPending}
              onClick={() => download('json')}
            >
              <Icon name="download" size={16} color="var(--ink-soft)" />
              {t(isPending && variables === 'json' ? 'common.working' : 'settings.export.json')}
            </Button>
            <Button
              variant="secondary"
              size="compact"
              disabled={isPending}
              onClick={() => download('csv')}
            >
              <Icon name="table_view" size={16} color="var(--ink-soft)" />
              {t(isPending && variables === 'csv' ? 'common.working' : 'settings.export.csv')}
            </Button>
          </div>
          <p className={styles.hint}>{t('settings.export.hint')}</p>
          {done && <p className={styles.success}>{t('settings.export.done')}</p>}
          {error && <p className={styles.failure}>{error}</p>}
        </div>
      )}
    </>
  );
}

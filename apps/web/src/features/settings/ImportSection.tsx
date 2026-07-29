import { useRef, useState, type ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Segmented } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import { ApiError } from '../../shared/apiClient';
import { useApiAuth } from '../../shared/api';
import {
  getGetApiLibraryQueryKey,
  getGetApiStatsQueryKey,
  usePostApiImportCsv,
  usePostApiImportSource,
  type ImportResult,
} from '../../api/generated/librarius';
import styles from './ImportSection.module.css';

type Source = 'booknode' | 'babelio';

/**
 * Booknode publishes its members' libraries, so one can be fetched from a handle alone.
 * Babelio does not: it has no API, and a member's shelves need a session, which is why
 * `BabelioImporter` refuses every handle. The source stays offered — that is where a
 * reader coming from Babelio looks — but it leads to the CSV export instead of to a
 * request that could only fail.
 */
function importsByHandle(source: Source): boolean {
  return source !== 'babelio';
}

function resultMessage(t: TFunction, r: ImportResult): string {
  return t('settings.import.result', { imported: r.imported ?? 0, skipped: r.skipped ?? 0 });
}

/** The API reports import problems through the message of a 400 response. */
function failureMessage(t: TFunction, error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    try {
      const parsed = JSON.parse(String(error.body)) as { message?: string };
      if (parsed.message) return parsed.message;
    } catch {
      // Not a JSON payload: fall through to the status.
    }
    return t('common.errorWithStatus', { status: error.status });
  }
  return fallback;
}

export function ImportSection() {
  const { t } = useTranslation();
  const auth = useApiAuth();
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [source, setSource] = useState<Source>('booknode');
  const [handle, setHandle] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { mutateAsync: importFromSource, isPending: scraping } = usePostApiImportSource();
  const { mutateAsync: importCsv, isPending: uploading } = usePostApiImportCsv();
  const busy = scraping || uploading;

  function reset() {
    setMessage(null);
    setError(null);
  }

  /** An import adds titles: the collection and the counters must be refreshed. */
  function refreshLibrary() {
    void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
    void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
  }

  async function runScrape() {
    if (!handle.trim()) return;
    reset();
    try {
      const result = await importFromSource({ source, data: { handle: handle.trim() } });
      setMessage(resultMessage(t, result));
      refreshLibrary();
    } catch (e) {
      setError(failureMessage(t, e, t('settings.import.unavailable')));
    }
  }

  async function onFile(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    reset();
    try {
      const text = await file.text();
      const result = await importCsv({ data: text });
      setMessage(resultMessage(t, result));
      refreshLibrary();
    } catch (err) {
      setError(failureMessage(t, err, t('settings.import.unreadableFile')));
    } finally {
      if (fileInput.current) fileInput.current.value = '';
    }
  }

  return (
    <>
      <h3 className={styles.title}>{t('settings.import.title')}</h3>
      <p className={styles.intro}>{t('settings.import.description')}</p>

      {!auth.authed ? (
        <Button variant="secondary" onClick={auth.login}>
          <Icon name="login" size={18} color="var(--ink-soft)" />
          {t('settings.import.signIn')}
        </Button>
      ) : (
        <div className={styles.form}>
          <Segmented<Source>
            value={source}
            onChange={(s) => {
              setSource(s);
              reset();
            }}
            options={[
              { id: 'booknode', label: t('settings.import.sources.booknode') },
              { id: 'babelio', label: t('settings.import.sources.babelio') },
            ]}
          />
          {importsByHandle(source) ? (
            <div className={styles.handleRow}>
              <input
                value={handle}
                onChange={(e) => setHandle(e.target.value)}
                placeholder={t(`settings.import.handlePlaceholder.${source}`)}
                aria-label={t(`settings.import.handlePlaceholder.${source}`)}
                className={styles.handleInput}
              />
              <Button variant="primary" size="compact" onClick={() => void runScrape()} disabled={busy}>
                {t(busy ? 'common.working' : 'settings.import.submit')}
              </Button>
            </div>
          ) : (
            <p className={styles.note}>{t('settings.import.babelioNote')}</p>
          )}

          <button onClick={() => fileInput.current?.click()} disabled={busy} className={styles.fileButton}>
            <Icon name="upload_file" size={18} color="var(--accent-deep)" />
            {t('settings.import.csv')}
          </button>
          <input
            ref={fileInput}
            type="file"
            accept=".csv,text/csv,text/plain"
            onChange={onFile}
            className={styles.hiddenInput}
          />

          {message && <p className={styles.success}>{message}</p>}
          {error && <p className={styles.failure}>{error}</p>}
        </div>
      )}
    </>
  );
}

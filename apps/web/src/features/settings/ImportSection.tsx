import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Segmented } from '../../shared/ui/primitives';
import { Loading } from '../../shared/ui/states';
import { Icon } from '../../shared/ui/Icon';
import { ApiError } from '../../shared/apiClient';
import { useApiAuth } from '../../shared/api';
import {
  getGetApiLibraryQueryKey,
  getGetApiStatsQueryKey,
  useGetApiImportJobsJobId,
  usePostApiImportCsv,
  usePostApiImportSource,
} from '../../api/generated/librarius';
import styles from './ImportSection.module.css';

type Source = 'booknode' | 'babelio';

/** How often a running import is polled — brisk enough to feel live, gentle on the server. */
const POLL_MS = 1500;

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
  // The import runs in the background; the screen follows a job identifier instead of holding
  // the request open. `message`/`error` carry the terminal outcome once it stops.
  const [jobId, setJobId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { mutateAsync: importFromSource, isPending: startingScrape } = usePostApiImportSource();
  const { mutateAsync: importCsv, isPending: startingCsv } = usePostApiImportCsv();

  // Follows the running import, stopping the poll the moment it is no longer RUNNING.
  const { data: job } = useGetApiImportJobsJobId(jobId ?? '', {
    query: {
      enabled: jobId != null,
      refetchInterval: (query) => (query.state.data?.status === 'RUNNING' ? POLL_MS : false),
    },
  });

  useEffect(() => {
    if (!job || job.status === 'RUNNING') return;
    if (job.status === 'DONE') {
      setMessage(t('settings.import.result', { imported: job.imported ?? 0, skipped: job.skipped ?? 0 }));
      // The titles are in: the collection and the counters must be refreshed.
      void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
      void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
    } else {
      setError(job.error ?? t('settings.import.unavailable'));
    }
    setJobId(null); // Stop polling; the job has finished.
  }, [job, queryClient, t]);

  const running = jobId != null;
  const busy = running || startingScrape || startingCsv;

  function reset() {
    setMessage(null);
    setError(null);
  }

  async function runScrape() {
    if (!handle.trim()) return;
    reset();
    try {
      const started = await importFromSource({ source, data: { handle: handle.trim() } });
      if (started.id) setJobId(started.id);
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
      const started = await importCsv({ data: text });
      if (started.id) setJobId(started.id);
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
                {t('settings.import.submit')}
                {/* Either import feeds it: a CSV upload disables the same controls. */}
                <Loading size="compact" pending={busy} />
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

          {/* A large library takes a while: the screen says it is working, and counts up as
              titles land, rather than looking frozen behind one long request. */}
          {running && (
            <p className={styles.progress} role="status">
              {t('settings.import.running', { imported: job?.imported ?? 0 })}
            </p>
          )}
          {!running && message && <p className={styles.success}>{message}</p>}
          {!running && error && <p className={styles.failure}>{error}</p>}
        </div>
      )}
    </>
  );
}

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
  getGetApiWishlistQueryKey,
  useGetApiImportJobsJobId,
  usePostApiImportCollected,
  usePostApiImportCsv,
  usePostApiImportSource,
} from '../../api/generated/librarius';
import type { ImportJobDto } from '../../api/generated/librarius';
import { bookmarkletHref, isCollectedFile } from './collector';
import { isMangacollecCollection } from './mangacollec';
import styles from './ImportSection.module.css';
import { STATS_QUERIES } from '../../shared/statsQueries';

type Source = 'booknode' | 'babelio' | 'mangacollec';

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

/** Reads a file as JSON when it is one — Mangacollec's collection is saved with no extension. */
function asJson(text: string): unknown {
  if (!text.trimStart().startsWith('{')) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
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
  const { mutateAsync: importCollected, isPending: startingCollected } = usePostApiImportCollected();
  const [copied, setCopied] = useState(false);

  // The collector's link. React refuses a `javascript:` URL in `href` — rightly, for anything
  // built from data — so this one, a constant of our own code, is set on the element directly.
  const bookmarklet = useRef<HTMLAnchorElement>(null);
  useEffect(() => {
    bookmarklet.current?.setAttribute('href', bookmarkletHref());
  });

  async function copyBookmarklet() {
    try {
      await navigator.clipboard.writeText(bookmarkletHref());
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

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
      void queryClient.invalidateQueries(STATS_QUERIES);
      // A Booknode import brings the reader's wants along, onto the wishlist.
      void queryClient.invalidateQueries({ queryKey: getGetApiWishlistQueryKey() });
    } else {
      setError(job.error ?? t('settings.import.unavailable'));
    }
    setJobId(null); // Stop polling; the job has finished.
  }, [job, queryClient, t]);

  const running = jobId != null;
  const busy = running || startingScrape || startingCsv || startingCollected;

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

  /**
   * One picker for every file a reader may hold: the collector's `.json`, pages of their library
   * saved from the browser (`.html`, several at once), or a CSV export.
   */
  async function onFile(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    if (files.length === 0) return;
    reset();
    try {
      const first = files[0];
      const name = first.name.toLowerCase();
      const text = await first.text();
      const json = asJson(text);
      let started;
      if (isMangacollecCollection(json)) {
        // Sent as the site wrote it, as the one page of a collected file: the API reads
        // Mangacollec's own document (#448, #456).
        started = await importCollected({ data: { source: 'mangacollec', pages: [text] } });
      } else if (json !== undefined || name.endsWith('.json')) {
        const parsed: unknown = json ?? JSON.parse(text);
        if (!isCollectedFile(parsed)) {
          setError(t('settings.import.collector.notCollected'));
          return;
        }
        started = await importCollected({ data: { source: parsed.source, pages: parsed.pages } });
      } else if (name.endsWith('.html') || name.endsWith('.htm')) {
        const pages = await Promise.all(files.map((f) => f.text()));
        started = await importCollected({ data: { source, pages } });
      } else {
        started = await importCsv({ data: text });
      }
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
              { id: 'mangacollec', label: t('settings.import.sources.mangacollec') },
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
          ) : source === 'babelio' ? (
            <p className={styles.note}>{t('settings.import.babelioNote')}</p>
          ) : null}

          {/* The path that works whatever the site does to servers: the reader's own browser,
              already signed in, collects the library and hands back a file. */}
          <div className={styles.collector}>
            <h4 className={styles.collectorTitle}>{t('settings.import.collector.title')}</h4>
            <ol className={styles.steps}>
              <li>
                {t('settings.import.collector.step1')}{' '}
                <a
                  ref={bookmarklet}
                  className={styles.bookmarklet}
                  onClick={(e) => e.preventDefault()}
                  draggable
                >
                  <Icon name="bookmark_add" size={16} color="var(--on-accent)" />
                  {t('settings.import.collector.button')}
                </a>
              </li>
              <li>{t(`settings.import.collector.step2.${source}`)}</li>
              <li>{t('settings.import.collector.step3')}</li>
            </ol>
            <button type="button" onClick={() => void copyBookmarklet()} className={styles.copyButton}>
              <Icon name="bookmark_added" size={15} color="var(--accent-deep)" />
              {t(copied ? 'settings.import.collector.copied' : 'settings.import.collector.copy')}
            </button>
          </div>

          <button onClick={() => fileInput.current?.click()} disabled={busy} className={styles.fileButton}>
            <Icon name="upload_file" size={18} color="var(--accent-deep)" />
            {t('settings.import.file')}
          </button>
          <input
            ref={fileInput}
            type="file"
            multiple
            // Mangacollec's collection is saved with no extension: no filter for it.
            accept={source === 'mangacollec' ? undefined : '.json,.csv,.html,.htm,application/json,text/csv,text/plain,text/html'}
            onChange={onFile}
            aria-label={t('settings.import.file')}
            className={styles.hiddenInput}
          />

          {/* A large library takes a while: the screen says it is working, and counts up as
              titles land, rather than looking frozen behind one long request. */}
          {running && <ImportProgress job={job} />}
          {!running && message && (
            <div className={styles.outcomeSuccess} role="status">
              <Icon name="check_circle" size={26} color="var(--accent-deep)" />
              <div>
                <p className={styles.outcomeTitle}>{t('settings.import.doneTitle')}</p>
                <p className={styles.outcomeText}>{message}</p>
              </div>
            </div>
          )}
          {!running && error && (
            <div className={styles.outcomeFailure} role="alert">
              <Icon name="priority_high" size={26} color="var(--tint-rose-ink)" />
              <div>
                <p className={styles.outcomeTitle}>{t('settings.import.failedTitle')}</p>
                <p className={styles.outcomeText}>{error}</p>
              </div>
            </div>
          )}
        </div>
      )}
    </>
  );
}

/**
 * The import under way, in a card of its own: a large percentage and a bar that moves batch
 * by batch. A line of grey text at the foot of the section was missed — readers launched a
 * two-thousand-title import, saw nothing happen, and ran it again. It scrolls itself into view
 * when it appears.
 */
function ImportProgress({ job }: { job: ImportJobDto | undefined }) {
  const { t } = useTranslation();
  const card = useRef<HTMLDivElement>(null);
  useEffect(() => {
    card.current?.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
  }, []);

  const total = job?.total ?? 0;
  const imported = job?.imported ?? 0;
  const done = imported + (job?.skipped ?? 0);
  // Before the first batch the file is still being read: no total yet, a bar that waits.
  const percent = total > 0 ? Math.min(100, Math.round((done * 100) / total)) : null;

  return (
    <div ref={card} className={styles.progressCard} role="status" aria-live="polite">
      <div className={styles.progressHead}>
        <span className={styles.progressTitle}>{t('settings.import.runningTitle')}</span>
        {percent != null && (
          <span className={styles.progressPercent}>{t('settings.import.percent', { percent })}</span>
        )}
      </div>
      <div
        className={styles.progressTrack}
        role="progressbar"
        aria-label={t('settings.import.runningTitle')}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent ?? undefined}
      >
        <div
          className={percent == null ? styles.progressWaiting : styles.progressBar}
          style={percent == null ? undefined : { width: `${percent}%` }}
        />
      </div>
      <p className={styles.progressDetail}>
        {percent == null
          ? t('settings.import.reading')
          : t('settings.import.progressDetail', { done, total, imported })}
      </p>
    </div>
  );
}


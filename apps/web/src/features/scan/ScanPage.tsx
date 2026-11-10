import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  deleteApiLibraryId,
  deleteApiWishlistId,
  getGetApiLibraryQueryKey,
  getApiScanIsbn,
  getGetApiWishlistQueryKey,
  postApiLibrary,
  postApiWishlist,
  type CatalogResult,
  type ScanResultDto,
} from '../../api/generated/librarius';
import { STATS_QUERIES } from '../../shared/statsQueries';
import { ApiError } from '../../shared/apiClient';
import { Button, Screen, Segmented } from '../../shared/ui/primitives';
import { Cover } from '../../shared/ui/Cover';
import { Icon } from '../../shared/ui/Icon';
import { LoginGate } from '../../shared/LoginGate';
import { detectIsbn, toIsbn13 } from '../discover/isbn';
import { catalogPath, toBook } from '../catalog/catalogBook';
import { cameraScanSupported, failureKind, startScanning, type CameraFailure } from './camera';
import styles from './ScanPage.module.css';

/** One scanned book and what it turned out to be, newest first on screen. */
interface Entry {
  isbn: string;
  result?: ScanResultDto;
  error?: string;
  /** Added by the session on its own: where, and the row to remove to undo it. */
  added?: { target: SessionTarget; id?: string; undone?: boolean };
  addFailed?: boolean;
}

/**
 * Where a scanning session files the new titles it recognises (#168): nowhere — it only
 * checks, the default — or straight into one state, with no confirmation per book, so a
 * shelf of twenty volumes goes through in one motion.
 */
type SessionTarget = 'CHECK' | 'OWNED' | 'READ' | 'READING' | 'WISHLIST';
const SESSION_TARGETS: SessionTarget[] = ['CHECK', 'OWNED', 'READ', 'READING', 'WISHLIST'];

/**
 * Scanning in a bookshop (#68): point the camera at the back cover and know within two
 * seconds whether the book is already at home, on the wishlist, or new — and add it in one
 * tap if it is. The camera keeps reading, so a whole stack goes through without a tap between
 * books; the ISBN can always be typed instead.
 */
export function ScanPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <Screen>
      <div className={styles.header}>
        <button onClick={() => navigate(-1)} aria-label={t('common.back')} className={styles.backButton}>
          <Icon name="arrow_back" size={24} color="var(--ink)" />
        </button>
        <h2 className={styles.title}>{t('scan.title')}</h2>
      </div>
      <LoginGate prompt={t('scan.prompt')}>
        <Scanner />
      </LoginGate>
    </Screen>
  );
}

function Scanner() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [entries, setEntries] = useState<Entry[]>([]);
  const [target, setTarget] = useState<SessionTarget>('CHECK');
  // Read by the camera's callback, which outlives a render.
  const targetRef = useRef<SessionTarget>('CHECK');
  targetRef.current = target;
  const seen = useRef(new Set<string>());

  const update = useCallback((key: string, patch: Partial<Entry>) => {
    setEntries((list) => list.map((e) => (e.isbn === key ? { ...e, ...patch } : e)));
  }, []);

  const refresh = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
    void queryClient.invalidateQueries({ queryKey: getGetApiWishlistQueryKey() });
    void queryClient.invalidateQueries(STATS_QUERIES);
  }, [queryClient]);

  /** Files a new title where the session says, without asking. */
  const autoAdd = useCallback(
    async (isbn: string, found: CatalogResult, session: Exclude<SessionTarget, 'CHECK'>) => {
      const book = toBook(found);
      try {
        const created = (session === 'WISHLIST'
          ? await postApiWishlist({ book, priority: 'SOON' })
          : await postApiLibrary({ book, status: session })) as { id?: string } | undefined;
        update(isbn, { added: { target: session, id: created?.id } });
        refresh();
      } catch {
        update(isbn, { addFailed: true });
      }
    },
    [update, refresh],
  );

  /** Looks a code up once: the camera reads the same barcode several times a second. */
  const lookUp = useCallback(
    (raw: string) => {
      const detected = detectIsbn(raw);
      if (!detected) return false;
      // One key per book, whichever of its two numbers was read.
      const isbn = toIsbn13(detected);
      if (seen.current.has(isbn)) return true;
      seen.current.add(isbn);
      navigator.vibrate?.(40);
      const session = targetRef.current;
      setEntries((list) => [{ isbn }, ...list]);
      getApiScanIsbn(isbn)
        .then((result) => {
          update(isbn, { result });
          // Only a title the reader holds nowhere yet: an owned or wished one is reported,
          // never added a second time, even mid-session.
          if (session !== 'CHECK' && !result.owned && !result.wished && result.catalog) {
            // Narrowed for the call: a checking session never adds anything.
            void autoAdd(isbn, result.catalog, session);
          }
        })
        .catch((error: unknown) => update(isbn, { error: failureMessage(t, error) }));
      return true;
    },
    [t, update, autoAdd],
  );

  async function undo(entry: Entry) {
    const id = entry.added?.id;
    if (!id) return;
    try {
      if (entry.added?.target === 'WISHLIST') await deleteApiWishlistId(id);
      else await deleteApiLibraryId(id);
      update(entry.isbn, { added: { ...entry.added!, undone: true } });
      refresh();
    } catch {
      update(entry.isbn, { addFailed: true });
    }
  }

  const addedCount = entries.filter((e) => e.added && !e.added.undone).length;

  return (
    <div className={styles.layout}>
      <div className={styles.inputs}>
        <div className={styles.session}>
          <span className={styles.sessionLabel}>{t('scan.session.label')}</span>
          <Segmented<SessionTarget>
            options={SESSION_TARGETS.map((id) => ({ id, label: t(`scan.session.targets.${id}`) }))}
            value={target}
            onChange={setTarget}
          />
        </div>
        <Camera onCode={lookUp} />
        <ManualEntry onSubmit={lookUp} />
      </div>
      <section aria-live="polite" className={styles.results}>
        {addedCount > 0 && (
          <p className={styles.sessionCount} role="status">
            {t('scan.session.count', { count: addedCount })}
          </p>
        )}
        {entries.length === 0 ? (
          <p className={styles.empty}>{t('scan.empty')}</p>
        ) : (
          <ul className={styles.list}>
            {entries.map((entry) => (
              <ScanEntry key={entry.isbn} entry={entry} onUndo={() => void undo(entry)} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function Camera({ onCode }: { onCode: (code: string) => boolean }) {
  const { t } = useTranslation();
  const video = useRef<HTMLVideoElement>(null);
  const stop = useRef<(() => void) | null>(null);
  const [state, setState] = useState<'off' | 'starting' | 'on' | CameraFailure>(
    cameraScanSupported() ? 'off' : 'unsupported',
  );

  useEffect(() => () => stop.current?.(), []);

  async function start() {
    if (!video.current) return;
    setState('starting');
    try {
      stop.current = await startScanning(video.current, (code) => {
        onCode(code);
      });
      setState('on');
    } catch (error) {
      setState(failureKind(error));
    }
  }

  function turnOff() {
    stop.current?.();
    stop.current = null;
    setState('off');
  }

  const running = state === 'on' || state === 'starting';
  return (
    <div className={styles.camera}>
      <div className={running ? styles.viewfinder : styles.viewfinderOff}>
        <video ref={video} className={styles.video} aria-label={t('scan.camera.label')} />
        {running && <div className={styles.aim} aria-hidden="true" />}
        {!running && (
          <div className={styles.cameraIdle}>
            <Icon name="barcode_scanner" size={40} color="var(--muted)" />
            {state === 'off' && (
              <Button variant="primary" onClick={start}>
                {t('scan.camera.start')}
              </Button>
            )}
            {state !== 'off' && (
              <p role="alert" className={styles.cameraMessage}>
                {t(`scan.camera.${state}`)}
              </p>
            )}
            {(state === 'denied' || state === 'unavailable') && (
              <Button variant="secondary" size="compact" onClick={start}>
                {t('scan.camera.retry')}
              </Button>
            )}
          </div>
        )}
      </div>
      {running && (
        <div className={styles.cameraBar}>
          <span className={styles.hint}>{t('scan.camera.hint')}</span>
          <Button variant="secondary" size="compact" onClick={turnOff}>
            {t('scan.camera.stop')}
          </Button>
        </div>
      )}
    </div>
  );
}

function ManualEntry({ onSubmit }: { onSubmit: (code: string) => boolean }) {
  const { t } = useTranslation();
  const [value, setValue] = useState('');
  const [invalid, setInvalid] = useState(false);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (onSubmit(value)) {
      setValue('');
      setInvalid(false);
    } else {
      setInvalid(true);
    }
  }

  return (
    <form className={styles.manual} onSubmit={submit}>
      <label className={styles.manualLabel} htmlFor="scan-isbn">
        {t('scan.manual.label')}
      </label>
      <div className={styles.manualRow}>
        <input
          id="scan-isbn"
          inputMode="numeric"
          autoComplete="off"
          className={styles.manualInput}
          placeholder={t('scan.manual.placeholder')}
          value={value}
          aria-invalid={invalid}
          onChange={(e) => {
            setValue(e.target.value);
            setInvalid(false);
          }}
        />
        <Button type="submit" variant="primary" size="compact" disabled={!value.trim()}>
          {t('scan.manual.submit')}
        </Button>
      </div>
      {invalid && (
        <p role="alert" className={styles.manualError}>
          {t('scan.manual.invalid')}
        </p>
      )}
    </form>
  );
}

function ScanEntry({ entry, onUndo }: { entry: Entry; onUndo: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { result, error } = entry;

  if (!result) {
    return (
      <li className={styles.entry}>
        <div className={styles.entryBody}>
          <span className={styles.isbn}>{entry.isbn}</span>
          <span className={error ? styles.verdictError : styles.pending}>{error ?? t('scan.pending')}</span>
        </div>
      </li>
    );
  }

  const owned = result.owned;
  const wished = result.wished;
  const book = owned?.book ?? wished?.book;
  const title = book?.title ?? result.catalog?.title ?? entry.isbn;
  const authors = book?.authors ?? result.catalog?.authors;
  const cover = book?.coverUrl ?? result.catalog?.coverUrl;
  const likely = result.match === 'TITLE' || result.match === 'SERIES_VOLUME';

  let verdict: string;
  let tone: string;
  let action: { label: string; go: () => void } | null = null;
  if (owned) {
    verdict = t(`scan.verdict.owned.${owned.status ?? 'OWNED'}`);
    tone = styles.verdictOwned;
    action = { label: t('scan.action.open'), go: () => navigate(`/detail/${owned.id}`) };
  } else if (wished) {
    verdict = t('scan.verdict.wished');
    tone = styles.verdictWished;
    action = { label: t('scan.action.wishlist'), go: () => navigate('/wishlist') };
  } else if (entry.added && !entry.added.undone) {
    // Filed by the session: what it became, and the way back from a mis-scan.
    verdict = t(`scan.session.added.${entry.added.target}`);
    tone = styles.verdictOwned;
    action = entry.added.id ? { label: t('scan.session.undo'), go: onUndo } : null;
  } else if (result.catalog) {
    const found = result.catalog;
    verdict = t(entry.added?.undone ? 'scan.session.undone' : entry.addFailed ? 'scan.session.failed' : 'scan.verdict.new');
    tone = styles.verdictNew;
    action = { label: t('scan.action.add'), go: () => navigate(catalogPath(found), { state: { result: found } }) };
  } else {
    verdict = t('scan.verdict.unknown');
    tone = styles.verdictError;
    action = { label: t('scan.action.manual'), go: () => navigate('/discover') };
  }

  return (
    <li className={styles.entry}>
      {/* No cover drawn for an ISBN nobody knows: a coloured block titled with digits. */}
      {(book || result.catalog) && <Cover variant="shelf" width={52} title={title} imageUrl={cover ?? undefined} />}
      <div className={styles.entryBody}>
        <span className={styles.entryTitle}>{title}</span>
        {authors && <span className={styles.entryAuthor}>{authors}</span>}
        <span className={tone}>{verdict}</span>
        {likely && <span className={styles.likely}>{t(`scan.likely.${result.match}`)}</span>}
      </div>
      {action && (
        <Button variant="secondary" size="compact" onClick={action.go}>
          {action.label}
        </Button>
      )}
    </li>
  );
}

function failureMessage(t: TFunction, error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 429) return t('scan.tooMany');
    try {
      const parsed = JSON.parse(String(error.body)) as { message?: string };
      if (parsed.message) return parsed.message;
    } catch {
      // Not a JSON payload: fall through.
    }
  }
  return t('scan.failed');
}

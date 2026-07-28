import { useRef, useState, type ChangeEvent } from 'react';
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

function resultMessage(r: ImportResult): string {
  return `${r.imported ?? 0} titre(s) importé(s) · ${r.skipped ?? 0} déjà présent(s).`;
}

/** The API reports import problems through the message of a 400 response. */
function failureMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    try {
      const parsed = JSON.parse(String(error.body)) as { message?: string };
      if (parsed.message) return parsed.message;
    } catch {
      // Not a JSON payload: fall through to the status.
    }
    return `Erreur ${error.status}`;
  }
  return fallback;
}

export function ImportSection() {
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
      setMessage(resultMessage(result));
      refreshLibrary();
    } catch (e) {
      setError(failureMessage(e, 'Import indisponible pour le moment.'));
    }
  }

  async function onFile(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    reset();
    try {
      const text = await file.text();
      const result = await importCsv({ data: text });
      setMessage(resultMessage(result));
      refreshLibrary();
    } catch (err) {
      setError(failureMessage(err, 'Fichier illisible.'));
    } finally {
      if (fileInput.current) fileInput.current.value = '';
    }
  }

  return (
    <>
      <h3 className={styles.title}>Importer ma bibliothèque</h3>
      <p className={styles.intro}>
        Depuis Booknode (par pseudo) ou via un fichier CSV exporté (Babelio, Goodreads…).
      </p>

      {!auth.authed ? (
        <Button variant="secondary" onClick={auth.login}>
          <Icon name="login" size={18} color="var(--ink-soft)" />
          Se connecter pour importer
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
              { id: 'booknode', label: 'Booknode' },
              { id: 'babelio', label: 'Babelio' },
            ]}
          />
          <div className={styles.handleRow}>
            <input
              value={handle}
              onChange={(e) => setHandle(e.target.value)}
              placeholder={source === 'booknode' ? 'Pseudo Booknode' : 'Pseudo Babelio'}
              className={styles.handleInput}
            />
            <Button variant="primary" size="compact" onClick={() => void runScrape()} disabled={busy}>
              {busy ? '…' : 'Importer'}
            </Button>
          </div>

          <button onClick={() => fileInput.current?.click()} disabled={busy} className={styles.fileButton}>
            <Icon name="upload_file" size={18} color="var(--accent-deep)" />
            Importer un fichier CSV
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

import { useRef, useState, type ChangeEvent } from 'react';
import { useAuth } from 'react-oidc-context';
import { Button, Segmented } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import {
  postApiImportCsv,
  postApiImportSource,
  type ImportResult,
} from '../../api/generated/librarius';

type Source = 'booknode' | 'babelio';

function authOpts(token?: string): RequestInit | undefined {
  return token ? { headers: { Authorization: `Bearer ${token}` } } : undefined;
}

function resultMessage(r: ImportResult): string {
  return `${r.imported ?? 0} titre(s) importé(s) · ${r.skipped ?? 0} déjà présent(s).`;
}

export function ImportSection() {
  const auth = useAuth();
  const fileInput = useRef<HTMLInputElement>(null);
  const [source, setSource] = useState<Source>('booknode');
  const [handle, setHandle] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const token = auth.user?.access_token;

  function reset() {
    setMessage(null);
    setError(null);
  }

  async function runScrape() {
    if (!handle.trim()) return;
    setBusy(true);
    reset();
    try {
      const res = await postApiImportSource(source, { handle: handle.trim() }, authOpts(token));
      if (res.status === 200) setMessage(resultMessage(res.data));
      else setError((res.data as unknown as { message?: string })?.message ?? `Erreur ${res.status}`);
    } catch {
      setError('Import indisponible pour le moment.');
    } finally {
      setBusy(false);
    }
  }

  async function onFile(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setBusy(true);
    reset();
    try {
      const text = await file.text();
      const res = await postApiImportCsv(text, authOpts(token));
      if (res.status === 200) setMessage(resultMessage(res.data));
      else setError((res.data as unknown as { message?: string })?.message ?? `Erreur ${res.status}`);
    } catch {
      setError('Fichier illisible.');
    } finally {
      setBusy(false);
      if (fileInput.current) fileInput.current.value = '';
    }
  }

  return (
    <>
      <h3 style={{ fontSize: 16, margin: '0 0 4px' }}>Importer ma bibliothèque</h3>
      <p style={{ fontSize: 12, color: 'var(--muted)', margin: '0 0 14px', lineHeight: 1.4 }}>
        Depuis Booknode (par pseudo) ou via un fichier CSV exporté (Babelio, Goodreads…).
      </p>

      {!auth.isAuthenticated ? (
        <Button variant="secondary" onClick={() => void auth.signinRedirect()}>
          <Icon name="login" size={18} color="var(--ink-soft)" />
          Se connecter pour importer
        </Button>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 26 }}>
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
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              value={handle}
              onChange={(e) => setHandle(e.target.value)}
              placeholder={source === 'booknode' ? 'Pseudo Booknode' : 'Pseudo Babelio'}
              style={{ flex: 1, border: '1.5px solid var(--line)', borderRadius: 12, padding: '11px 14px', fontSize: 13.5, fontFamily: 'inherit', color: 'var(--ink)', background: 'var(--surface)', outline: 'none' }}
            />
            <Button variant="primary" onClick={() => void runScrape()} disabled={busy} style={{ padding: '0 16px' }}>
              {busy ? '…' : 'Importer'}
            </Button>
          </div>

          <button
            onClick={() => fileInput.current?.click()}
            disabled={busy}
            style={{ alignSelf: 'flex-start', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--accent-deep)', fontSize: 13, fontWeight: 600, fontFamily: 'inherit', display: 'flex', alignItems: 'center', gap: 6, padding: '4px 0' }}
          >
            <Icon name="upload_file" size={18} color="var(--accent-deep)" />
            Importer un fichier CSV
          </button>
          <input ref={fileInput} type="file" accept=".csv,text/csv,text/plain" onChange={onFile} style={{ display: 'none' }} />

          {message && <p style={{ fontSize: 13, color: 'var(--accent-deep)', fontWeight: 600, margin: 0 }}>{message}</p>}
          {error && <p style={{ fontSize: 13, color: 'var(--rose)', margin: 0 }}>{error}</p>}
        </div>
      )}
    </>
  );
}

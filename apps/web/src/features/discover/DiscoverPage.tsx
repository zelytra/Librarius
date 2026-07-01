import { useState, type CSSProperties, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useApiAuth } from '../../shared/api';
import { LoginGate } from '../../shared/LoginGate';
import {
  getApiCatalogSearch,
  postApiLibrary,
  postApiWishlist,
  type CatalogResult,
  type ManualBookDto,
} from '../../api/generated/librarius';
import { Icon } from '../../shared/ui/Icon';
import { Segmented } from '../../shared/ui/primitives';
import { BookCover } from '../../shared/ui/BookCover';

type Kind = 'BOOK' | 'MANGA';

function toBook(r: CatalogResult, fallbackKind: Kind): ManualBookDto {
  return {
    kind: (r.kind as Kind) ?? fallbackKind,
    title: r.title ?? '—',
    authors: r.authors,
    coverUrl: r.coverUrl,
    synopsis: r.synopsis,
    isbn13: r.isbn13,
    publisher: r.publisher,
    language: r.language,
    originalYear: r.year,
    releaseDate: r.releaseDate,
  };
}

function DiscoverContent() {
  const { t } = useTranslation();
  const { opts } = useApiAuth();
  const [query, setQuery] = useState('');
  const [kind, setKind] = useState<Kind>('BOOK');
  const [results, setResults] = useState<CatalogResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [added, setAdded] = useState<Record<string, 'library' | 'wishlist'>>({});

  const keyOf = (r: CatalogResult, i: number) => `${r.provider ?? ''}:${r.providerRef ?? i}:${r.title ?? ''}`;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const q = query.trim();
    if (!q) return;
    setLoading(true);
    setError(null);
    setAdded({});
    try {
      const res = await getApiCatalogSearch({ q, kind }, opts);
      if (res.status === 200) setResults(res.data);
      else setError(`Erreur ${res.status}`);
    } catch {
      setError('Recherche indisponible pour le moment.');
    } finally {
      setLoading(false);
    }
  }

  async function add(r: CatalogResult, key: string, target: 'library' | 'wishlist') {
    const book = toBook(r, kind);
    try {
      if (target === 'library') await postApiLibrary({ book, status: 'OWNED' }, opts);
      else await postApiWishlist({ book, priority: 'SOON' }, opts);
      setAdded((a) => ({ ...a, [key]: target }));
    } catch {
      setError("Ajout impossible pour le moment.");
    }
  }

  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Segmented<Kind>
          value={kind}
          onChange={setKind}
          options={[
            { id: 'BOOK', label: t('common.books') },
            { id: 'MANGA', label: t('common.mangas') },
          ]}
        />
      </div>

      <form onSubmit={onSubmit} style={{ display: 'flex', alignItems: 'center', gap: 10, background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 14, padding: '10px 14px', marginBottom: 22 }}>
        <Icon name="search" size={21} color="var(--faint)" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t('discover.searchPlaceholder')}
          style={{ flex: 1, border: 'none', outline: 'none', background: 'transparent', fontSize: 14, color: 'var(--ink)', fontFamily: 'inherit' }}
        />
        <button type="submit" aria-label="Rechercher" style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
          <Icon name="arrow_forward" size={20} color="var(--accent-deep)" />
        </button>
      </form>

      {loading && <p style={{ color: 'var(--muted)', fontSize: 13 }}>{t('common.loading')}</p>}
      {error && <p style={{ color: 'var(--rose)', fontSize: 13 }}>{error}</p>}
      {!loading && !error && results.length === 0 && (
        <p style={{ color: 'var(--faint)', fontSize: 13 }}>Lancez une recherche pour trouver des titres à ajouter.</p>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {results.map((r, i) => {
          const key = keyOf(r, i);
          const state = added[key];
          return (
            <div key={key} style={{ display: 'flex', gap: 14, padding: 12, background: 'var(--surface)', borderRadius: 16, border: '1px solid var(--line)' }}>
              <BookCover color="var(--accent-soft)" imageUrl={r.coverUrl ?? undefined} title={r.coverUrl ? undefined : (r.title ?? undefined)} width={58} height={84} radius={8} />
              <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 2 }}>
                <div style={{ fontFamily: "'Newsreader',serif", fontSize: 15.5, fontWeight: 600, lineHeight: 1.1 }}>{r.title}</div>
                <div style={{ fontSize: 12.5, color: 'var(--muted)' }}>{[r.authors, r.year].filter(Boolean).join(' · ')}</div>
                {state ? (
                  <div style={{ fontSize: 12, color: 'var(--accent-deep)', fontWeight: 600, marginTop: 4 }}>
                    {state === 'library' ? '✓ Ajouté à la collection' : '✓ Ajouté aux souhaits'}
                  </div>
                ) : (
                  <div style={{ display: 'flex', gap: 8, marginTop: 6 }}>
                    <button onClick={() => void add(r, key, 'library')} style={btn('var(--accent)')}>
                      <Icon name="add" size={16} color="#fff" /> Collection
                    </button>
                    <button onClick={() => void add(r, key, 'wishlist')} style={btnGhost()}>
                      <Icon name="favorite" size={16} color="var(--rose)" /> Souhaits
                    </button>
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
}

function btn(bg: string): CSSProperties {
  return { display: 'inline-flex', alignItems: 'center', gap: 5, background: bg, color: '#fff', border: 'none', borderRadius: 20, padding: '6px 12px', fontSize: 12, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit' };
}
function btnGhost(): CSSProperties {
  return { display: 'inline-flex', alignItems: 'center', gap: 5, background: 'var(--surface)', color: 'var(--ink-soft)', border: '1.5px solid var(--line)', borderRadius: 20, padding: '6px 12px', fontSize: 12, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit' };
}

export function DiscoverPage() {
  const { t } = useTranslation();
  return (
    <div style={{ padding: '14px 22px 40px' }}>
      <h2 style={{ fontSize: 27, margin: '6px 0 16px' }}>{t('discover.title')}</h2>
      <LoginGate prompt="Connecte-toi pour rechercher et enrichir ta bibliothèque.">
        <DiscoverContent />
      </LoginGate>
    </div>
  );
}

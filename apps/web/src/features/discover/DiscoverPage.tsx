import { useState, type FormEvent } from 'react';
import { useAuth } from 'react-oidc-context';
import { useTranslation } from 'react-i18next';
import { getApiCatalogSearch, type CatalogResult } from '../../api/generated/librarius';
import { Icon } from '../../shared/ui/Icon';
import { Button, Segmented } from '../../shared/ui/primitives';
import { BookCover } from '../../shared/ui/BookCover';

type Kind = 'BOOK' | 'MANGA';

export function DiscoverPage() {
  const { t } = useTranslation();
  const auth = useAuth();
  const [query, setQuery] = useState('');
  const [kind, setKind] = useState<Kind>('BOOK');
  const [results, setResults] = useState<CatalogResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const q = query.trim();
    if (!q) return;
    setLoading(true);
    setError(null);
    try {
      const token = auth.user?.access_token;
      const res = await getApiCatalogSearch(
        { q, kind },
        token ? { headers: { Authorization: `Bearer ${token}` } } : undefined,
      );
      if (res.status === 200) {
        setResults(res.data);
      } else {
        setError(`Erreur ${res.status}`);
      }
    } catch {
      setError('Recherche indisponible pour le moment.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ padding: '14px 22px 40px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <h2 style={{ fontSize: 27, margin: '6px 0 16px' }}>{t('discover.title')}</h2>
        {auth.isAuthenticated && (
          <button
            onClick={() => void auth.signoutRedirect()}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--muted)', fontSize: 12.5, fontWeight: 600, fontFamily: 'inherit' }}
          >
            {auth.user?.profile.preferred_username} · Quitter
          </button>
        )}
      </div>

      {!auth.isAuthenticated ? (
        <div style={{ textAlign: 'center', padding: '50px 16px', color: 'var(--muted)' }}>
          <Icon name="travel_explore" size={42} />
          <p style={{ fontSize: 13.5, lineHeight: 1.5, margin: '12px 0 18px' }}>
            Connectez-vous pour rechercher dans le catalogue (livres &amp; mangas).
          </p>
          <Button variant="primary" onClick={() => void auth.signinRedirect()}>
            <Icon name="login" size={20} fill color="#fff" />
            Se connecter
          </Button>
        </div>
      ) : (
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

          <form
            onSubmit={onSubmit}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              background: 'var(--surface)',
              border: '1px solid var(--line)',
              borderRadius: 14,
              padding: '10px 14px',
              marginBottom: 22,
            }}
          >
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
            <p style={{ color: 'var(--faint)', fontSize: 13 }}>
              {t('discover.popularGenres')} — lancez une recherche pour voir des résultats.
            </p>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: '16px 12px' }}>
            {results.map((r, i) => (
              <div key={`${r.providerRef ?? i}`} style={{ display: 'flex', flexDirection: 'column' }}>
                <BookCover
                  color="var(--accent-soft)"
                  title={r.coverUrl ? undefined : (r.title ?? undefined)}
                  imageUrl={r.coverUrl ?? undefined}
                  width="100%"
                  height={150}
                />
                <div style={{ fontSize: 11.5, fontWeight: 600, color: 'var(--ink)', marginTop: 6, lineHeight: 1.2 }}>
                  {r.title}
                </div>
                <div style={{ fontSize: 10.5, color: 'var(--muted)' }}>
                  {[r.authors, r.year].filter(Boolean).join(' · ')}
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

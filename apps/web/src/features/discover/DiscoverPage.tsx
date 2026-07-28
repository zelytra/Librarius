import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { LoginGate } from '../../shared/LoginGate';
import { ApiError } from '../../shared/apiClient';
import {
  getGetApiLibraryQueryKey,
  getGetApiStatsQueryKey,
  getGetApiWishlistQueryKey,
  useGetApiCatalogSearch,
  usePostApiLibrary,
  usePostApiWishlist,
  type CatalogResult,
  type ManualBookDto,
} from '../../api/generated/librarius';

/** The provider is reachable but refused: show the status, it is actionable. */
function searchFailureMessage(error: unknown): string {
  if (error instanceof ApiError) {
    // 429: the caller used up their share of the shared provider quota. Saying "error
    // 429" would be useless — what matters is that waiting fixes it.
    if (error.status === 429) return 'Trop de recherches d’affilée. Réessaie dans une minute.';
    return `Erreur ${error.status}`;
  }
  return 'Recherche indisponible pour le moment.';
}
import { Icon } from '../../shared/ui/Icon';
import { Screen, ScreenTitle, Segmented, StatusText } from '../../shared/ui/primitives';
import { BookCover } from '../../shared/ui/BookCover';
import styles from './DiscoverPage.module.css';

type Kind = 'BOOK' | 'MANGA';

/** Size of the thumbnail shown next to a catalogue result. */
const RESULT_COVER = { width: 58, height: 84, radius: 8 };

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
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [kind, setKind] = useState<Kind>('BOOK');
  // The search only runs once submitted, never on every keystroke: each miss costs a
  // call to a rate-limited third-party provider.
  const [submitted, setSubmitted] = useState<{ q: string; kind: Kind } | null>(null);
  const [added, setAdded] = useState<Record<string, 'library' | 'wishlist'>>({});
  const [addError, setAddError] = useState<string | null>(null);

  const keyOf = (r: CatalogResult, i: number) => `${r.provider ?? ''}:${r.providerRef ?? i}:${r.title ?? ''}`;

  const {
    data: results = [],
    isFetching: loading,
    error: searchError,
  } = useGetApiCatalogSearch(submitted ? { q: submitted.q, kind: submitted.kind } : undefined, {
    query: { enabled: submitted != null },
  });

  const error = addError ?? (searchError ? searchFailureMessage(searchError) : null);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const q = query.trim();
    if (!q) return;
    setAdded({});
    setAddError(null);
    setSubmitted({ q, kind });
  }

  const { mutateAsync: addToLibrary } = usePostApiLibrary();
  const { mutateAsync: addToWishlist } = usePostApiWishlist();

  async function add(r: CatalogResult, key: string, target: 'library' | 'wishlist') {
    const book = toBook(r, kind);
    try {
      if (target === 'library') await addToLibrary({ data: { book, status: 'OWNED' } });
      else await addToWishlist({ data: { book, priority: 'SOON' } });
      setAdded((a) => ({ ...a, [key]: target }));
      // The new title must show up in Collection, Wishlist, Home and the counters.
      void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
      void queryClient.invalidateQueries({ queryKey: getGetApiWishlistQueryKey() });
      void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
    } catch {
      setAddError('Ajout impossible pour le moment.');
    }
  }

  return (
    <>
      <div className={styles.kindSwitch}>
        <Segmented<Kind>
          value={kind}
          onChange={setKind}
          options={[
            { id: 'BOOK', label: t('common.books') },
            { id: 'MANGA', label: t('common.mangas') },
          ]}
        />
      </div>

      <form onSubmit={onSubmit} className={styles.searchBar}>
        <Icon name="search" size={21} color="var(--faint)" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t('discover.searchPlaceholder')}
          className={styles.searchInput}
        />
        <button type="submit" aria-label="Rechercher" className={styles.submit}>
          <Icon name="arrow_forward" size={20} color="var(--accent-deep)" />
        </button>
      </form>

      {loading && <StatusText>{t('common.loading')}</StatusText>}
      {error && <StatusText tone="error">{error}</StatusText>}
      {!loading && !error && results.length === 0 && (
        <StatusText tone="faint">Lancez une recherche pour trouver des titres à ajouter.</StatusText>
      )}

      <div className={styles.results}>
        {results.map((r, i) => {
          const key = keyOf(r, i);
          const state = added[key];
          return (
            <div key={key} className={styles.result}>
              <BookCover
                color="var(--accent-soft)"
                imageUrl={r.coverUrl ?? undefined}
                title={r.coverUrl ? undefined : (r.title ?? undefined)}
                width={RESULT_COVER.width}
                height={RESULT_COVER.height}
                radius={RESULT_COVER.radius}
              />
              <div className={styles.resultBody}>
                <div className={styles.resultTitle}>{r.title}</div>
                <div className={styles.resultMeta}>{[r.authors, r.year].filter(Boolean).join(' · ')}</div>
                {state ? (
                  <div className={styles.added}>
                    {state === 'library' ? '✓ Ajouté à la collection' : '✓ Ajouté aux souhaits'}
                  </div>
                ) : (
                  <div className={styles.actions}>
                    <button
                      onClick={() => void add(r, key, 'library')}
                      className={`${styles.action} ${styles.actionPrimary}`}
                    >
                      <Icon name="add" size={16} color="var(--on-accent)" /> Collection
                    </button>
                    <button
                      onClick={() => void add(r, key, 'wishlist')}
                      className={`${styles.action} ${styles.actionGhost}`}
                    >
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

export function DiscoverPage() {
  const { t } = useTranslation();
  return (
    <Screen>
      <ScreenTitle className={styles.title}>{t('discover.title')}</ScreenTitle>
      <LoginGate prompt="Connecte-toi pour rechercher et enrichir ta bibliothèque.">
        <DiscoverContent />
      </LoginGate>
    </Screen>
  );
}

import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
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
  type GetApiCatalogSearchParams,
  type ManualBookDto,
} from '../../api/generated/librarius';

import { Icon } from '../../shared/ui/Icon';
import { Button, Screen, ScreenTitle, Segmented } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { BookCover } from '../../shared/ui/BookCover';
import { Field, FieldGrid, SelectField } from './fields';
import { ManualAddForm } from './ManualAddForm';
import { detectIsbn } from './isbn';
import styles from './DiscoverPage.module.css';

/** The provider is reachable but refused: show the status, it is actionable. */
function searchFailureMessage(t: TFunction, error: unknown): string {
  if (error instanceof ApiError) {
    // 429: the caller used up their share of the shared provider quota. Saying "error
    // 429" would be useless — what matters is that waiting fixes it.
    if (error.status === 429) return t('discover.errors.rateLimited');
    return t('common.errorWithStatus', { status: error.status });
  }
  return t('discover.errors.unavailable');
}

type Kind = 'BOOK' | 'MANGA';

/** Size of the thumbnail shown next to a catalogue result. */
const RESULT_COVER = { width: 58, height: 84, radius: 8 };

/**
 * Languages the advanced search offers, as ISO 639-1 codes. The API maps them to whatever
 * its providers index — Open Library wants MARC codes, AniList has no such notion.
 */
const LANGUAGES = ['fr', 'en', 'ja', 'de', 'es', 'it'];

/** The advanced criteria, held as typed rather than parsed: the form owns raw text. */
interface Advanced {
  author: string;
  year: string;
  language: string;
  publisher: string;
}

const NO_ADVANCED: Advanced = { author: '', year: '', language: '', publisher: '' };

function trimmed(value: string): string | undefined {
  return value.trim() === '' ? undefined : value.trim();
}

/**
 * Turns what the form holds into the query the API takes. An ISBN recognised in the plain
 * field is sent as one instead of as keywords: the catalogs index it on its own field, so
 * searching the digits as text finds nothing.
 */
function searchParams(query: string, advanced: Advanced, kind: Kind): GetApiCatalogSearchParams {
  const isbn = detectIsbn(query);
  const year = Number(advanced.year.trim());
  return {
    kind: [kind],
    q: isbn ? undefined : trimmed(query),
    isbn: isbn ?? undefined,
    author: trimmed(advanced.author),
    year: advanced.year.trim() === '' || Number.isNaN(year) ? undefined : year,
    language: trimmed(advanced.language),
    publisher: trimmed(advanced.publisher),
  };
}

/** Nothing to search: every criterion is empty, and the API would answer an empty list. */
function isBlankSearch(params: GetApiCatalogSearchParams): boolean {
  return !params.q && !params.isbn && !params.author && !params.year && !params.language
    && !params.publisher;
}

/**
 * The result as the API takes it. `provider` and `providerRef` say which record the entry
 * came from: without them the server cannot tell a title picked here from one typed by hand,
 * and no provider can be asked about it again later.
 */
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
    provider: r.provider,
    providerRef: r.providerRef,
  };
}

function DiscoverContent() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [kind, setKind] = useState<Kind>('BOOK');
  const [advanced, setAdvanced] = useState<Advanced>(NO_ADVANCED);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [manualOpen, setManualOpen] = useState(false);
  const [manualAdded, setManualAdded] = useState<string | null>(null);
  // The search only runs once submitted, never on every keystroke: each miss costs a
  // call to a rate-limited third-party provider.
  const [submitted, setSubmitted] = useState<GetApiCatalogSearchParams | null>(null);
  const [added, setAdded] = useState<Record<string, 'library' | 'wishlist'>>({});
  const [addError, setAddError] = useState<string | null>(null);

  const detectedIsbn = detectIsbn(query);

  const keyOf = (r: CatalogResult, i: number) => `${r.provider ?? ''}:${r.providerRef ?? i}:${r.title ?? ''}`;

  const {
    data: results = [],
    isFetching: loading,
    isError: searchFailed,
    error: searchError,
    refetch,
  } = useGetApiCatalogSearch(submitted ?? undefined, { query: { enabled: submitted != null } });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const params = searchParams(query, advanced, kind);
    if (isBlankSearch(params)) return;
    setAdded({});
    setAddError(null);
    setManualAdded(null);
    setSubmitted(params);
  }

  const { mutateAsync: addToLibrary } = usePostApiLibrary();
  const { mutateAsync: addToWishlist } = usePostApiWishlist();

  /** The new title must show up in Collection, Wishlist, Home and the counters. */
  function refreshAfterAdd() {
    void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
    void queryClient.invalidateQueries({ queryKey: getGetApiWishlistQueryKey() });
    void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
  }

  async function add(r: CatalogResult, key: string, target: 'library' | 'wishlist') {
    const book = toBook(r, kind);
    try {
      if (target === 'library') await addToLibrary({ data: { book, status: 'OWNED' } });
      else await addToWishlist({ data: { book, priority: 'SOON' } });
      setAdded((a) => ({ ...a, [key]: target }));
      refreshAfterAdd();
    } catch {
      setAddError(t('discover.errors.addFailed'));
    }
  }

  /** The way out of an empty screen: the book exists, the catalogs just do not know it. */
  const manualAction = (
    <Button variant="secondary" onClick={() => setManualOpen(true)}>
      <Icon name="edit" size={18} color="var(--ink-soft)" />
      {t('discover.manual.open')}
    </Button>
  );

  const nothingFound = !loading && !searchFailed && results.length === 0;

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

      <form onSubmit={onSubmit} className={styles.searchForm}>
        <div className={styles.searchBar}>
          <Icon name="search" size={21} color="var(--faint)" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('discover.searchPlaceholder')}
            aria-label={t('discover.searchPlaceholder')}
            className={styles.searchInput}
          />
          <button type="submit" aria-label={t('common.search')} className={styles.submit}>
            <Icon name="arrow_forward" size={20} color="var(--accent-deep)" />
          </button>
        </div>

        {detectedIsbn && (
          <p className={styles.isbnHint}>{t('discover.isbnDetected', { isbn: detectedIsbn })}</p>
        )}

        <button
          type="button"
          onClick={() => setAdvancedOpen((open) => !open)}
          aria-expanded={advancedOpen}
          className={styles.advancedToggle}
        >
          <Icon name={advancedOpen ? 'expand_less' : 'tune'} size={18} color="var(--accent-deep)" />
          {t(advancedOpen ? 'discover.advanced.close' : 'discover.advanced.open')}
        </button>

        {advancedOpen && (
          <div className={styles.advanced}>
            <FieldGrid>
              <Field
                label={t('discover.advanced.author')}
                value={advanced.author}
                onChange={(author) => setAdvanced((a) => ({ ...a, author }))}
              />
              <Field
                label={t('discover.advanced.year')}
                value={advanced.year}
                onChange={(year) => setAdvanced((a) => ({ ...a, year }))}
                type="number"
                inputMode="numeric"
              />
              <Field
                label={t('discover.advanced.publisher')}
                value={advanced.publisher}
                onChange={(publisher) => setAdvanced((a) => ({ ...a, publisher }))}
              />
              <SelectField
                label={t('discover.advanced.language')}
                value={advanced.language}
                onChange={(language) => setAdvanced((a) => ({ ...a, language }))}
                options={[
                  { value: '', label: t('discover.advanced.anyLanguage') },
                  ...LANGUAGES.map((code) => ({
                    value: code,
                    label: t(`discover.advanced.languages.${code}`),
                  })),
                ]}
              />
            </FieldGrid>
            {/* Saying which criteria a provider honours beats silently dropping them. */}
            <p className={styles.coverage}>{t('discover.advanced.coverage')}</p>
            <Button type="button" variant="ghost" onClick={() => setAdvanced(NO_ADVANCED)}>
              {t('discover.advanced.reset')}
            </Button>
          </div>
        )}
      </form>

      {manualOpen && (
        <ManualAddForm
          kind={kind}
          onCancel={() => setManualOpen(false)}
          onAdded={(title) => {
            setManualOpen(false);
            setManualAdded(title);
            refreshAfterAdd();
          }}
        />
      )}

      {manualAdded && (
        <p className={styles.manualAdded}>{t('discover.manual.added', { title: manualAdded })}</p>
      )}

      {loading && <Loading />}
      {addError && <ErrorState message={addError} />}
      {searchFailed && (
        <ErrorState message={searchFailureMessage(t, searchError)} onRetry={() => void refetch()} />
      )}

      {/* Both empty states offer the manual entry: a search that found nothing is exactly
          when the user needs it, and so is a screen they have not searched from yet. */}
      {!manualOpen && nothingFound && (
        <EmptyState
          icon={submitted ? 'search_off' : 'search'}
          title={t(submitted ? 'discover.noResults' : 'discover.start')}
          description={t('discover.manual.invite')}
          action={manualAction}
        />
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
                    {t(state === 'library' ? 'discover.addedToLibrary' : 'discover.addedToWishlist')}
                  </div>
                ) : (
                  <div className={styles.actions}>
                    <button
                      onClick={() => void add(r, key, 'library')}
                      className={`${styles.action} ${styles.actionPrimary}`}
                    >
                      <Icon name="add" size={16} color="var(--on-accent)" />
                      {t('discover.addToLibrary')}
                    </button>
                    <button
                      onClick={() => void add(r, key, 'wishlist')}
                      className={`${styles.action} ${styles.actionGhost}`}
                    >
                      <Icon name="favorite" size={16} color="var(--rose)" />
                      {t('discover.addToWishlist')}
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
      <LoginGate prompt={t('auth.prompts.discover')}>
        <DiscoverContent />
      </LoginGate>
    </Screen>
  );
}

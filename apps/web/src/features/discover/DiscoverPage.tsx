import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useQueryClient } from '@tanstack/react-query';
import { LoginGate } from '../../shared/LoginGate';
import { ApiError } from '../../shared/apiClient';
import {
  getGetApiLibraryQueryKey,
  getGetApiWishlistQueryKey,
  useGetApiCatalogSearch,
  type CatalogResult,
  type GetApiCatalogSearchParams,
  type Kind,
} from '../../api/generated/librarius';

import { Icon } from '../../shared/ui/Icon';
import { Button, Screen, ScreenTitle } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { BookCover } from '../../shared/ui/BookCover';
import { AuthorMatches } from '../author/AuthorSearch';
import { AuthorNames } from '../author/AuthorNames';
import { splitAuthorNames } from '../author/author';
import { SeriesLink } from '../series/SeriesLink';
import { catalogPath, toBook } from '../catalog/catalogBook';
import { addedKeyOf, type AddTarget } from '../catalog/addTargets';
import { AddButton } from '../catalog/AddButton';
import { useAddTitle } from '../catalog/useAddTitle';
import { Field, FieldGrid, MultiSelectField, SelectField } from './fields';
import { ManualAddForm } from './ManualAddForm';
import { detectIsbn } from './isbn';
import { isBlankSearch, searchFromUrl, searchToUrl } from './searchUrl';
import { ALL_KINDS, KIND_LABEL_KEY, knownKind } from './medium';
import styles from './DiscoverPage.module.css';
import { STATS_QUERIES } from '../../shared/statsQueries';

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
  /** Narrows the search to these mediums; empty reaches every registered provider. */
  medium: Kind[];
}

const NO_ADVANCED: Advanced = { author: '', year: '', language: '', publisher: '', medium: [] };

function trimmed(value: string): string | undefined {
  return value.trim() === '' ? undefined : value.trim();
}

/**
 * Turns what the form holds into the query the API takes. An ISBN recognised in the plain
 * field is sent as one instead of as keywords: the catalogs index it on its own field, so
 * searching the digits as text finds nothing. `kind` is left out entirely when the medium
 * filter is empty, which is what makes the search reach every registered provider instead
 * of just one.
 */
function searchParams(query: string, advanced: Advanced): GetApiCatalogSearchParams {
  const isbn = detectIsbn(query);
  const year = Number(advanced.year.trim());
  return {
    kind: advanced.medium.length > 0 ? advanced.medium : undefined,
    q: isbn ? undefined : trimmed(query),
    isbn: isbn ?? undefined,
    author: trimmed(advanced.author),
    year: advanced.year.trim() === '' || Number.isNaN(year) ? undefined : year,
    language: trimmed(advanced.language),
    publisher: trimmed(advanced.publisher),
  };
}


function DiscoverContent() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  // The search lives in the URL (#435): coming back from a fiche lands on the same search.
  const [url, setUrl] = useSearchParams();
  const fromUrl = searchFromUrl(url);
  const [query, setQuery] = useState(fromUrl?.q ?? fromUrl?.isbn ?? '');
  const [advanced, setAdvanced] = useState<Advanced>(() => ({
    author: fromUrl?.author ?? '',
    year: fromUrl?.year != null ? String(fromUrl.year) : '',
    language: fromUrl?.language ?? '',
    publisher: fromUrl?.publisher ?? '',
    medium: (fromUrl?.kind as Kind[] | undefined) ?? [],
  }));
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [manualOpen, setManualOpen] = useState(false);
  const [manualAdded, setManualAdded] = useState<string | null>(null);
  // The parameters a search actually ran with — set by the button, by Enter, and by the
  // debounced auto-search below, never straight from the input value.
  const [submitted, setSubmittedState] = useState<GetApiCatalogSearchParams | null>(fromUrl);
  const setSubmitted = useCallback(
    (params: GetApiCatalogSearchParams) => {
      setSubmittedState(params);
      setUrl(searchToUrl(params), { replace: true });
    },
    [setUrl],
  );
  const [added, setAdded] = useState<Record<string, AddTarget>>({});
  const [addError, setAddError] = useState<string | null>(null);

  const detectedIsbn = detectIsbn(query);

  // Fluid search: once the box holds a real keyword, the search follows the typing after a
  // short pause — the Babelio/Mangacollec feel the button alone never gave. It holds off
  // until three characters (or a full ISBN) so a lone letter does not spend a rate-limited
  // provider call on nothing, and the pause folds a burst of keystrokes into one query. The
  // button and the Enter key still fire it at once; an advanced criterion on its own, with
  // no keyword, stays submit-driven — there is nothing typed there that settles.
  useEffect(() => {
    if (!detectedIsbn && query.trim().length < 3) return;
    const timer = setTimeout(() => {
      const params = searchParams(query, advanced);
      if (isBlankSearch(params)) return;
      // The search on screen already answers this text — submitted with the button or Enter
      // before the pause ran out. Submitting it again would wipe the "added" confirmations of
      // whatever the reader filed in the meantime.
      if (JSON.stringify(params) === JSON.stringify(submitted)) return;
      setAdded({});
      setAddError(null);
      setManualAdded(null);
      setSubmitted(params);
    }, 500);
    return () => clearTimeout(timer);
  }, [query, advanced, detectedIsbn, submitted, setSubmitted]);

  // What identifies a result is its content, never its rank: a key built on the index moved
  // the "added" state onto another title whenever the list was re-ordered between two renders,
  // which is exactly what happens to a result with no provider reference.
  const keyOf = (r: CatalogResult) =>
    [r.provider, r.providerRef, r.title, r.authors, r.isbn13, r.volumeNumber].map((v) => v ?? '').join('|');

  const {
    data: results = [],
    isFetching: loading,
    isError: searchFailed,
    error: searchError,
    refetch,
  } = useGetApiCatalogSearch(submitted ?? undefined, { query: { enabled: submitted != null } });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const params = searchParams(query, advanced);
    if (isBlankSearch(params)) return;
    setAdded({});
    setAddError(null);
    setManualAdded(null);
    setSubmitted(params);
  }

  const addTitle = useAddTitle();

  /** A title typed by hand must show up in Collection, Wishlist, Home and the counters. */
  function refreshAfterAdd() {
    void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
    void queryClient.invalidateQueries({ queryKey: getGetApiWishlistQueryKey() });
    void queryClient.invalidateQueries(STATS_QUERIES);
  }


  async function add(r: CatalogResult, key: string, target: AddTarget) {
    try {
      await addTitle(toBook(r), target);
      setAdded((a) => ({ ...a, [key]: target }));
    } catch (error) {
      setAddError(t('discover.errors.addFailed'));
      throw error;
    }
  }

  /** Opens the catalog fiche of an unowned result, carrying the result itself along so the
   *  page has everything to draw without a second lookup. */
  const openFiche = (r: CatalogResult) => navigate(catalogPath(r), { state: { result: r } });

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
      {/* Search field, ISBN hint and advanced panel are a form, not a collection: past the
          tablet breakpoint they settle at a reading-form measure instead of stretching the
          full content width — see `.searchColumn` in the module CSS. */}
      <div className={styles.searchColumn}>
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

          <div className={styles.searchActions}>
            <button
              type="button"
              onClick={() => setAdvancedOpen((open) => !open)}
              aria-expanded={advancedOpen}
              className={styles.advancedToggle}
            >
              <Icon name={advancedOpen ? 'expand_less' : 'tune'} size={18} color="var(--accent-deep)" />
              {t(advancedOpen ? 'discover.advanced.close' : 'discover.advanced.open')}
            </button>
            {/* The bookshop path: a barcode instead of a typed title (#68). */}
            <button type="button" onClick={() => navigate('/scan')} className={styles.advancedToggle}>
              <Icon name="barcode_scanner" size={18} color="var(--accent-deep)" />
              {t('discover.scan')}
            </button>
          </div>

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
                <MultiSelectField<Kind>
                  label={t('discover.advanced.medium')}
                  values={advanced.medium}
                  onChange={(medium) => setAdvanced((a) => ({ ...a, medium }))}
                  options={ALL_KINDS.map((k) => ({ value: k, label: t(KIND_LABEL_KEY[k]) }))}
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
      </div>

      {/* The same words, looked up among the people of the shared catalog: an author typed in
          the one search bar is offered before the titles. */}
      <AuthorMatches term={submitted?.q ?? ''} />

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
        {results.map((r) => {
          const key = keyOf(r);
          const state = added[key];
          const credits = splitAuthorNames(r.authors);
          return (
            <div key={key} className={styles.result}>
              <BookCover
                color="var(--accent-soft)"
                imageUrl={r.coverUrl ?? undefined}
                title={r.coverUrl ? undefined : (r.title ?? undefined)}
                width={RESULT_COVER.width}
                height={RESULT_COVER.height}
                radius={RESULT_COVER.radius}
                onClick={() => openFiche(r)}
              />
              <div className={styles.resultBody}>
                {/* The cover and the title open the fiche — the page to read before owning;
                    the two buttons below file it straight away without that detour. */}
                <button type="button" onClick={() => openFiche(r)} className={styles.resultTitle}>
                  {r.title}
                </button>
                {/* A mixed feed no longer says what a result is through a screen-wide
                    toggle, so each one names its own medium here. */}
                <div className={styles.resultMeta}>
                  <span className={styles.kindBadge}>{t(KIND_LABEL_KEY[knownKind(r.kind)])}</span>
                  {/* The series and each author lead on (#443): the reader's series or a search
                      on it, the author's page or a search on their books. */}
                  {r.seriesTitle && (
                    <span>
                      <SeriesLink title={r.seriesTitle} kind={r.kind} />
                    </span>
                  )}
                  {credits.length > 0 && (
                    <span>
                      <AuthorNames text={r.authors} searchFallback limit={SHOWN_CREDITS} />
                      {credits.length > SHOWN_CREDITS && t('discover.moreAuthors', { count: credits.length - SHOWN_CREDITS })}
                    </span>
                  )}
                  {r.year != null && <span>{r.year}</span>}
                </div>
                {state ? (
                  <div className={styles.added}>{t(addedKeyOf(state))}</div>
                ) : (
                  <div className={styles.actions}>
                    {/* One button, then the sheet that asks where the title goes (#399). */}
                    <AddButton compact onAdd={(target) => add(r, key, target)} />
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

/** Credits shown on a result card: an anthology's fifteen names grew the card to a screen's
 *  height. The first three, then how many more; the fiche lists them all. */
const SHOWN_CREDITS = 3;

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

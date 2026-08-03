import { useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useQueryClient } from '@tanstack/react-query';
import { LoginGate } from '../../shared/LoginGate';
import { Icon } from '../../shared/ui/Icon';
import { Cover } from '../../shared/ui/Cover';
import { Button, Screen } from '../../shared/ui/primitives';
import { EmptyState, ErrorState } from '../../shared/ui/states';
import {
  getGetApiLibraryQueryKey,
  getGetApiStatsQueryKey,
  getGetApiWishlistQueryKey,
  usePostApiLibrary,
  usePostApiWishlist,
  type CatalogResult,
} from '../../api/generated/librarius';
import { AuthorNames } from '../author/AuthorNames';
import { KIND_LABEL_KEY, knownKind } from '../discover/medium';
import { toBook } from './catalogBook';
import styles from './CatalogDetailPage.module.css';

type Target = 'library' | 'wishlist';

/** The series line: the run and, when it is one, the volume within it. */
function seriesLabel(result: CatalogResult, t: TFunction): string {
  if (!result.seriesTitle) return t('detail.standalone');
  return result.volumeNumber != null
    ? `${result.seriesTitle} · ${t('collection.volumeShort', { number: result.volumeNumber })}`
    : result.seriesTitle;
}

function Fact({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className={styles.fact}>
      <div className={styles.factValue}>{value}</div>
      <div className={styles.factLabel}>{label}</div>
    </div>
  );
}

/**
 * The catalog fiche of a title the reader does not own yet: cover, credits, the facts the
 * enriched result carries, the summary, and the two ways to file it. It is what "clicking a
 * title opens its page" means for a search result — a page to read before owning, the way
 * Babelio and Mangacollec show one.
 */
function CatalogDetailContent({ result }: { result: CatalogResult }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [added, setAdded] = useState<Target | null>(null);
  const [addError, setAddError] = useState<string | null>(null);

  const { mutateAsync: addToLibrary } = usePostApiLibrary();
  const { mutateAsync: addToWishlist } = usePostApiWishlist();

  const title = result.title ?? '—';
  const kind = knownKind(result.kind);
  // Publisher, language and ISBN, in one line: secondary next to the facts above, absent
  // rather than shown as dashes when the record carries none of them.
  const meta = [result.publisher, result.language, result.isbn13].filter(Boolean).join(' · ');

  /** The title now exists somewhere it did not: Collection, Wishlist, Home and the counters. */
  function refreshAfterAdd() {
    void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
    void queryClient.invalidateQueries({ queryKey: getGetApiWishlistQueryKey() });
    void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
  }

  async function add(target: Target) {
    const book = toBook(result);
    try {
      if (target === 'library') await addToLibrary({ data: { book, status: 'OWNED' } });
      else await addToWishlist({ data: { book, priority: 'SOON' } });
      setAdded(target);
      refreshAfterAdd();
    } catch {
      setAddError(t('discover.errors.addFailed'));
    }
  }

  return (
    <>
      <div className={styles.coverRow}>
        <Cover variant="hero" title={title} imageUrl={result.coverUrl} />
      </div>

      <div className={styles.heading}>
        <span className={styles.kind}>{t(KIND_LABEL_KEY[kind])}</span>
        <h1 className={styles.title}>{title}</h1>
        {result.authors && (
          <div className={styles.authors}>
            <AuthorNames text={result.authors} />
          </div>
        )}
      </div>

      <div className={styles.facts}>
        <Fact label={t('detail.pages')} value={result.pageCount != null ? String(result.pageCount) : '—'} />
        <Fact label={t('detail.series')} value={seriesLabel(result, t)} />
        <Fact label={t('detail.released')} value={result.year != null ? String(result.year) : '—'} />
      </div>

      {meta && <p className={styles.meta}>{meta}</p>}

      {result.synopsis && (
        <>
          <h2 className={styles.sectionTitle}>{t('detail.summary')}</h2>
          <p className={styles.synopsis}>{result.synopsis}</p>
        </>
      )}

      {addError && <ErrorState message={addError} />}

      {added ? (
        <p className={styles.added}>
          {t(added === 'library' ? 'discover.addedToLibrary' : 'discover.addedToWishlist')}
        </p>
      ) : (
        <div className={styles.actions}>
          <Button onClick={() => void add('library')}>
            <Icon name="add" size={18} color="var(--on-accent)" />
            {t('catalog.addToLibrary')}
          </Button>
          <Button variant="secondary" onClick={() => void add('wishlist')}>
            <Icon name="favorite" size={18} color="var(--rose)" />
            {t('catalog.addToWishlist')}
          </Button>
        </div>
      )}
    </>
  );
}

export function CatalogDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  // The result is handed over in navigation state from wherever it was picked. A fiche
  // reached cold — a shared link, a reload — has none, since the catalog is browsed, not
  // stored: that case sends the reader back to the search rather than showing a blank page.
  const result = (location.state as { result?: CatalogResult } | null)?.result;

  return (
    <Screen className={styles.screen}>
      <div className={styles.backRow}>
        <button onClick={() => navigate(-1)} aria-label={t('common.back')} className={styles.backButton}>
          <Icon name="arrow_back" size={24} color="var(--ink)" />
        </button>
      </div>

      <LoginGate prompt={t('auth.prompts.discover')}>
        {result ? (
          <CatalogDetailContent result={result} />
        ) : (
          <EmptyState
            icon="search"
            title={t('catalog.gone.title')}
            description={t('catalog.gone.description')}
            action={
              <Button variant="secondary" onClick={() => navigate('/discover')}>
                {t('catalog.gone.action')}
              </Button>
            }
          />
        )}
      </LoginGate>
    </Screen>
  );
}

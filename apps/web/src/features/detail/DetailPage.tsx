import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { LoginGate } from '../../shared/LoginGate';
import { apiErrorStatus } from '../../shared/apiClient';
import { Icon } from '../../shared/ui/Icon';
import { Cover } from '../../shared/ui/Cover';
import { colorFor } from '../../shared/ui/coverPalette';
import { RANK_COLORS, type RankCode } from '../../shared/ui/ranks';
import { Button } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import {
  getGetApiLibraryIdQueryKey,
  getGetApiLibraryQueryKey,
  getGetApiStatsQueryKey,
  useGetApiCategories,
  useGetApiLibraryId,
  usePutApiLibraryIdProgress,
  usePutApiLibraryIdRank,

} from '../../api/generated/librarius';
import styles from './DetailPage.module.css';

/** Opacity suffixes of the wash drawn behind the top of the screen. */
const WASH_FROM = 'aa';
const WASH_TO = '00';

/** Opacity suffix of the selected rank's background. */
const RANK_TINT = '22';

function DetailContent({ id }: { id: string }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // One request for one title. The collection is paginated, so the item is no longer
  // guaranteed to be in a cached page — and a deep link never had it. Its own cache
  // entry also means the screen keeps working when the user reloads on this URL.
  const { data: item = null, isPending: loading, isError, error, refetch } = useGetApiLibraryId(id);
  const { data: cats = [] } = useGetApiCategories();

  const invalidateLibrary = () => {
    // The item has its own cache entry, whose key is not a prefix of the collection's:
    // invalidating the list alone would leave this very screen showing a stale rank.
    void queryClient.invalidateQueries({ queryKey: getGetApiLibraryIdQueryKey(id) });
    void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
    void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
  };

  const { mutate: mutateRank } = usePutApiLibraryIdRank({
    mutation: { onSuccess: invalidateLibrary },
  });
  const { mutate: mutateProgress } = usePutApiLibraryIdProgress({
    mutation: { onSuccess: invalidateLibrary },
  });

  function assignRank(categoryId?: string) {
    mutateRank({ id, data: { categoryId } });
  }

  function setStatus(status: 'READING' | 'READ') {
    mutateProgress({ id, data: { status, percent: status === 'READ' ? 100 : undefined } });
  }

  if (loading) return <Loading />;

  // A 404 is the normal answer for an unknown identifier — or for one belonging to
  // another user. That is an absence, not an outage: no point offering a retry.
  const notFound = apiErrorStatus(error) === 404;
  if (isError && !notFound) {
    return <ErrorState message={t('detail.error')} onRetry={() => void refetch()} />;
  }
  if (!item) {
    return (
      <EmptyState
        icon="search_off"
        className={styles.notFound}
        title={t('detail.notFound')}
        action={
          <Button variant="secondary" onClick={() => navigate(-1)}>
            {t('common.back')}
          </Button>
        }
      />
    );
  }

  const b = item.book!;
  const title = b.title ?? '—';
  const color = colorFor(title);
  const ranks = cats.filter((c) => ['or', 'argent', 'bronze'].includes(c.code ?? ''));

  return (
    <div className={styles.page}>
      {/* The wash is tinted with the cover colour, so it lives on the element. */}
      <div
        className={styles.wash}
        style={{ background: `linear-gradient(180deg, ${color}${WASH_FROM}, ${color}${WASH_TO})` }}
      />
      <div className={styles.body}>
        <div className={styles.backRow}>
          <button onClick={() => navigate(-1)} aria-label={t('common.back')} className={styles.backButton}>
            <Icon name="arrow_back" size={24} color="var(--overlay-ink)" />
          </button>
        </div>

        <div className={styles.coverRow}>
          <Cover variant="hero" title={title} imageUrl={b.coverUrl} />
        </div>

        <div className={styles.heading}>
          <h2 className={styles.title}>{title}</h2>
          <div className={styles.authors}>{b.authors}</div>
          <div className={styles.genres}>
            {b.genres || t(b.kind === 'MANGA' ? 'detail.kind.manga' : 'detail.kind.book')}
          </div>
        </div>

        <div className={styles.stats}>
          <Stat value={b.pageCount != null ? String(b.pageCount) : '—'} label={t('detail.pages')} />
          <Stat value={b.seriesTitle || t('detail.standalone')} label={t('detail.series')} grow />
          <Stat value={b.originalYear != null ? String(b.originalYear) : '—'} label={t('detail.released')} last />
        </div>

        {b.synopsis && (
          <>
            <h3 className={styles.sectionTitle}>{t('detail.summary')}</h3>
            <p className={styles.synopsis}>{b.synopsis}</p>
          </>
        )}

        <h3 className={styles.rankTitle}>{t('detail.ranking')}</h3>
        <div className={styles.rankRow}>
          {ranks.map((r) => {
            const on = item.rankCode === r.code;
            const rc = RANK_COLORS[r.code as RankCode];
            return (
              <button
                key={r.id}
                onClick={() => assignRank(on ? undefined : r.id)}
                className={styles.rankButton}
                // The selected state is painted in the rank's own colour.
                style={on ? { borderColor: rc, background: `${rc}${RANK_TINT}` } : undefined}
              >
                <span className={styles.rankDot} style={{ background: rc }} />
                {r.label}
              </button>
            );
          })}
        </div>

        <div className={styles.actions}>
          {item.status !== 'READ' && (
            <Button variant="primary" size="lg" onClick={() => setStatus('READING')}>
              <Icon name="auto_stories" size={20} fill color="var(--on-accent)" />
              {t(item.status === 'READING' ? 'detail.reading' : 'detail.startReading')}
            </Button>
          )}
          <Button variant="secondary" size="block" onClick={() => setStatus('READ')}>
            {t(item.status === 'READ' ? 'detail.read' : 'detail.markAsRead')}
          </Button>
        </div>
      </div>
    </div>
  );
}

function Stat({ value, label, grow, last }: { value: string; label: string; grow?: boolean; last?: boolean }) {
  return (
    <div
      className={[styles.stat, grow && styles.statGrow, last && styles.statLast]
        .filter(Boolean)
        .join(' ')}
    >
      <div className={`${styles.statValue} ${grow ? styles.statValueGrow : ''}`}>{value}</div>
      <div className={styles.statLabel}>{label}</div>
    </div>
  );
}

export function DetailPage() {
  const { t } = useTranslation();
  const { id = '' } = useParams();
  return (
    <LoginGate prompt={t('auth.prompts.detail')}>
      <DetailContent id={id} />
    </LoginGate>
  );
}

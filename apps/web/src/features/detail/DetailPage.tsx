import { useState } from 'react';
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
  useGetApiSeries,
  usePutApiLibraryIdProgress,
  usePutApiLibraryIdRank,
  type LibraryItemDto,
  type ProgressDto,
} from '../../api/generated/librarius';
import { seriesIdOf } from '../series/series';
import styles from './DetailPage.module.css';

/** Opacity suffixes of the wash drawn behind the top of the screen. */
const WASH_FROM = 'aa';
const WASH_TO = '00';

/** Opacity suffix of the selected rank's background. */
const RANK_TINT = '22';

/** What the progress form holds while it is being edited — strings, as inputs give them. */
interface ProgressDraft {
  page: string;
  percent: string;
  startedAt: string;
  finishedAt: string;
}

const EMPTY_DRAFT: ProgressDraft = { page: '', percent: '', startedAt: '', finishedAt: '' };

function draftOf(item: LibraryItemDto): ProgressDraft {
  const p = item.progress;
  if (!p) return EMPTY_DRAFT;
  return {
    page: p.currentPage != null ? String(p.currentPage) : '',
    percent: p.percent != null ? String(p.percent) : '',
    startedAt: p.startedAt ?? '',
    finishedAt: p.finishedAt ?? '',
  };
}

/** The position as the API takes it: an empty field is the absence of a value, not a zero. */
function payloadOf(draft: ProgressDraft): ProgressDto {
  return {
    currentPage: draft.page === '' ? undefined : Number(draft.page),
    percent: draft.percent === '' ? undefined : Number(draft.percent),
    startedAt: draft.startedAt || undefined,
    finishedAt: draft.finishedAt || undefined,
  };
}

function clampPercent(value: number): number {
  return Math.max(0, Math.min(100, value));
}

/**
 * The two halves of a position, each derived from the other. Page 120 of a 300-page book
 * is 40 %, and the field the user is not typing in follows along — the server applies the
 * very same rule on save, so what is shown while typing is what ends up stored.
 */
function percentFrom(page: string, total: number | null): string {
  if (page === '' || total == null || total <= 0) return '';
  return String(clampPercent(Math.round((Number(page) * 100) / total)));
}

function pageFrom(percent: string, total: number | null): string {
  if (percent === '' || total == null || total <= 0) return '';
  return String(Math.round((clampPercent(Number(percent)) * total) / 100));
}

/**
 * Where the reader stands, and the two ways of saying it.
 *
 * <p>The form is seeded from the item and re-seeded whenever the server value changes —
 * after a save, or after the status buttons complete a book. An identical refetch leaves
 * what is being typed alone.
 */
function ProgressSection({
  item,
  onSave,
}: {
  item: LibraryItemDto;
  onSave: (data: ProgressDto) => void;
}) {
  const { t } = useTranslation();
  const total = item.book?.pageCount ?? null;

  const signature = JSON.stringify(item.progress ?? null);
  const [draft, setDraft] = useState(() => draftOf(item));
  const [synced, setSynced] = useState(signature);
  if (synced !== signature) {
    setSynced(signature);
    setDraft(draftOf(item));
  }

  const percent = draft.percent === '' ? 0 : clampPercent(Number(draft.percent));
  const summary = draft.percent === ''
    ? t('detail.progress.summaryEmpty')
    : total != null
      ? t('detail.progress.summary', { percent, page: draft.page || 0, total })
      : t('detail.progress.summaryPercent', { percent });

  return (
    <section className={styles.progress}>
      <h3 className={styles.sectionTitle}>{t('detail.progress.title')}</h3>

      {/* The fill is the value itself, so its width can only be inline. */}
      <div className={styles.progressTrack}>
        <div className={styles.progressFill} style={{ width: `${percent}%` }} />
      </div>
      <div className={styles.progressSummary}>{summary}</div>

      <div className={styles.fieldRow}>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>{t('detail.progress.page')}</span>
          <span className={styles.fieldInputRow}>
            <input
              type="number"
              min={0}
              max={total ?? undefined}
              value={draft.page}
              aria-label={t('detail.progress.page')}
              className={styles.fieldInput}
              onChange={(e) =>
                setDraft((d) => ({
                  ...d,
                  page: e.target.value,
                  percent: total != null ? percentFrom(e.target.value, total) : d.percent,
                }))
              }
            />
            {total != null && (
              <span className={styles.fieldUnit}>{t('detail.progress.outOf', { total })}</span>
            )}
          </span>
        </label>

        <label className={styles.field}>
          <span className={styles.fieldLabel}>{t('detail.progress.percent')}</span>
          <span className={styles.fieldInputRow}>
            <input
              type="number"
              min={0}
              max={100}
              value={draft.percent}
              aria-label={t('detail.progress.percent')}
              className={styles.fieldInput}
              onChange={(e) =>
                setDraft((d) => ({
                  ...d,
                  percent: e.target.value,
                  page: total != null ? pageFrom(e.target.value, total) : d.page,
                }))
              }
            />
            <span className={styles.fieldUnit}>{t('detail.progress.percentUnit')}</span>
          </span>
        </label>
      </div>

      <div className={styles.fieldRow}>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>{t('detail.progress.startedAt')}</span>
          <input
            type="date"
            value={draft.startedAt}
            aria-label={t('detail.progress.startedAt')}
            className={styles.fieldInput}
            onChange={(e) => setDraft((d) => ({ ...d, startedAt: e.target.value }))}
          />
        </label>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>{t('detail.progress.finishedAt')}</span>
          <input
            type="date"
            value={draft.finishedAt}
            aria-label={t('detail.progress.finishedAt')}
            className={styles.fieldInput}
            onChange={(e) => setDraft((d) => ({ ...d, finishedAt: e.target.value }))}
          />
        </label>
      </div>

      <Button variant="secondary" size="block" onClick={() => onSave(payloadOf(draft))}>
        {t('detail.progress.save')}
      </Button>
    </section>
  );
}

function DetailContent({ id }: { id: string }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // One request for one title. The collection is paginated, so the item is no longer
  // guaranteed to be in a cached page — and a deep link never had it. Its own cache
  // entry also means the screen keeps working when the user reloads on this URL.
  const { data: item = null, isPending: loading, isError, error, refetch } = useGetApiLibraryId(id);
  const { data: cats = [] } = useGetApiCategories();
  // The series a volume belongs to, when the user has one: `BookView` carries the series
  // title but no identifier, so the link is resolved against their own series.
  const { data: knownSeries = [] } = useGetApiSeries();
  const seriesId = seriesIdOf(knownSeries, item?.book);

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

  /**
   * Flips the status, handing the stored position back untouched: the payload replaces
   * the progress as a whole, so dropping it here would wipe the dates the user entered.
   * The server fills in what the transition implies — the start date, or 100 % and the
   * finish date.
   */
  function setStatus(status: 'READING' | 'READ') {
    const p = item?.progress;
    mutateProgress({
      id,
      data: {
        status,
        currentPage: p?.currentPage,
        percent: p?.percent,
        startedAt: p?.startedAt,
        finishedAt: p?.finishedAt,
      },
    });
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
  // A title nobody has opened has nothing to show yet; the buttons below start it.
  const tracking = item.status !== 'OWNED' || item.progress != null;

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
          <Stat
            value={b.seriesTitle || t('detail.standalone')}
            label={t('detail.series')}
            grow
            // Only a series the user has a stake in has a screen to open.
            onClick={seriesId ? () => navigate(`/series/${seriesId}`) : undefined}
          />
          <Stat value={b.originalYear != null ? String(b.originalYear) : '—'} label={t('detail.released')} last />
        </div>

        {b.synopsis && (
          <>
            <h3 className={styles.sectionTitle}>{t('detail.summary')}</h3>
            <p className={styles.synopsis}>{b.synopsis}</p>
          </>
        )}

        {tracking && (
          <ProgressSection item={item} onSave={(data) => mutateProgress({ id, data })} />
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

/** One cell of the stat strip. With an `onClick` it becomes the way into that stat. */
function Stat({
  value,
  label,
  grow,
  last,
  onClick,
}: {
  value: string;
  label: string;
  grow?: boolean;
  last?: boolean;
  onClick?: () => void;
}) {
  const className = [styles.stat, grow && styles.statGrow, last && styles.statLast]
    .filter(Boolean)
    .join(' ');
  const body = (
    <>
      <div className={`${styles.statValue} ${grow ? styles.statValueGrow : ''}`}>{value}</div>
      <div className={styles.statLabel}>{label}</div>
    </>
  );

  if (!onClick) return <div className={className}>{body}</div>;
  return (
    <button type="button" onClick={onClick} className={`${className} ${styles.statLink}`}>
      {body}
    </button>
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

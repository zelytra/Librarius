import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
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
  getGetApiWorksIdEditionsQueryKey,
  useGetApiCategories,
  useGetApiLibraryId,
  useGetApiSeries,
  useGetApiWorksIdEditions,
  usePutApiLibraryIdEdition,
  usePutApiLibraryIdProgress,
  usePutApiLibraryIdRank,
  usePutApiLibraryIdReview,
  type EditionDto,
  type LibraryItemDto,
  type ProgressDto,
  type ReviewDto,
} from '../../api/generated/librarius';
import { seriesIdOf } from '../series/series';
import styles from './DetailPage.module.css';

/** Opacity suffixes of the wash drawn behind the top of the screen. */
const WASH_FROM = 'aa';
const WASH_TO = '00';

/** Opacity suffix of the selected rank's background. */
const RANK_TINT = '22';

/** The rating is out of five, like everywhere the app shows one. */
const STARS = [1, 2, 3, 4, 5];

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

/**
 * The private half of a title: a rating out of five and free-text notes.
 *
 * <p>Both are saved optimistically — the star fills in as it is clicked rather than after
 * the round trip — and the screen tells the user, in as many words, that neither leaves
 * their account.
 */
function ReviewSection({
  item,
  onSave,
}: {
  item: LibraryItemDto;
  onSave: (data: ReviewDto) => void;
}) {
  const { t } = useTranslation();
  const rating = item.rating ?? 0;
  const stored = item.review ?? '';

  const [review, setReview] = useState(stored);
  const [synced, setSynced] = useState(stored);
  if (synced !== stored) {
    setSynced(stored);
    setReview(stored);
  }

  return (
    <section className={styles.review}>
      <h3 className={styles.sectionTitle}>{t('detail.review.title')}</h3>
      <div className={styles.stars}>
        {STARS.map((n) => (
          <button
            key={n}
            type="button"
            // Clicking the current rating again removes it: there has to be a way back
            // from a rating given by mistake.
            onClick={() =>
              onSave({ rating: n === rating ? undefined : n, review: review || undefined })
            }
            aria-label={n === rating ? t('detail.review.clear') : t('detail.review.star', { rating: n })}
            aria-pressed={n <= rating}
            className={styles.star}
          >
            <Icon
              name="star"
              size={30}
              fill={n <= rating}
              color={n <= rating ? 'var(--gold)' : 'var(--line)'}
            />
          </button>
        ))}
      </div>

      <textarea
        value={review}
        rows={4}
        placeholder={t('detail.review.placeholder')}
        aria-label={t('detail.review.title')}
        className={styles.reviewInput}
        onChange={(e) => setReview(e.target.value)}
        onBlur={() => {
          if (review !== stored) onSave({ rating: item.rating ?? undefined, review: review || undefined });
        }}
      />
      <p className={styles.privateNote}>{t('detail.review.private')}</p>
    </section>
  );
}

/**
 * Language of an edition, named rather than coded: providers hand back `fre`, `fr` or
 * `eng`, which mean nothing on a shelf. An unknown code is shown as it came — better a raw
 * code than a language silently dropped.
 */
function languageName(code: string): string {
  try {
    return new Intl.DisplayNames(['fr'], { type: 'language' }).of(code) ?? code;
  } catch {
    return code;
  }
}

/** Month and year of a release, the precision a reader actually compares editions on. */
function releaseLabel(date: string): string {
  const parsed = new Date(date);
  if (Number.isNaN(parsed.getTime())) return date;
  return new Intl.DateTimeFormat('fr-FR', { year: 'numeric', month: 'long' }).format(parsed);
}

/** The identity of an edition on one line: who published it, in what language and shape. */
function editionLabel(edition: EditionDto, unknown: string): string {
  const parts = [
    edition.publisher,
    edition.language ? languageName(edition.language) : undefined,
    edition.format,
  ].filter(Boolean);
  return parts.length > 0 ? parts.join(' · ') : unknown;
}

/**
 * The other editions of the same work, and the way to say which one is on the shelf.
 *
 * <p>The section only exists when the catalog knows more than one edition — most works are
 * known in a single one, and an "other editions" heading over an empty list says nothing.
 * An edition already in the collection is named as such instead of being offered: owning
 * the same edition twice is what `UNIQUE(user, edition)` forbids, and a button the server
 * would refuse is worse than no button.
 */
function EditionsSection({
  item,
  onChoose,
  error,
}: {
  item: LibraryItemDto;
  onChoose: (editionId: string) => void;
  error: string | null;
}) {
  const { t } = useTranslation();
  // The hook disables itself on an empty identifier, so a book whose payload predates
  // `workId` simply shows no section rather than firing a request for `/works//editions`.
  const { data: editions = [] } = useGetApiWorksIdEditions(item.book?.workId ?? '');

  const current = item.book?.editionId;
  const others = editions.filter((edition) => edition.id !== current);
  if (others.length === 0) return null;

  const mine = editions.find((edition) => edition.id === current);
  const unknown = t('detail.editions.unknownPublisher');

  return (
    <section className={styles.editions}>
      <h3 className={styles.sectionTitle}>{t('detail.editions.title')}</h3>
      {mine && (
        <p className={styles.editionsHint}>
          {t('detail.editions.yours', { edition: editionLabel(mine, unknown) })}
        </p>
      )}

      <ul className={styles.editionList}>
        {others.map((edition) => {
          const label = editionLabel(edition, unknown);
          const meta = [
            edition.pageCount != null
              ? t('detail.editions.pages', { pages: edition.pageCount })
              : undefined,
            edition.releaseDate ? releaseLabel(edition.releaseDate) : undefined,
            edition.isbn13 ? t('detail.editions.isbn', { isbn: edition.isbn13 }) : undefined,
          ].filter(Boolean);

          return (
            <li key={edition.id} className={styles.edition}>
              <div className={styles.editionLabel}>{label}</div>
              {meta.length > 0 && <div className={styles.editionMeta}>{meta.join(' · ')}</div>}
              {edition.owned ? (
                <div className={styles.editionOwned}>{t('detail.editions.alreadyOwned')}</div>
              ) : (
                <Button
                  variant="secondary"
                  size="block"
                  aria-label={t('detail.editions.chooseAria', { edition: label })}
                  onClick={() => onChoose(edition.id!)}
                >
                  {t('detail.editions.choose')}
                </Button>
              )}
            </li>
          );
        })}
      </ul>

      {error && <p className={styles.editionsError}>{error}</p>}
      {/* Says what the switch does to the position, because it is not obvious: a page
          number does not survive a change of pagination, a percentage does. */}
      <p className={styles.editionsNote}>{t('detail.editions.keepsProgress')}</p>
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

  const workId = item?.book?.workId;
  const [editionError, setEditionError] = useState<string | null>(null);

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
  const { mutate: mutateReview } = usePutApiLibraryIdReview({
    mutation: {
      // Painted before the round trip: a star that only lights up once the server has
      // answered feels broken on a slow connection. The invalidation then reconciles
      // with what was actually stored.
      onMutate: ({ data }) => {
        queryClient.setQueryData<LibraryItemDto>(getGetApiLibraryIdQueryKey(id), (prev) =>
          prev ? { ...prev, rating: data.rating, review: data.review } : prev);
      },
      onSettled: invalidateLibrary,
    },
  });

  const { mutate: mutateEdition } = usePutApiLibraryIdEdition({
    mutation: {
      onSuccess: () => {
        setEditionError(null);
        invalidateLibrary();
        // The `owned` flags of the list have just moved, and so has the edition the
        // section calls "yours".
        if (workId) {
          void queryClient.invalidateQueries({
            queryKey: getGetApiWorksIdEditionsQueryKey(workId),
          });
        }
      },
      // A 409 is the one refusal the user can act on: that edition is already on their
      // shelf, under another entry. Anything else is an outage, and says so.
      onError: (failure) =>
        setEditionError(t(apiErrorStatus(failure) === 409
          ? 'detail.editions.alreadyOwnedError'
          : 'detail.editions.error')),
    },
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

        <EditionsSection
          item={item}
          error={editionError}
          onChoose={(editionId) => mutateEdition({ id, data: { editionId } })}
        />

        {tracking && (
          <ProgressSection item={item} onSave={(data) => mutateProgress({ id, data })} />
        )}

        <ReviewSection item={item} onSave={(data) => mutateReview({ id, data })} />

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

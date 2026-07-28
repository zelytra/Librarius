import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useQueryClient } from '@tanstack/react-query';
import { LoginGate } from '../../shared/LoginGate';
import { apiErrorStatus } from '../../shared/apiClient';
import { Icon } from '../../shared/ui/Icon';
import { Cover } from '../../shared/ui/Cover';
import { colorFor } from '../../shared/ui/coverPalette';
import { Button } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import {
  getGetApiLibraryQueryKey,
  getGetApiSeriesIdQueryKey,
  getGetApiSeriesQueryKey,
  getGetApiStatsQueryKey,
  getGetApiWishlistQueryKey,
  useDeleteApiSeriesIdFollow,
  useGetApiSeriesId,
  usePostApiLibrary,
  usePostApiWishlist,
  usePutApiSeriesIdFollow,
  type ManualBookDto,
  type SeriesDetailDto,
  type SeriesVolumeDto,
} from '../../api/generated/librarius';
import { missingVolumes, runLength, volumeState, type VolumeState } from './series';
import styles from './SeriesPage.module.css';

/** Opacity suffixes of the wash drawn behind the top of the screen, as on Detail. */
const WASH_FROM = 'aa';
const WASH_TO = '00';

/** Volume numbers listed in the "missing volumes" line before it is cut short. */
const MISSING_PREVIEW = 12;

/**
 * How the four states are told apart **without reading anything**: each one owns a fill
 * and an icon, and the two missing-from-the-run states are outlined rather than solid.
 * Colour alone would leave a colour-blind reader with a grid of identical squares, and
 * the pale tints of the light palettes are close to one another by design.
 */
const VOLUME_VISUALS: Record<VolumeState, { cell: string; icon: string; fill: boolean }> = {
  read: { cell: styles.read, icon: 'check_circle', fill: true },
  owned: { cell: styles.owned, icon: 'book_2', fill: true },
  missing: { cell: styles.missing, icon: 'priority_high', fill: false },
  upcoming: { cell: styles.upcoming, icon: 'schedule', fill: false },
};

/** The states, in the order the legend lists them. */
const LEGEND: VolumeState[] = ['read', 'owned', 'missing', 'upcoming'];

/** Where an "add" lands. The wording differs, the payload does not. */
type AddTarget = 'library' | 'wishlist';

function SeriesContent({ id }: { id: string }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data, isPending: loading, isError, error, refetch } = useGetApiSeriesId(id);

  // The volume the action panel is open on. A missing or upcoming volume is not in the
  // collection yet, so it has nothing to open: it offers the two ways of getting it.
  const [selected, setSelected] = useState<SeriesVolumeDto | null>(null);
  // Volumes sent to the wishlist during this session. `SeriesVolumeDto` carries no
  // "wished" flag, so the server cannot tell us; without this the user would have no
  // feedback that the volume they just asked for is on its way.
  const [wished, setWished] = useState<Record<number, true>>({});
  const [addError, setAddError] = useState<string | null>(null);

  const invalidateSeries = () => {
    void queryClient.invalidateQueries({ queryKey: getGetApiSeriesIdQueryKey(id) });
    // The counters of the Series view of the collection move with it.
    void queryClient.invalidateQueries({ queryKey: getGetApiSeriesQueryKey() });
  };

  const { mutate: follow } = usePutApiSeriesIdFollow({ mutation: { onSuccess: invalidateSeries } });
  const { mutate: unfollow } = useDeleteApiSeriesIdFollow({ mutation: { onSuccess: invalidateSeries } });
  const { mutateAsync: addToLibrary } = usePostApiLibrary();
  const { mutateAsync: addToWishlist } = usePostApiWishlist();

  if (loading) return <Loading />;

  // A 404 is the normal answer for an unknown series — and for one the user neither owns
  // a volume of nor follows. That is an absence, not an outage: nothing to retry.
  const notFound = apiErrorStatus(error) === 404;
  if (isError && !notFound) {
    return <ErrorState message={t('series.error')} onRetry={() => void refetch()} />;
  }
  if (!data) {
    return (
      <EmptyState
        icon="search_off"
        className={styles.notFound}
        title={t('series.notFound')}
        action={
          <Button variant="secondary" onClick={() => navigate(-1)}>
            {t('common.back')}
          </Button>
        }
      />
    );
  }

  // Bound once it is known to be there, so the callbacks below read it without a guard.
  const series = data;
  const title = series.title ?? '—';
  const volumes = series.volumes ?? [];
  const owned = series.ownedCount ?? 0;
  const read = series.readCount ?? 0;
  const total = runLength(series.totalVolumes, volumes);
  const percent = total > 0 ? Math.min(100, Math.round((owned / total) * 100)) : 0;
  const missing = missingVolumes(volumes);
  const truncated = missing.length > MISSING_PREVIEW;

  /**
   * The entry created for a volume that is not in the collection yet. `seriesTitle` is
   * what attaches the new work to this very series, so the volume lands in this grid.
   */
  function bookOf(volume: SeriesVolumeDto): ManualBookDto {
    return {
      kind: series.kind === 'MANGA' ? 'MANGA' : 'BOOK',
      title: volume.title ?? t('series.volumeTitle', { series: title, number: volume.volumeNumber }),
      seriesTitle: series.title,
      volumeNumber: volume.volumeNumber,
    };
  }

  async function add(volume: SeriesVolumeDto, target: AddTarget) {
    setAddError(null);
    try {
      if (target === 'library') {
        await addToLibrary({ data: { book: bookOf(volume), status: 'OWNED' } });
        // The volume is now owned: the grid, the counters and the collection all move.
        invalidateSeries();
        void queryClient.invalidateQueries({ queryKey: getGetApiLibraryQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
      } else {
        await addToWishlist({ data: { book: bookOf(volume), priority: 'SOON' } });
        void queryClient.invalidateQueries({ queryKey: getGetApiWishlistQueryKey() });
        if (volume.volumeNumber != null) {
          setWished((w) => ({ ...w, [volume.volumeNumber!]: true }));
        }
      }
      setSelected(null);
    } catch {
      setAddError(t('series.addFailed'));
    }
  }

  function openVolume(volume: SeriesVolumeDto) {
    // A failure belongs to the volume it happened on, not to the next one picked.
    setAddError(null);
    // An owned volume already has its screen; the rest opens the action panel.
    if (volume.libraryItemId) navigate(`/detail/${volume.libraryItemId}`);
    else setSelected(volume);
  }

  return (
    <div className={styles.page}>
      {/* The wash is tinted with the cover colour, so it lives on the element. */}
      <div
        className={styles.wash}
        style={{ background: `linear-gradient(180deg, ${colorFor(title)}${WASH_FROM}, ${colorFor(title)}${WASH_TO})` }}
      />
      <div className={styles.body}>
        <div className={styles.backRow}>
          <button onClick={() => navigate(-1)} aria-label={t('common.back')} className={styles.backButton}>
            <Icon name="arrow_back" size={24} color="var(--overlay-ink)" />
          </button>
        </div>

        <div className={styles.coverRow}>
          <Cover variant="hero" title={title} imageUrl={series.coverUrl} />
        </div>

        <div className={styles.heading}>
          <h2 className={styles.title}>{title}</h2>
          <div className={styles.meta}>
            <span className={styles.status}>{statusLabel(series, t)}</span>
          </div>
        </div>

        <div className={styles.progress}>
          <div className={styles.progressHead}>
            <span className={styles.progressValue}>
              {total > 0
                ? t('series.volumesOfTotal', { owned, total })
                : t('series.volumesOwned', { count: owned })}
            </span>
            <span className={styles.progressRead}>{t('series.readCount', { count: read })}</span>
          </div>
          <div className={styles.track}>
            {/* The width is the progress itself. */}
            <div className={styles.bar} style={{ width: `${percent}%` }} />
          </div>
        </div>

        <div className={styles.followRow}>
          <Button
            variant={series.followed ? 'secondary' : 'primary'}
            size="block"
            onClick={() => (series.followed ? unfollow({ id }) : follow({ id }))}
          >
            <Icon
              name={series.followed ? 'bookmark_added' : 'bookmark_add'}
              size={20}
              fill={series.followed}
              color={series.followed ? 'var(--accent-deep)' : 'var(--on-accent)'}
            />
            {t(series.followed ? 'series.following' : 'series.follow')}
          </Button>
        </div>

        {series.synopsis && <p className={styles.synopsis}>{series.synopsis}</p>}

        <h3 className={styles.sectionTitle}>{t('series.volumesTitle')}</h3>

        <div className={styles.legend}>
          {LEGEND.map((state) => (
            <span key={state} className={`${styles.legendItem} ${VOLUME_VISUALS[state].cell}`}>
              <Icon name={VOLUME_VISUALS[state].icon} size={13} fill={VOLUME_VISUALS[state].fill} />
              {t(`series.states.${state}`)}
            </span>
          ))}
        </div>

        {volumes.length === 0 ? (
          <EmptyState
            icon="library_books"
            className={styles.emptyVolumes}
            title={t('series.emptyVolumes.title')}
            description={t('series.emptyVolumes.description')}
          />
        ) : (
          <div className={styles.grid}>
            {volumes.map((volume, index) => (
              <VolumeCell
                key={volume.volumeNumber ?? `unnumbered-${index}`}
                volume={volume}
                wished={volume.volumeNumber != null && wished[volume.volumeNumber] === true}
                onClick={() => openVolume(volume)}
              />
            ))}
          </div>
        )}

        {missing.length > 0 && (
          <p className={styles.missingSummary}>
            <Icon name="priority_high" size={16} color="var(--tint-rose-ink)" />
            {t(truncated ? 'series.missingListTruncated' : 'series.missingList', {
              volumes: missing.slice(0, MISSING_PREVIEW).join(', '),
            })}
          </p>
        )}

        {addError && <ErrorState message={addError} />}

        {selected && (
          <div className={styles.actions}>
            <div className={styles.actionsHead}>
              <span className={styles.actionsTitle}>
                {selected.volumeNumber != null
                  ? t('series.volume', { number: selected.volumeNumber })
                  : (selected.title ?? t('series.unnumbered'))}
              </span>
              <button
                onClick={() => {
                  setSelected(null);
                  setAddError(null);
                }}
                aria-label={t('series.closeActions')}
                className={styles.closeButton}
              >
                <Icon name="close" size={18} color="var(--faint)" />
              </button>
            </div>
            <div className={styles.actionsButtons}>
              <Button variant="primary" onClick={() => void add(selected, 'wishlist')}>
                <Icon name="favorite" size={18} fill color="var(--on-accent)" />
                {t('series.addToWishlist')}
              </Button>
              <Button variant="secondary" onClick={() => void add(selected, 'library')}>
                <Icon name="add" size={18} color="var(--accent-deep)" />
                {t('series.addToCollection')}
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/** One cell of the grid: a state, a number, and what can be done about it. */
function VolumeCell({
  volume,
  wished,
  onClick,
}: {
  volume: SeriesVolumeDto;
  wished: boolean;
  onClick: () => void;
}) {
  const { t } = useTranslation();
  const state = volumeState(volume);
  const visual = VOLUME_VISUALS[state];
  const stateLabel = t(`series.states.${state}`);
  // The number alone would leave a screen reader announcing "3" on a grid of squares.
  const label =
    volume.volumeNumber != null
      ? t(wished ? 'series.volumeLabelWished' : 'series.volumeLabel', {
          number: volume.volumeNumber,
          state: stateLabel,
        })
      : t('series.unnumberedLabel', { title: volume.title ?? '', state: stateLabel });

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className={`${styles.cell} ${visual.cell}`}
    >
      <Icon name={visual.icon} size={15} fill={visual.fill} />
      <span className={styles.cellNumber}>
        {volume.volumeNumber != null ? volume.volumeNumber : t('series.unnumberedShort')}
      </span>
      {wished && <Icon name="favorite" size={11} fill style={{ color: 'var(--rose)' }} />}
    </button>
  );
}

/** Publication status, or the kind of the series when the catalog does not know it. */
function statusLabel(series: SeriesDetailDto, t: TFunction): string {
  if (series.status === 'ONGOING' || series.status === 'COMPLETED' || series.status === 'HIATUS') {
    return t(`series.status.${series.status}`);
  }
  return t(series.kind === 'MANGA' ? 'detail.kind.manga' : 'detail.kind.book');
}

export function SeriesPage() {
  const { t } = useTranslation();
  const { id = '' } = useParams();
  return (
    <LoginGate prompt={t('auth.prompts.series')}>
      <SeriesContent id={id} />
    </LoginGate>
  );
}

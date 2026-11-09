import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Cover } from '../../shared/ui/Cover';
import type { SeriesSummaryDto } from '../../api/generated/librarius';
import { completion } from './series';
import styles from './SeriesBlock.module.css';

/**
 * A whole series as one block of the collection grid: the run is one object on the shelf,
 * the way it stands on a real one, rather than its volumes scattered among the one-shots.
 *
 * <p>The cover is the series' own, or its first owned volume's; two sheets drawn behind it
 * say "several books" at a glance, and the bar under it how much of the run is owned (or,
 * when the length of the run is unknown, how much of what is owned has been read). Opening
 * it lists every volume — owned, read, and the ones still missing.
 */
export function SeriesBlock({ series }: { series: SeriesSummaryDto }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const owned = series.ownedCount ?? 0;
  const read = series.readCount ?? 0;
  const total = series.totalVolumes ?? 0;
  const share = total > 0 ? completion(series) : owned > 0 ? read / owned : 0;
  const caption =
    total > 0
      ? t('series.block.ofTotal', { owned, total, read })
      : t('series.block.owned', { count: owned, read });

  return (
    <div className={styles.block}>
      <div className={styles.stack} aria-hidden="true" />
      <Cover
        variant="tile"
        title={series.title ?? '—'}
        imageUrl={series.coverUrl}
        tag={t('series.block.tag')}
        caption={caption}
        onClick={() => navigate(`/series/${series.id}`)}
      >
        <span className={styles.badge}>{t('series.block.badge', { count: owned })}</span>
        <div className={styles.track} aria-hidden="true">
          {/* The width is a share of the run, computed at render time. */}
          <div className={styles.bar} style={{ width: `${Math.round(share * 100)}%` }} />
        </div>
      </Cover>
    </div>
  );
}

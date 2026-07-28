import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { colorFor } from '../../shared/ui/coverPalette';
import type { SeriesSummaryDto } from '../../api/generated/librarius';
import { completion, isIncomplete } from './series';
import styles from './SeriesList.module.css';

/**
 * The Series view of the collection: one row per series, how far its run has got, and
 * whether volumes are still missing from it. A collector with four hundred volumes reads
 * twenty-five rows here instead of scrolling four hundred covers.
 */
export function SeriesList({ series }: { series: SeriesSummaryDto[] }) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <div className={styles.list}>
      {series.map((s) => {
        const title = s.title ?? '—';
        const owned = s.ownedCount ?? 0;
        const total = s.totalVolumes ?? 0;
        const incomplete = isIncomplete(s);
        return (
          <button key={s.id} onClick={() => navigate(`/series/${s.id}`)} className={styles.row}>
            <div
              className={styles.thumb}
              // Either the real cover, or a colour derived from the title.
              style={{
                background: s.coverUrl
                  ? `center/cover no-repeat url(${s.coverUrl})`
                  : colorFor(title),
              }}
            />
            <div className={styles.body}>
              <div className={styles.titleRow}>
                <span className={styles.name}>{title}</span>
                {incomplete && (
                  <span className={styles.badge}>
                    <Icon name="priority_high" size={12} color="var(--tint-rose-ink)" />
                    {t('series.incomplete')}
                  </span>
                )}
              </div>
              <div className={styles.count}>
                {total > 0
                  ? t('series.volumesOfTotal', { owned, total })
                  : t('series.volumesOwned', { owned })}
              </div>
              {total > 0 && (
                <div className={styles.track}>
                  {/* The width is the share of the run already owned. */}
                  <div
                    className={`${styles.bar} ${incomplete ? '' : styles.barComplete}`}
                    style={{ width: `${Math.round(completion(s) * 100)}%` }}
                  />
                </div>
              )}
            </div>
            <Icon name="chevron_right" size={22} color="var(--faint)" />
          </button>
        );
      })}
    </div>
  );
}

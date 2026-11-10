import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Cover } from '../../shared/ui/Cover';
import { Icon } from '../../shared/ui/Icon';
import { Button } from '../../shared/ui/primitives';
import { AuthorNames } from '../author/AuthorNames';
import type { LibraryItemDto, StatsDto } from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';
import { pagesRead } from '../../shared/pagesRead';
import styles from './HomeShowcase.module.css';

/** Covers drawn in the shelf of the showcase: enough to read as a library, few enough to load. */
const SHOWCASE_COVERS = 7;

/**
 * The top of Home: the library shown off rather than listed. A fan of the latest covers next
 * to what the library adds up to, the two ways in (add a book, open the collection), and the
 * book being read now, large, with where the reader stands and the button to carry on.
 *
 * <p>Above the configurable sections rather than one of them: it is the page's front, not a
 * shelf to reorder. It draws nothing on an empty library, which the onboarding greets instead.
 */
export function HomeShowcase({
  reading,
  read,
  stats,
}: {
  reading: LibraryItemDto[];
  read: LibraryItemDto[];
  stats: StatsDto | undefined;
}) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  // The shelves Home already fetched, covers only — a fan of coloured placeholders shows
  // nothing off, and a request of its own would download the collection to pick seven.
  const covers = [...reading, ...read].filter((it) => it.book?.coverUrl).slice(0, SHOWCASE_COVERS);
  const total = (stats?.read ?? 0) + (stats?.reading ?? 0) + (stats?.toRead ?? 0) + (stats?.abandoned ?? 0);
  const current = reading[0];
  const percent = current?.progress?.percent ?? 0;

  // An estimate while lengths are still being filled in, left out while there is none.
  const pages = pagesRead(stats);
  const figures: { value: number; prefix?: string; label: string }[] = [
    { value: total, label: t('home.showcase.titles', { count: total }) },
    { value: stats?.seriesCount ?? 0, label: t('home.showcase.series', { count: stats?.seriesCount ?? 0 }) },
    ...(pages.unknown
      ? []
      : [{ value: pages.value, prefix: pages.estimated ? '≈ ' : '', label: t('home.showcase.pages', { count: pages.value }) }]),
  ];

  return (
    <section className={styles.showcase}>
      <div className={styles.banner}>
        <div className={styles.text}>
          <h2 className={styles.title}>{t('home.showcase.title')}</h2>
          <div className={styles.figures}>
            {figures.map((f) => (
              <div key={f.label} className={styles.figure}>
                <span className={styles.figureValue}>
                  {f.prefix}
                  {f.value.toLocaleString(activeLanguage())}
                </span>
                <span className={styles.figureLabel}>{f.label}</span>
              </div>
            ))}
          </div>
          <div className={styles.actions}>
            <Button variant="primary" size="compact" onClick={() => navigate('/discover')}>
              <Icon name="add" size={18} color="var(--on-accent)" />
              {t('home.showcase.add')}
            </Button>
            <Button variant="secondary" size="compact" onClick={() => navigate('/collection')}>
              {t('home.showcase.collection')}
            </Button>
          </div>
        </div>
        {covers.length > 0 && (
          <div className={styles.fan} aria-hidden="true">
            {covers.map((it, index) => (
              <div
                key={it.id}
                className={styles.fanCover}
                // Each cover's place in the fan depends on its rank, hence inline.
                style={{ ['--i' as string]: index, ['--n' as string]: covers.length }}
              >
                <Cover variant="tile" title={it.book?.title ?? '—'} imageUrl={it.book?.coverUrl} caption={null} />
              </div>
            ))}
          </div>
        )}
      </div>

      {current && (
        <div className={styles.now}>
          <div className={styles.nowCover}>
            <Cover
              variant="tile"
              title={current.book?.title ?? '—'}
              imageUrl={current.book?.coverUrl}
              caption={null}
              onClick={() => navigate(`/detail/${current.id}`, { state: { item: current } })}
            />
          </div>
          <div className={styles.nowBody}>
            <span className={styles.nowLabel}>{t('home.showcase.now')}</span>
            <h3 className={styles.nowTitle}>{current.book?.title}</h3>
            <span className={styles.nowAuthor}>
              <AuthorNames text={current.book?.authors} />
            </span>
            <div
              className={styles.nowTrack}
              role="progressbar"
              aria-label={t('home.showcase.progress', { percent })}
              aria-valuenow={percent}
            >
              {/* The fill is the value itself: its width can only be inline. */}
              <span className={styles.nowFill} style={{ width: `${percent}%` }} />
            </div>
            <span className={styles.nowPercent} aria-hidden="true">
              {t('home.showcase.progress', { percent })}
            </span>
            <div>
              <Button
                variant="primary"
                size="compact"
                onClick={() => navigate(`/detail/${current.id}`, { state: { item: current } })}
              >
                {t('home.showcase.continue')}
              </Button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

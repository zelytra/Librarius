import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { GoalGauge } from '../../shared/ui/GoalGauge';
import { Screen, ScreenTitle } from '../../shared/ui/primitives';
import { ErrorState, Loading } from '../../shared/ui/states';
import { LoginGate } from '../../shared/LoginGate';
import { goalPace, toUnit } from '../../shared/goal';
import { useGetApiStats } from '../../api/generated/librarius';
import { TimelineSection } from './TimelineSection';
import styles from './StatsPage.module.css';

/** Bar colours, cycled through in order. */
const GENRE_BARS = [styles.bar1, styles.bar2, styles.bar3, styles.bar4];

function StatsContent() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data: stats, isPending: loading, refetch } = useGetApiStats();

  if (loading) return <Loading />;
  // A failed call and an empty payload are the same thing here: there is nothing to
  // chart, and retrying is the only useful move.
  if (!stats) return <ErrorState message={t('stats.unavailable')} onRetry={() => void refetch()} />;

  const read = stats.read ?? 0;
  const reading = stats.reading ?? 0;
  const pagesRead = stats.pagesRead ?? 0;
  const seriesCount = stats.seriesCount ?? 0;
  const goalCurrent = stats.goalCurrent ?? 0;
  const byGenre = stats.byGenre ?? [];
  const target = stats.goalTarget ?? 0;
  const year = new Date().getFullYear();
  // The unit agrees with the number it qualifies: "20 pages", but "encore 1 page".
  const units = (count: number) => t(`goal.units.${toUnit(stats.goalUnit)}`, { count });
  const pace = goalPace(goalCurrent, target, new Date());

  const bigStats = [
    { value: String(read), label: t('stats.cards.read'), icon: 'menu_book', ic: 'var(--tint-sage-ink)', tone: styles.tileSage },
    { value: pagesRead.toLocaleString('fr-FR'), label: t('stats.cards.pages'), icon: 'auto_stories', ic: 'var(--tint-rose-ink)', tone: styles.tileRose },
    { value: String(seriesCount), label: t('stats.cards.series'), icon: 'collections_bookmark', ic: 'var(--tint-violet-ink)', tone: styles.tileViolet },
    { value: String(reading), label: t('stats.cards.reading'), icon: 'local_fire_department', ic: 'var(--tint-clay-ink)', tone: styles.tileClay },
  ];

  const maxGenre = Math.max(1, ...byGenre.map((g) => g.count ?? 0));

  return (
    <>
      {/* No goal means an invitation, not a ring stuck at zero: the two look the same
          and only one of them tells the user what to do about it. */}
      <div className={styles.goal}>
        {target > 0 && (
          <GoalGauge
            percent={pace.percent}
            value={goalCurrent}
            targetLabel={t('goal.outOf', { target })}
            unitLabel={units(target)}
            label={t('goal.gaugeLabel', { current: goalCurrent, target, unit: units(target), year })}
          />
        )}
        <div className={styles.goalBody}>
          <div className={styles.goalTitle}>
            {target > 0 ? t('goal.title', { year }) : t('goal.empty.title')}
          </div>
          <div className={styles.goalHint}>
            {target <= 0 && t('goal.empty.description', { year })}
            {target > 0 && pace.reached && t('goal.reached')}
            {target > 0 &&
              !pace.reached &&
              t('goal.remaining', { remaining: pace.remaining, unit: units(pace.remaining) })}
          </div>
          <button className={styles.goalLink} onClick={() => navigate('/settings')}>
            {t(target > 0 ? 'goal.edit' : 'goal.empty.action')}
          </button>
        </div>
      </div>

      <div className={styles.tiles}>
        {bigStats.map((s) => (
          <div key={s.label} className={`${styles.tile} ${s.tone}`}>
            <Icon name={s.icon} size={22} color={s.ic} />
            <div className={styles.tileValue}>{s.value}</div>
            <div className={styles.tileLabel}>{s.label}</div>
          </div>
        ))}
      </div>

      <div className={styles.panel}>
        <div className={styles.panelTitle}>{t('stats.favoriteGenres')}</div>
        {byGenre.length === 0 ? (
          <p className={styles.panelEmpty}>{t('stats.noGenres')}</p>
        ) : (
          <div className={styles.genres}>
            {byGenre.map((g, i) => (
              <div key={g.genre}>
                <div className={styles.genreHead}>
                  <span className={styles.genreName}>{g.genre}</span>
                  <span className={styles.genreCount}>{g.count ?? 0}</span>
                </div>
                <div className={styles.track}>
                  <div
                    className={`${styles.bar} ${GENRE_BARS[i % GENRE_BARS.length]}`}
                    // The width is the share of the busiest genre.
                    style={{ width: `${Math.round(((g.count ?? 0) / maxGenre) * 100)}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Reading over time: the counters above say where the user stands, these say
          whether they are reading more than they used to. */}
      <TimelineSection stats={stats} />
    </>
  );
}

export function StatsPage() {
  const { t } = useTranslation();
  return (
    <Screen>
      <ScreenTitle className={styles.title}>{t('stats.title')}</ScreenTitle>
      <LoginGate prompt={t('auth.prompts.stats')}>
        <StatsContent />
      </LoginGate>
    </Screen>
  );
}

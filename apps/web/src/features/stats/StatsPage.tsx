import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Screen, ScreenTitle, StatusText } from '../../shared/ui/primitives';
import { LoginGate } from '../../shared/LoginGate';
import { useGetApiStats } from '../../api/generated/librarius';
import styles from './StatsPage.module.css';

/** Bar colours, cycled through in order. */
const GENRE_BARS = [styles.bar1, styles.bar2, styles.bar3, styles.bar4];

function StatsContent() {
  const { t } = useTranslation();
  const { data: stats, isPending: loading } = useGetApiStats();

  if (loading) return <StatusText>{t('common.loading')}</StatusText>;
  if (!stats) return <StatusText tone="error">{t('stats.unavailable')}</StatusText>;

  const read = stats.read ?? 0;
  const reading = stats.reading ?? 0;
  const pagesRead = stats.pagesRead ?? 0;
  const seriesCount = stats.seriesCount ?? 0;
  const goalCurrent = stats.goalCurrent ?? 0;
  const byGenre = stats.byGenre ?? [];
  const target = stats.goalTarget ?? 0;
  const pct = target > 0 ? Math.min(100, Math.round((goalCurrent / target) * 100)) : 0;
  const remaining = target > 0 ? Math.max(0, target - goalCurrent) : 0;

  const bigStats = [
    { value: String(read), label: t('stats.cards.read'), icon: 'menu_book', ic: 'var(--tint-sage-ink)', tone: styles.tileSage },
    { value: pagesRead.toLocaleString('fr-FR'), label: t('stats.cards.pages'), icon: 'auto_stories', ic: 'var(--tint-rose-ink)', tone: styles.tileRose },
    { value: String(seriesCount), label: t('stats.cards.series'), icon: 'collections_bookmark', ic: 'var(--tint-violet-ink)', tone: styles.tileViolet },
    { value: String(reading), label: t('stats.cards.reading'), icon: 'local_fire_department', ic: 'var(--tint-clay-ink)', tone: styles.tileClay },
  ];

  const maxGenre = Math.max(1, ...byGenre.map((g) => g.count ?? 0));

  return (
    <>
      <div className={styles.goal}>
        <div
          className={styles.gauge}
          // The filled arc is the progress itself.
          style={{ background: `conic-gradient(var(--accent) 0% ${pct}%, var(--chip) ${pct}% 100%)` }}
        >
          <div className={styles.gaugeCore}>
            <span className={styles.gaugeValue}>{goalCurrent}</span>
            <span className={styles.gaugeTarget}>{t('stats.goalProgress', { target: target || '—' })}</span>
          </div>
        </div>
        <div className={styles.goalBody}>
          <div className={styles.goalTitle}>{t('stats.goalTitle', { year: new Date().getFullYear() })}</div>
          <div className={styles.goalHint}>
            {target > 0 ? t('stats.goalRemaining', { remaining }) : t('stats.goalUnset')}
          </div>
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

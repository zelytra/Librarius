import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Button } from '../../shared/ui/primitives';
import { useGetApiAchievements } from '../../api/generated/librarius';
import { levelProgress } from '../achievements/achievements';
import styles from './StatsPage.module.css';

/** The level and the XP bar, and the way into every achievement (#287). */
export function AchievementsPanel() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data } = useGetApiAchievements();
  if (!data) return null;
  const xp = data.xp ?? 0;
  const unlocked = (data.achievements ?? []).filter((a) => (a.tier ?? 0) > 0).length;
  const progress = levelProgress(xp, data.levelXp ?? 0, data.nextLevelXp ?? 0);
  return (
    <div className={styles.panel}>
      <div className={styles.panelTitle}>{t('achievements.level', { level: data.level })}</div>
      <div className={styles.xpTrack} role="progressbar" aria-valuenow={Math.round(progress * 100)} aria-label={t('achievements.title')}>
        <span className={styles.xpFill} style={{ width: `${Math.round(progress * 100)}%` }} />
      </div>
      <p className={styles.panelEmpty}>
        {[t('achievements.count', { unlocked, total: data.achievements?.length ?? 0 }), t('achievements.xpTotal', { xp })].join(' · ')}
      </p>
      <Button variant="secondary" size="compact" onClick={() => navigate('/achievements')}>
        {t('achievements.see')}
      </Button>
    </div>
  );
}

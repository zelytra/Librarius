import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { useApiAuth } from '../../shared/api';
import { useGetApiAchievements } from '../../api/generated/librarius';
import styles from './LevelChip.module.css';

/** The reader's level, and the way into the achievements screen (#287). */
export function LevelChip() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { authed } = useApiAuth();
  const { data } = useGetApiAchievements({ query: { enabled: authed, staleTime: 30_000 } });
  if (!data) return null;
  return (
    <button
      type="button"
      className={styles.chip}
      onClick={() => navigate('/achievements')}
      aria-label={t('achievements.open', { level: data.level })}
      title={t('achievements.open', { level: data.level })}
    >
      <Icon name="emoji_events" size={20} color="var(--accent-deep)" />
      {t('achievements.levelShort', { level: data.level })}
    </button>
  );
}

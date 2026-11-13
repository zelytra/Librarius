import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { LoginGate } from '../../shared/LoginGate';
import { Icon } from '../../shared/ui/Icon';
import { Screen, ScreenTitle } from '../../shared/ui/primitives';
import { ErrorState, Loading } from '../../shared/ui/states';
import { activeLanguage } from '../../i18n/languages';
import { useGetApiAchievements, type AchievementDto, type AchievementsDto } from '../../api/generated/librarius';
import { ACHIEVEMENT_ICONS, FAMILIES, levelProgress, progressToNext, tierKey } from './achievements';
import styles from './AchievementsPage.module.css';

/** The level, and the XP to the next one. */
export function LevelCard({ data }: { data: AchievementsDto }) {
  const { t } = useTranslation();
  const xp = data.xp ?? 0;
  const from = data.levelXp ?? 0;
  const to = data.nextLevelXp ?? 0;
  const progress = levelProgress(xp, from, to);
  const unlocked = (data.achievements ?? []).filter((a) => (a.tier ?? 0) > 0).length;
  const locale = activeLanguage();
  return (
    <div className={styles.levelCard}>
      <div className={styles.levelBadge} aria-hidden="true">
        <span className={styles.levelNumber}>{data.level}</span>
      </div>
      <div className={styles.levelBody}>
        <div className={styles.levelTitle}>{t('achievements.level', { level: data.level })}</div>
        <div
          className={styles.xpTrack}
          role="progressbar"
          aria-label={t('achievements.xpLabel', { xp: xp.toLocaleString(locale), next: to.toLocaleString(locale) })}
          aria-valuenow={Math.round(progress * 100)}
        >
          <span className={styles.xpFill} style={{ width: `${Math.round(progress * 100)}%` }} />
        </div>
        <div className={styles.levelHint}>
          {t('achievements.toNext', {
            xp: xp.toLocaleString(locale),
            remaining: Math.max(0, to - xp).toLocaleString(locale),
            next: (data.level ?? 1) + 1,
          })}
        </div>
        <div className={styles.levelHint}>
          {t('achievements.count', { unlocked, total: data.achievements?.length ?? 0 })}
        </div>
      </div>
    </div>
  );
}

function AchievementCard({ a }: { a: AchievementDto }) {
  const { t } = useTranslation();
  const code = a.code ?? '';
  const tiers = a.tiers ?? 1;
  const tier = a.tier ?? 0;
  const unlocked = tier > 0;
  const complete = a.nextThreshold == null;
  const locale = activeLanguage();
  // The description names the bar to clear next, or the last one once everything is earned.
  const target = a.nextThreshold ?? a.value ?? 0;
  const currentTier = tierKey(tier, tiers);
  return (
    <li className={`${styles.card} ${unlocked ? styles.cardOn : ''}`}>
      <div className={`${styles.badge} ${currentTier ? styles[currentTier] : ''}`} aria-hidden="true">
        <Icon name={ACHIEVEMENT_ICONS[code] ?? 'emoji_events'} size={24} color="currentColor" />
        {!unlocked && (
          <span className={styles.lock}>
            <Icon name="lock" size={12} color="currentColor" />
          </span>
        )}
      </div>
      <div className={styles.cardBody}>
        <div className={styles.cardHead}>
          <span className={styles.name}>{t(`achievements.items.${code}.name`)}</span>
          {currentTier && <span className={styles.tierName}>{t(`achievements.tiers.${currentTier}`)}</span>}
        </div>
        <p className={styles.description}>
          {t(`achievements.items.${code}.description`, { count: target, value: target.toLocaleString(locale) })}
        </p>
        {tiers > 1 && (
          <div className={styles.pips} aria-label={t('achievements.tiersLabel', { tier, tiers })}>
            {Array.from({ length: tiers }, (_, i) => (
              <span key={i} className={`${styles.pip} ${i < tier ? styles.pipOn : ''}`} />
            ))}
          </div>
        )}
        {!complete && (
          <div className={styles.progressRow}>
            <div className={styles.track}>
              <span className={styles.fill} style={{ width: `${Math.round(progressToNext(a) * 100)}%` }} />
            </div>
            <span className={styles.progressText}>
              {t('achievements.progress', {
                value: (a.value ?? 0).toLocaleString(locale),
                target: (a.nextThreshold ?? 0).toLocaleString(locale),
              })}
            </span>
          </div>
        )}
        <div className={styles.meta}>
          {[
            unlocked && a.unlockedAt
              ? t('achievements.unlockedOn', {
                  date: new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'long', year: 'numeric' }).format(
                    new Date(a.unlockedAt),
                  ),
                })
              : t('achievements.locked'),
            complete ? t('achievements.xpEarned', { xp: a.xpEarned ?? 0 }) : t('achievements.xpNext', { xp: a.nextXp ?? 0 }),
          ].join(' · ')}
        </div>
      </div>
    </li>
  );
}

function AchievementsContent() {
  const { t } = useTranslation();
  const { data, isPending, isError, refetch } = useGetApiAchievements();
  if (isPending) return <Loading />;
  if (isError || !data) return <ErrorState message={t('achievements.error')} onRetry={() => void refetch()} />;
  const all = data.achievements ?? [];
  return (
    <div className={styles.page}>
      <LevelCard data={data} />
      {FAMILIES.map((family) => {
        const items = all.filter((a) => a.family === family);
        if (items.length === 0) return null;
        return (
          <section key={family} className={styles.family}>
            <h2 className={styles.familyTitle}>{t(`achievements.families.${family}`)}</h2>
            <ul className={styles.grid}>
              {items.map((a) => (
                <AchievementCard key={a.code} a={a} />
              ))}
            </ul>
          </section>
        );
      })}
    </div>
  );
}

export function AchievementsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <Screen>
      <div className={styles.header}>
        <button onClick={() => navigate(-1)} aria-label={t('common.back')} className={styles.back}>
          <Icon name="arrow_back" size={24} color="var(--ink)" />
        </button>
        <ScreenTitle className={styles.title}>{t('achievements.title')}</ScreenTitle>
      </div>
      <LoginGate prompt={t('achievements.prompt')}>
        <AchievementsContent />
      </LoginGate>
    </Screen>
  );
}

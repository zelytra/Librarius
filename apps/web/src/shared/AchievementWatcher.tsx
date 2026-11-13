import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetApiAchievementsQueryKey,
  postApiAchievementsSeen,
  useGetApiAchievements,
} from '../api/generated/librarius';
import { useApiAuth } from './api';
import { toast } from './ui/toast';

/**
 * Announces the achievements the server unlocked (#287). Rendered once, by the app shell: the
 * progression is refetched whenever a write refreshes the statistics (see `STATS_QUERIES`), and
 * each unlock not shown yet becomes a toast, then is marked seen. Nothing is decided here —
 * unlocks, XP and levels are the server's.
 */
export function AchievementWatcher() {
  const { t } = useTranslation();
  const { authed } = useApiAuth();
  const queryClient = useQueryClient();
  const { data } = useGetApiAchievements({ query: { enabled: authed, staleTime: 30_000 } });
  const announced = useRef(new Set<string>());

  useEffect(() => {
    const fresh = (data?.unseen ?? []).filter((u) => !announced.current.has(`${u.code}#${u.tier}`));
    if (fresh.length === 0) return;
    fresh.forEach((unlock) => announced.current.add(`${unlock.code}#${unlock.tier}`));
    // A burst — a library imported, or rebuilt — is one message: a stack of toasts would bury
    // the screen, and the achievements screen lists them all.
    const BURST = 3;
    if (fresh.length >= BURST) {
      const xp = fresh.reduce((sum, unlock) => sum + (unlock.xp ?? 0), 0);
      toast.success(t('achievements.unlockedMany', { count: fresh.length, xp }), 'emoji_events');
    }
    for (const unlock of fresh.length >= BURST ? [] : fresh) {
      const tier = (unlock.tiers ?? 1) > 1 ? ` · ${t(`achievements.tiers.${['bronze', 'silver', 'gold', 'platinum'][Math.min(unlock.tier ?? 1, 4) - 1]}`)}` : '';
      toast.success(
        t('achievements.unlocked', { name: t(`achievements.items.${unlock.code}.name`) + tier, xp: unlock.xp ?? 0 }),
        'emoji_events',
      );
    }
    void postApiAchievementsSeen().then(() =>
      queryClient.invalidateQueries({ queryKey: getGetApiAchievementsQueryKey() }),
    );
  }, [data, t, queryClient]);

  return null;
}

import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { GoalGauge } from '../../shared/ui/GoalGauge';
import { Button } from '../../shared/ui/primitives';
import { EmptyState } from '../../shared/ui/states';
import { formatPace, goalPace, lastGoalBefore, paceForUnit, toUnit } from '../../shared/goal';
import {
  getGetApiGoalsQueryKey,
  getGetApiStatsQueryKey,
  useGetApiGoals,
  usePutApiGoalsYear,
  type StatsDto,
} from '../../api/generated/librarius';
import styles from './GoalCard.module.css';

/**
 * The annual reading goal on the dashboard.
 *
 * <p>Three states, and the empty one is not a gauge at zero: a ring showing nothing is
 * indistinguishable from a year gone badly. Without a goal the card invites the user to set
 * one — and on 1 January, when the goal they had does not carry over on its own, it offers
 * last year's target rather than an empty form.
 */
export function GoalCard({ stats }: { stats: StatsDto | undefined }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const year = new Date().getFullYear();

  // Only needed to answer "did they have a goal last year?": the current one travels on the
  // statistics the dashboard already reads.
  const { data: goals } = useGetApiGoals();
  const { mutate: saveGoal, isPending: carryingOver } = usePutApiGoalsYear({
    mutation: {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: getGetApiGoalsQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
      },
    },
  });

  const target = stats?.goalTarget ?? 0;
  const current = stats?.goalCurrent ?? 0;
  const unit = toUnit(stats?.goalUnit);
  // The unit agrees with the number it qualifies, which is not the same one on every line:
  // "8 / 30 livres", but "encore 1 livre".
  const units = (count: number) => t(`goal.units.${unit}`, { count });

  if (target <= 0) {
    const previous = lastGoalBefore(goals, year);
    const previousUnit = toUnit(previous?.unit);
    const previousTarget = previous?.targetCount ?? 0;
    const previousUnits = t(`goal.units.${previousUnit}`, { count: previousTarget });

    // A goal is set per year and the year turns over on its own: the one the user had is
    // still there, one row further down, and re-typing it is the only thing standing
    // between them and carrying on.
    return previous ? (
      <EmptyState
        icon="flag"
        className={styles.empty}
        title={t('goal.rollover.title', { year })}
        description={t('goal.rollover.description', {
          year: previous.year,
          target: previousTarget,
          unit: previousUnits,
        })}
        action={
          <div className={styles.emptyActions}>
            <Button
              onClick={() =>
                saveGoal({ year, data: { targetCount: previousTarget, unit: previousUnit } })
              }
              disabled={carryingOver}
            >
              {t(carryingOver ? 'common.working' : 'goal.rollover.action', {
                target: previousTarget,
                unit: previousUnits,
              })}
            </Button>
            <Button variant="ghost" onClick={() => navigate('/settings')}>
              {t('goal.rollover.other')}
            </Button>
          </div>
        }
      />
    ) : (
      <EmptyState
        icon="flag"
        className={styles.empty}
        title={t('goal.empty.title')}
        description={t('goal.empty.description', { year })}
        action={
          <Button variant="secondary" onClick={() => navigate('/settings')}>
            {t('goal.empty.action')}
          </Button>
        }
      />
    );
  }

  const pace = goalPace(current, target, new Date());
  const { value, perMonth } = paceForUnit(pace, unit);

  return (
    <div className={styles.card}>
      <GoalGauge
        percent={pace.percent}
        value={current}
        targetLabel={t('goal.outOf', { target })}
        unitLabel={units(target)}
        label={t('goal.gaugeLabel', { current, target, unit: units(target), year })}
      />
      <div className={styles.body}>
        <div className={styles.head}>
          <h3 className={styles.title}>{t('goal.title', { year })}</h3>
          <span className={pace.onTrack ? styles.badgeOnTrack : styles.badgeBehind}>
            {t(pace.onTrack ? 'goal.onTrack' : 'goal.behind')}
          </span>
        </div>
        {pace.reached ? (
          <p className={styles.line}>{t('goal.reached')}</p>
        ) : (
          <>
            <p className={styles.line}>
              {t('goal.remaining', { remaining: pace.remaining, unit: units(pace.remaining) })}
            </p>
            <p className={styles.pace}>
              {t(perMonth ? 'goal.pace.perMonth' : 'goal.pace.perDay', {
                value: formatPace(value),
                unit: units(value),
              })}
            </p>
          </>
        )}
        <button className={styles.edit} onClick={() => navigate('/settings')}>
          {t('goal.edit')}
        </button>
      </div>
    </div>
  );
}

import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Segmented } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import { useApiAuth } from '../../shared/api';
import { GOAL_UNITS, goalUnitOf, type GoalUnit } from '../../shared/goal';
import {
  getGetApiGoalsQueryKey,
  getGetApiStatsQueryKey,
  useGetApiGoals,
  usePutApiGoalsYear,
  type GoalDto,
} from '../../api/generated/librarius';
import styles from './GoalSection.module.css';

/**
 * Setting the annual reading goal.
 *
 * `GET /api/goals` and `PUT /api/goals/{year}` had worked since the first back-end
 * milestone without a single screen calling them, so the feature existed and was
 * invisible. This is where a goal is set; Home and Statistics show how it is going.
 */
export function GoalSection() {
  const { t } = useTranslation();
  const auth = useApiAuth();
  const queryClient = useQueryClient();
  const year = new Date().getFullYear();

  const { data: goals = [], isPending } = useGetApiGoals({
    query: { enabled: auth.authed },
  });

  const current = goals.find((g) => g.year === year);
  // The most recent goal of an earlier year, which the new year can carry over rather
  // than make the user retype every January.
  const previous = goals
    .filter((g) => (g.year ?? 0) < year)
    .sort((a, b) => (b.year ?? 0) - (a.year ?? 0))[0];

  return auth.authed ? (
    <>
      <h3 className={styles.title}>{t('settings.goal.title', { year })}</h3>
      <p className={styles.intro}>{t('settings.goal.description')}</p>
      {isPending ? (
        <p className={styles.intro}>{t('common.loading')}</p>
      ) : (
        <GoalForm
          year={year}
          current={current}
          previous={current ? undefined : previous}
          onSaved={() => {
            void queryClient.invalidateQueries({ queryKey: getGetApiGoalsQueryKey() });
            // The gauges on Home and Statistics read the goal through the statistics.
            void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
          }}
        />
      )}
    </>
  ) : null;
}

interface GoalFormProps {
  year: number;
  current?: GoalDto;
  /** Last year's goal, offered as a starting point when this year has none. */
  previous?: GoalDto;
  onSaved: () => void;
}

function GoalForm({ year, current, previous, onSaved }: GoalFormProps) {
  const { t } = useTranslation();
  const [target, setTarget] = useState(current ? String(current.targetCount) : '');
  const [unit, setUnit] = useState<GoalUnit>(goalUnitOf(current?.unit));
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const parsed = Number(target.trim());
  const valid = target.trim() !== '' && Number.isInteger(parsed) && parsed >= 1;

  const { mutate: save, isPending: saving } = usePutApiGoalsYear({
    mutation: {
      onSuccess: () => {
        setSaved(true);
        setError(null);
        onSaved();
      },
      onError: () => setError(t('settings.goal.failed')),
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!valid) return;
    setSaved(false);
    save({ year, data: { targetCount: parsed, unit } });
  }

  /** Carrying last year's goal over fills the form; the user still confirms it. */
  function carryOver() {
    if (!previous) return;
    setTarget(String(previous.targetCount));
    setUnit(goalUnitOf(previous.unit));
    setSaved(false);
  }

  return (
    <form className={styles.form} onSubmit={submit}>
      {previous && (
        <button type="button" onClick={carryOver} className={styles.carryOver}>
          <Icon name="history" size={18} color="var(--accent-deep)" />
          {t('settings.goal.carryOver', {
            year: previous.year,
            target: previous.targetCount,
            unit: t(`settings.goal.units.${goalUnitOf(previous.unit)}`).toLowerCase(),
          })}
        </button>
      )}

      <Segmented<GoalUnit>
        value={unit}
        onChange={(u) => {
          setUnit(u);
          setSaved(false);
        }}
        options={GOAL_UNITS.map((u) => ({ id: u, label: t(`settings.goal.units.${u}`) }))}
      />

      <div className={styles.row}>
        <input
          value={target}
          inputMode="numeric"
          onChange={(e) => {
            setTarget(e.target.value);
            setSaved(false);
          }}
          placeholder={t('settings.goal.placeholder')}
          aria-label={t('settings.goal.target')}
          aria-invalid={target.trim() !== '' && !valid}
          className={styles.input}
        />
        <Button type="submit" size="compact" disabled={saving || !valid}>
          {t(saving ? 'common.working' : 'settings.goal.save')}
        </Button>
      </div>

      {saved && <p className={styles.success}>{t('settings.goal.saved')}</p>}
      {error && <p className={styles.failure}>{error}</p>}
    </form>
  );
}

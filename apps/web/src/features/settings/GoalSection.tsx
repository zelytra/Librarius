import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Segmented } from '../../shared/ui/primitives';
import { useApiAuth } from '../../shared/api';
import { goalForYear, toUnit, type Unit } from '../../shared/goal';
import {
  getGetApiGoalsQueryKey,
  getGetApiStatsQueryKey,
  useGetApiGoals,
  usePutApiGoalsYear,
  GoalUnit,
} from '../../api/generated/librarius';
import styles from './GoalSection.module.css';

/**
 * The units offered, in the order they read: a novel, a page. {@code VOLUMES} is not
 * offered — it counts identically to {@code BOOKS} today (see {@code shared/goal.ts}) —
 * but the enum value stays on the API for goals stored with it before it was retired.
 */
const UNITS: Unit[] = [GoalUnit.BOOKS, GoalUnit.PAGES];

/**
 * Where the annual reading goal is set.
 *
 * <p>The form is filled from the goal already stored for the year, so opening it after the
 * fact shows what the user chose rather than an empty field. Saving invalidates the
 * statistics too: the gauge on the dashboard is drawn from them, and it has to move the
 * moment the target does.
 */
export function GoalSection() {
  const { t } = useTranslation();
  const auth = useApiAuth();
  const queryClient = useQueryClient();
  const year = new Date().getFullYear();

  const { data: goals } = useGetApiGoals({ query: { enabled: auth.authed } });
  const stored = goalForYear(goals, year);

  const [target, setTarget] = useState('');
  const [unit, setUnit] = useState<Unit>(GoalUnit.BOOKS);
  const [saved, setSaved] = useState(false);

  // The stored goal arrives after the first render, and the user may not have touched the
  // form yet. Syncing on the identity of that goal fills the fields once, and leaves them
  // alone afterwards — including while the save is in flight.
  useEffect(() => {
    if (stored?.targetCount) {
      setTarget(String(stored.targetCount));
      setUnit(toUnit(stored.unit));
    }
  }, [stored?.id, stored?.targetCount, stored?.unit]);

  const { mutate: saveGoal, isPending, isError } = usePutApiGoalsYear({
    mutation: {
      onSuccess: () => {
        setSaved(true);
        void queryClient.invalidateQueries({ queryKey: getGetApiGoalsQueryKey() });
        void queryClient.invalidateQueries({ queryKey: getGetApiStatsQueryKey() });
      },
    },
  });

  const parsed = Number.parseInt(target, 10);
  const valid = Number.isFinite(parsed) && parsed > 0;

  return (
    <>
      <h3 className={styles.title}>{t('goal.settings.title', { year })}</h3>
      <p className={styles.intro}>{t('goal.settings.description')}</p>

      {!auth.authed ? (
        <p className={styles.intro}>{t('goal.settings.signIn')}</p>
      ) : (
        <div className={styles.form}>
          <Segmented<Unit>
            value={unit}
            onChange={(next) => {
              setUnit(next);
              setSaved(false);
            }}
            // The picker names the unit rather than a quantity of it, so it reads plural.
            options={UNITS.map((id) => ({ id, label: t(`goal.units.${id}`, { count: 2 }) }))}
          />
          <div className={styles.row}>
            <input
              type="number"
              inputMode="numeric"
              min={1}
              value={target}
              onChange={(e) => {
                setTarget(e.target.value);
                setSaved(false);
              }}
              placeholder={t('goal.settings.placeholder')}
              aria-label={t('goal.settings.placeholder')}
              className={styles.input}
            />
            <Button
              variant="primary"
              size="compact"
              disabled={!valid || isPending}
              onClick={() => saveGoal({ year, data: { targetCount: parsed, unit } })}
            >
              {t(isPending ? 'common.working' : 'goal.settings.submit')}
            </Button>
          </div>

          {saved && !isPending && (
            <p className={styles.success}>
              {t('goal.settings.saved', {
                target: parsed,
                unit: t(`goal.units.${unit}`, { count: parsed }),
              })}
            </p>
          )}
          {isError && <p className={styles.failure}>{t('goal.settings.failed')}</p>}
        </div>
      )}
    </>
  );
}

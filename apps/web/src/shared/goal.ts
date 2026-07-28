import { GoalUnit, type GoalDto } from '../api/generated/librarius';

/**
 * The arithmetic behind the annual reading goal.
 *
 * <p>Kept out of the components, and pure: the pace a screen tells the user to hold is the
 * one figure they act on, and a frozen date is the only way to lock it down.
 */

/** The three units a goal can be expressed in, as the API spells them. */
export type Unit = (typeof GoalUnit)[keyof typeof GoalUnit];

const UNITS: Unit[] = [GoalUnit.BOOKS, GoalUnit.VOLUMES, GoalUnit.PAGES];

/** Reads the unit off a payload where it is typed as a bare string. */
export function toUnit(value: string | undefined): Unit {
  return UNITS.find((unit) => unit === value) ?? GoalUnit.BOOKS;
}

export interface GoalPace {
  /** What is left to read, never negative. */
  remaining: number;
  /** Days still available, today included — 1 on 31 December. */
  daysLeft: number;
  /** The remaining work spread evenly over the days left. */
  perDay: number;
  perWeek: number;
  perMonth: number;
  /** Share of the target already reached, 0 to 100. */
  percent: number;
  /** Where a steady reader would stand today. */
  expected: number;
  /** At or past that mark. */
  onTrack: boolean;
  /** Target met — there is no pace left to hold. */
  reached: boolean;
}

/** Days in a calendar year, leap years included. */
export function daysInYear(year: number): number {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0 ? 366 : 365;
}

/** Rank of a day inside its year: 1 on 1 January. */
export function dayOfYear(date: Date): number {
  const start = new Date(date.getFullYear(), 0, 1);
  const day = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  return Math.round((day.getTime() - start.getTime()) / 86_400_000) + 1;
}

/**
 * Where the user stands against their target, and what it takes to still meet it.
 *
 * <p>The remaining work is spread over the days that are actually left, today included:
 * dividing by what is left of the year rather than by the whole of it is what makes the
 * figure move as the year goes by, and what makes falling behind visible.
 *
 * @param current what has been read so far this year, in the unit of the goal
 * @param target  the goal itself
 * @param today   the day the pace is computed for
 */
export function goalPace(current: number, target: number, today: Date): GoalPace {
  const total = daysInYear(today.getFullYear());
  const elapsed = Math.min(dayOfYear(today), total);
  const daysLeft = total - elapsed + 1;
  const remaining = Math.max(0, target - current);
  const perDay = remaining / daysLeft;

  return {
    remaining,
    daysLeft,
    perDay,
    perWeek: perDay * 7,
    perMonth: (perDay * total) / 12,
    percent: target > 0 ? Math.min(100, Math.round((current / target) * 100)) : 0,
    expected: (target * elapsed) / total,
    onTrack: current >= (target * elapsed) / total,
    reached: target > 0 && current >= target,
  };
}

/**
 * The pace figure that reads best for a unit. Pages are a daily habit — "30 pages a day" is
 * something one can hold; books are a monthly one, and "0.1 book a day" says nothing.
 */
export function paceForUnit(pace: GoalPace, unit: Unit): { value: number; perMonth: boolean } {
  return unit === GoalUnit.PAGES
    ? { value: pace.perDay, perMonth: false }
    : { value: pace.perMonth, perMonth: true };
}

/** The goal set for a given year, if there is one. */
export function goalForYear(goals: GoalDto[] | undefined, year: number): GoalDto | undefined {
  return goals?.find((goal) => goal.year === year);
}

/**
 * The most recent goal from a year already over.
 *
 * <p>What the 1 January rollover offers to carry over: the year changes on its own, the
 * goal does not, and a user who set one last year should not have to type it again.
 */
export function lastGoalBefore(goals: GoalDto[] | undefined, year: number): GoalDto | undefined {
  return (goals ?? [])
    .filter((goal) => (goal.year ?? 0) < year && (goal.targetCount ?? 0) > 0)
    .sort((a, b) => (b.year ?? 0) - (a.year ?? 0))[0];
}

/** Rounds a pace for display: one decimal below ten, none above. */
export function formatPace(value: number): string {
  return value.toLocaleString('fr-FR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: value < 10 ? 1 : 0,
  });
}

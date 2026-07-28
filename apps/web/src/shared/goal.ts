/**
 * The annual reading goal, as the screens need to show it.
 *
 * Home, Statistics and Settings all read the same goal; the arithmetic lives here so the
 * three of them can never disagree on how far along the user is, or on what it would take
 * to finish the year on target.
 */

/** Units a goal can be expressed in, in the order the API declares them. */
export const GOAL_UNITS = ['BOOKS', 'VOLUMES', 'PAGES'] as const;

export type GoalUnit = (typeof GOAL_UNITS)[number];

/** A unit the front end does not know about would leave the figure unlabelled. */
export function goalUnitOf(unit: string | undefined): GoalUnit {
  return GOAL_UNITS.find((u) => u === unit) ?? 'BOOKS';
}

export interface GoalPace {
  /** Share of the target already read, 0–100. */
  percent: number;
  /** What is left to read. Zero once the goal is met. */
  remaining: number;
  /** Days left in the year, today included. */
  daysLeft: number;
  /** What a week has to carry from today on — null once there is nothing left. */
  perWeek: number | null;
  reached: boolean;
}

/** Midnight of the given day, so a comparison counts days and not hours. */
function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

/**
 * Where the user stands, and the pace the rest of the year demands.
 *
 * @param target  what the goal asks for, in its own unit
 * @param current what has been read this year, in that same unit
 * @param today   injected rather than read from the clock, so the pace can be tested
 */
export function goalPace(target: number, current: number, today: Date): GoalPace {
  const remaining = Math.max(0, target - current);
  // Capped: a goal beaten does not draw a gauge past its own end.
  const percent = target > 0 ? Math.min(100, Math.round((current / target) * 100)) : 0;

  const lastDay = new Date(today.getFullYear(), 11, 31);
  const daysLeft = Math.max(
    1,
    Math.round((startOfDay(lastDay) - startOfDay(today)) / 86_400_000) + 1,
  );

  // Rounded up: a pace rounded down is a pace that misses the goal. The last week of the
  // year asks for everything that is left, rather than for a fraction of a week.
  const perWeek = remaining === 0 ? null : Math.ceil(remaining / Math.max(1, daysLeft / 7));

  return { percent, remaining, daysLeft, perWeek, reached: remaining === 0 };
}

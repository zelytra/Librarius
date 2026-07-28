import { describe, expect, test } from 'vitest';
import {
  dayOfYear,
  daysInYear,
  formatPace,
  goalForYear,
  goalPace,
  lastGoalBefore,
  paceForUnit,
  toUnit,
} from './goal';

/**
 * The pace is the one figure the user acts on, and it depends on today's date: every
 * expectation below is pinned to a frozen day so the arithmetic can be checked by hand.
 */
describe('goalPace', () => {
  /** 1 July 2026 is the 182nd day of a 365-day year, so 184 days are left. */
  const JULY_FIRST = new Date(2026, 6, 1);

  test('spreads what is left over the days that are left', () => {
    const pace = goalPace(12, 30, JULY_FIRST);

    expect(pace.remaining).toBe(18);
    expect(pace.daysLeft).toBe(184);
    // 18 over 184 days: 0.098 a day, 0.68 a week, 2.98 a month — each rounded up.
    expect(pace.perDay).toBe(1);
    expect(pace.perWeek).toBe(1);
    expect(pace.perMonth).toBe(3);
  });

  /**
   * Rounded up, and on each window in its own right: a pace rounded down is a pace that
   * misses the goal, and rounding the day up first would turn one book a month into seven.
   */
  test('rounds every pace up, each on its own window', () => {
    const pace = goalPace(0, 120, JULY_FIRST);

    expect(pace.perDay).toBe(1); // 120 / 184 = 0.65
    expect(pace.perWeek).toBe(5); // 120 / (184 / 7) = 4.57
    expect(pace.perMonth).toBe(20); // 120 / (184 / 30.4) = 19.8
  });

  test('asks a demanding target for more than one a day', () => {
    const pace = goalPace(0, 500, JULY_FIRST);

    expect(pace.perDay).toBe(3); // 500 / 184 = 2.72
    expect(pace.perWeek).toBe(20); // 500 / (184 / 7) = 19.02
  });

  test('measures being behind against a steady pace, not against the target', () => {
    // A steady reader would be at 30 × 182 / 365 ≈ 14.96 titles on 1 July.
    expect(goalPace(12, 30, JULY_FIRST).expected).toBeCloseTo(14.9589, 3);
    expect(goalPace(12, 30, JULY_FIRST).onTrack).toBe(false);
    expect(goalPace(15, 30, JULY_FIRST).onTrack).toBe(true);
  });

  /** The last day of the year asks for everything left, not for a fraction of a week. */
  test('counts today as a day still available', () => {
    const pace = goalPace(28, 30, new Date(2026, 11, 31));

    expect(pace.daysLeft).toBe(1);
    expect(pace.perDay).toBe(2);
    expect(pace.perWeek).toBe(2);
    expect(pace.perMonth).toBe(2);
  });

  test('stops at the target rather than reporting a negative pace', () => {
    const pace = goalPace(40, 30, JULY_FIRST);

    expect(pace.remaining).toBe(0);
    expect(pace.perDay).toBe(0);
    expect(pace.perWeek).toBe(0);
    expect(pace.perMonth).toBe(0);
    expect(pace.percent).toBe(100);
    expect(pace.reached).toBe(true);
  });

  test('caps the share of the target shown by the gauge', () => {
    expect(goalPace(0, 30, JULY_FIRST).percent).toBe(0);
    expect(goalPace(15, 30, JULY_FIRST).percent).toBe(50);
    expect(goalPace(1, 0, JULY_FIRST).percent).toBe(0);
  });

  test('takes the extra day of a leap year into account', () => {
    // 1 July 2024 is the 183rd day of a 366-day year.
    expect(daysInYear(2024)).toBe(366);
    expect(daysInYear(2100)).toBe(365);
    expect(dayOfYear(new Date(2024, 6, 1))).toBe(183);
    expect(goalPace(0, 366, new Date(2024, 6, 1)).daysLeft).toBe(184);
  });
});

describe('paceForUnit', () => {
  const pace = goalPace(12, 30, new Date(2026, 6, 1));

  test('states pages by the day and books by the month', () => {
    expect(paceForUnit(pace, 'PAGES')).toEqual({ value: pace.perDay, perMonth: false });
    expect(paceForUnit(pace, 'BOOKS')).toEqual({ value: pace.perMonth, perMonth: true });
    expect(paceForUnit(pace, 'VOLUMES')).toEqual({ value: pace.perMonth, perMonth: true });
  });
});

describe('unit and goal lookup', () => {
  test('falls back to books on a unit it does not know', () => {
    expect(toUnit('PAGES')).toBe('PAGES');
    expect(toUnit(undefined)).toBe('BOOKS');
    expect(toUnit('CHAPTERS')).toBe('BOOKS');
  });

  test('finds this year among the goals', () => {
    const goals = [{ year: 2025, targetCount: 20 }, { year: 2026, targetCount: 30 }];

    expect(goalForYear(goals, 2026)?.targetCount).toBe(30);
    expect(goalForYear(goals, 2027)).toBeUndefined();
    expect(goalForYear(undefined, 2026)).toBeUndefined();
  });

  /** What the 1 January rollover offers to carry over. */
  test('offers the most recent goal of a year already over', () => {
    const goals = [
      { year: 2023, targetCount: 10 },
      { year: 2025, targetCount: 20 },
      { year: 2024, targetCount: 15 },
    ];

    expect(lastGoalBefore(goals, 2026)?.year).toBe(2025);
    expect(lastGoalBefore(goals, 2024)?.year).toBe(2023);
    expect(lastGoalBefore(goals, 2023)).toBeUndefined();
    expect(lastGoalBefore([{ year: 2025, targetCount: 0 }], 2026)).toBeUndefined();
  });
});

describe('formatPace', () => {
  test('shows a whole figure, grouped once it reaches the thousands', () => {
    expect(formatPace(3)).toBe('3');
    expect(formatPace(42)).toBe('42');
    // French groups the thousands; which space the locale data uses is its own business.
    expect(formatPace(1200)).toMatch(/^1\D200$/);
  });
});

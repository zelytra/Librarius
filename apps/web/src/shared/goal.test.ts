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
    expect(pace.perDay).toBeCloseTo(18 / 184, 6);
    expect(pace.perWeek).toBeCloseTo((18 / 184) * 7, 6);
    expect(pace.perMonth).toBeCloseTo(((18 / 184) * 365) / 12, 6);
  });

  test('measures being behind against a steady pace, not against the target', () => {
    // A steady reader would be at 30 × 182 / 365 ≈ 14.96 titles on 1 July.
    expect(goalPace(12, 30, JULY_FIRST).expected).toBeCloseTo(14.9589, 3);
    expect(goalPace(12, 30, JULY_FIRST).onTrack).toBe(false);
    expect(goalPace(15, 30, JULY_FIRST).onTrack).toBe(true);
  });

  test('counts today as a day still available', () => {
    // 31 December leaves one day, not zero: the pace must stay a number.
    const pace = goalPace(28, 30, new Date(2026, 11, 31));

    expect(pace.daysLeft).toBe(1);
    expect(pace.perDay).toBe(2);
  });

  test('stops at the target rather than reporting a negative pace', () => {
    const pace = goalPace(40, 30, JULY_FIRST);

    expect(pace.remaining).toBe(0);
    expect(pace.perDay).toBe(0);
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
  test('keeps one decimal below ten and none above', () => {
    expect(formatPace(2.9755)).toBe('3');
    expect(formatPace(1.26)).toBe('1,3');
    expect(formatPace(42.4)).toBe('42');
  });
});

import { describe, expect, test } from 'vitest';
import { GOAL_UNITS, goalPace, goalUnitOf } from './goal';

/**
 * Every case runs on a frozen date: the pace is the one figure of the goal that depends
 * on when it is looked at, and a test reading the real clock would only be true in July.
 */
describe('goalPace', () => {
  const JULY_FIRST = new Date(2026, 6, 1);

  test('a third of the way through a thirty-book year', () => {
    const pace = goalPace(30, 12, JULY_FIRST);

    // 1 July to 31 December inclusive: 184 days, a shade over 26 weeks.
    expect(pace.daysLeft).toBe(184);
    expect(pace.percent).toBe(40);
    expect(pace.remaining).toBe(18);
    expect(pace.perWeek).toBe(1);
    expect(pace.reached).toBe(false);
  });

  test('a demanding target asks for more than one a week', () => {
    expect(goalPace(52, 10, JULY_FIRST).perWeek).toBe(2);
  });

  test('the pace is rounded up, since rounding down misses the goal', () => {
    // 27 left over ~26.3 weeks is 1.03 a week: one a week finishes the year short.
    expect(goalPace(30, 3, JULY_FIRST).perWeek).toBe(2);
  });

  test('a goal already met asks for no pace at all', () => {
    const pace = goalPace(30, 30, JULY_FIRST);

    expect(pace.reached).toBe(true);
    expect(pace.remaining).toBe(0);
    expect(pace.perWeek).toBeNull();
    expect(pace.percent).toBe(100);
  });

  test('a goal beaten does not draw a gauge past its end', () => {
    expect(goalPace(30, 45, JULY_FIRST).percent).toBe(100);
  });

  test('the first of January has the whole year ahead of it', () => {
    expect(goalPace(30, 0, new Date(2026, 0, 1)).daysLeft).toBe(365);
  });

  test('the last day of the year asks for everything that is left, not a fraction', () => {
    const pace = goalPace(30, 28, new Date(2026, 11, 31));

    expect(pace.daysLeft).toBe(1);
    expect(pace.perWeek).toBe(2);
  });

  test('a leap year counts its extra day', () => {
    expect(goalPace(10, 0, new Date(2028, 0, 1)).daysLeft).toBe(366);
  });

  test('no target means no progress to show', () => {
    expect(goalPace(0, 5, JULY_FIRST).percent).toBe(0);
  });
});

describe('goalUnitOf', () => {
  test('keeps the units the API declares', () => {
    for (const unit of GOAL_UNITS) {
      expect(goalUnitOf(unit)).toBe(unit);
    }
  });

  test('falls back to books for anything else', () => {
    expect(goalUnitOf(undefined)).toBe('BOOKS');
    expect(goalUnitOf('CHAPTERS')).toBe('BOOKS');
  });
});

import { describe, expect, test } from 'vitest';
import { levelProgress, progressToNext, tierKey } from './achievements';

describe('achievements (#287)', () => {
  test('names the tiers of a tiered achievement only', () => {
    expect(tierKey(1, 1)).toBeNull();
    expect(tierKey(1, 4)).toBe('bronze');
    expect(tierKey(3, 4)).toBe('gold');
    expect(tierKey(0, 4)).toBeNull();
  });

  test('measures the way to the next tier and the next level', () => {
    expect(progressToNext({ code: 'X', family: 'READING', tiers: 2, tier: 0, value: 5, nextThreshold: 10, xpEarned: 0 })).toBe(0.5);
    expect(progressToNext({ code: 'X', family: 'READING', tiers: 1, tier: 1, value: 3, xpEarned: 20 })).toBe(1);
    expect(levelProgress(75, 50, 141)).toBeCloseTo(25 / 91);
  });
});

import { describe, expect, test } from 'vitest';
import { runLength } from './series';

const numbered = (count: number) => Array.from({ length: count }, (_, i) => ({ volumeNumber: i + 1 }));

/** #633: the run's length the series screen divides by. */
describe('runLength', () => {
  test('is the announced total when it covers the volumes listed', () => {
    expect(runLength(12, numbered(4))).toBe(12);
  });

  test('is never below the numbered volumes listed ("6/3 tomes")', () => {
    expect(runLength(3, numbered(6))).toBe(6);
  });

  test('counts the numbered volumes when no total is announced', () => {
    expect(runLength(undefined, [...numbered(3), { volumeNumber: undefined }])).toBe(3);
  });
});

import { describe, expect, test } from 'vitest';
import { pagesRead } from './pagesRead';

describe('pagesRead', () => {
  test('is the exact sum once every read book has a length', () => {
    expect(pagesRead({ read: 3, pagesRead: 900, readWithPageCount: 3 })).toEqual({ value: 900, estimated: false, unknown: false });
    // An older API, which does not say: taken as complete.
    expect(pagesRead({ read: 3, pagesRead: 900 })).toEqual({ value: 900, estimated: false, unknown: false });
  });

  // #414: the server sums the estimated lengths of the books it knows no length for.
  test('shows the server sum, marked as an estimate while some lengths are estimated', () => {
    expect(pagesRead({ read: 2293, pagesRead: 740_000, readWithPageCount: 44 })).toEqual({
      value: 740_000,
      estimated: true,
      unknown: false,
    });
  });

  test('never leaves a hole, however few lengths are known', () => {
    expect(pagesRead({ read: 2293, pagesRead: 700_000, readWithPageCount: 1 }).unknown).toBe(false);
  });
});

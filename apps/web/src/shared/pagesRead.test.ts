import { describe, expect, test } from 'vitest';
import { pagesRead } from './pagesRead';

describe('pagesRead', () => {
  test('is the exact sum once every read book has a length', () => {
    expect(pagesRead({ read: 3, pagesRead: 900, readWithPageCount: 3 })).toEqual({ value: 900, estimated: false, unknown: false });
    // An older API, which does not say: taken as complete.
    expect(pagesRead({ read: 3, pagesRead: 900 })).toEqual({ value: 900, estimated: false, unknown: false });
  });

  test('extrapolates the known books to all of them, and says so', () => {
    // 44 books averaging 370 pages, out of 2 293 read.
    expect(pagesRead({ read: 2293, pagesRead: 16280, readWithPageCount: 44 })).toEqual({
      value: 848410,
      estimated: true,
      unknown: false,
    });
  });

  test('says nothing from too small a sample', () => {
    expect(pagesRead({ read: 2293, pagesRead: 300, readWithPageCount: 1 }).unknown).toBe(true);
  });
});

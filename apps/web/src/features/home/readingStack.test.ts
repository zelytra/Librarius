import { afterEach, describe, expect, test } from 'vitest';
import i18n from '../../i18n';
import {
  MAX_SPINES,
  PAGE_THICKNESS_MM,
  SHEET_THICKNESS_MM,
  formatPaperHeight,
  paperHeight,
  stackSpines,
} from './readingStack';

/**
 * The two decisions the illustration rests on: how tall to draw the stack, and how much
 * paper the pages read come to. Both are exercised at the two ends of the persona range in
 * `PRODUCT.md` § 2 — Sarah the newcomer and Léa's 400 volumes — because "it looks right on
 * my own library" is exactly the check that misses one of them.
 */

describe('stackSpines', () => {
  test('draws nothing at all for a library with nothing read', () => {
    expect(stackSpines(0)).toBe(0);
    // Never asked for by the component, which hides the section first, but a negative
    // count must not produce a negative height either.
    expect(stackSpines(-3)).toBe(0);
  });

  test('draws a small shelf one spine per book', () => {
    expect(stackSpines(1)).toBe(1);
    expect(stackSpines(3)).toBe(3);
    expect(stackSpines(8)).toBe(8);
  });

  /** Léa's 400 volumes: still legible, still on the screen, still visibly a lot. */
  test('compresses a large library instead of running off the screen', () => {
    expect(stackSpines(400)).toBe(MAX_SPINES);
    expect(stackSpines(3000)).toBe(MAX_SPINES);
    expect(stackSpines(1_000_000)).toBe(MAX_SPINES);
  });

  /** Each doubling past the small shelf buys exactly one more spine. */
  test('grows by one spine per doubling once past the exact range', () => {
    expect(stackSpines(16)).toBe(9);
    expect(stackSpines(32)).toBe(10);
    expect(stackSpines(64)).toBe(11);
    expect(stackSpines(128)).toBe(12);
  });

  /** Marc's 120 novels sit between the two ends, and land in the middle of the scale. */
  test('leaves the mid-sized library in the middle of the scale', () => {
    const spines = stackSpines(120);
    expect(spines).toBeGreaterThan(stackSpines(8));
    expect(spines).toBeLessThan(MAX_SPINES);
  });

  /**
   * Reading one more book can never shrink the drawing. The first shape tried divided the
   * books between a fixed number of spines, and going from 14 books to 15 took the stack
   * from fourteen spines down to eight.
   */
  test('never shrinks as the library grows', () => {
    let previous = 0;
    for (let books = 0; books <= 600; books += 1) {
      const spines = stackSpines(books);
      expect(spines).toBeGreaterThanOrEqual(previous);
      expect(spines).toBeLessThanOrEqual(MAX_SPINES);
      previous = spines;
    }
  });
});

describe('paperHeight', () => {
  test('a page is half a sheet, since a sheet is printed on both sides', () => {
    expect(PAGE_THICKNESS_MM).toBe(SHEET_THICKNESS_MM / 2);
  });

  test('converts pages to paper at the documented sheet thickness', () => {
    // 20 000 pages = 10 000 sheets = 1 000 mm of paper.
    expect(paperHeight(20_000)).toEqual({ value: 1, unit: 'meter' });
    expect(paperHeight(42_000)).toEqual({ value: 2.1, unit: 'meter' });
  });

  /**
   * A first novel is two centimetres of paper. Expressed in metres it rounds to "0 m",
   * which is the one figure the section exists to show turned into nothing.
   */
  test('stays in centimetres below a metre', () => {
    expect(paperHeight(900)).toEqual({ value: 4.5, unit: 'centimeter' });
    expect(paperHeight(4200)).toEqual({ value: 21, unit: 'centimeter' });
  });

  test('reads zero pages as no paper rather than as a negative height', () => {
    expect(paperHeight(0)).toEqual({ value: 0, unit: 'centimeter' });
    expect(paperHeight(-100)).toEqual({ value: 0, unit: 'centimeter' });
  });
});

describe('formatPaperHeight', () => {
  afterEach(() => i18n.changeLanguage('fr'));

  test('carries its unit, in the notation of the language in force', async () => {
    expect(formatPaperHeight(42_000)).toMatch(/^2,1\s?m$/u);

    await i18n.changeLanguage('en');
    expect(formatPaperHeight(42_000)).toBe('2.1 m');
    expect(formatPaperHeight(4200)).toBe('21 cm');
  });
});

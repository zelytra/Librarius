import { beforeEach, describe, expect, test } from 'vitest';
import {
  SHEET_THEME_STORAGE_KEY,
  pageRule,
  printMargin,
  readSheetTheme,
  sheetIdsFromParams,
  sheetSize,
  storeSheetTheme,
} from './readingSheet';

describe('sheet formats (#548)', () => {
  // The ISO 216 sizes, to the millimetre: the sheet is drawn at that size, not merely capped.
  test.each([
    ['A4', 210, 297],
    ['A5', 148, 210],
    ['A6', 105, 148],
  ] as const)('%s is %i × %i mm, on screen and on paper', (format, width, height) => {
    expect(sheetSize(format)).toEqual({ width: `${width}mm`, height: `${height}mm` });
    expect(pageRule(format)).toBe(`@page { size: ${width}mm ${height}mm; margin: 0; }`);
  });

  test('widens only the inner margin for a ring binder', () => {
    expect(printMargin('A6')).toBe('6mm 6mm 6mm 6mm');
    expect(printMargin('A6', true)).toBe('6mm 6mm 6mm 14mm');
  });
});

describe('sheet theme (#550)', () => {
  beforeEach(() => localStorage.removeItem(SHEET_THEME_STORAGE_KEY));

  test('is handwritten until the reader picks another one, and remembers the pick', () => {
    expect(readSheetTheme()).toBe('handwritten');
    storeSheetTheme('notebook');
    expect(readSheetTheme()).toBe('notebook');
  });

  test('ignores a stored value it does not know', () => {
    localStorage.setItem(SHEET_THEME_STORAGE_KEY, 'neon');
    expect(readSheetTheme()).toBe('handwritten');
  });
});

describe('sheetIdsFromParams (#551)', () => {
  test('reads the titles and the series of a selection, dropping blanks and repeats', () => {
    const params = new URLSearchParams('items=a,b,,a&series=s1');
    expect(sheetIdsFromParams(params)).toEqual({ items: ['a', 'b'], series: ['s1'] });
  });

  test('is empty without parameters', () => {
    expect(sheetIdsFromParams(new URLSearchParams())).toEqual({ items: [], series: [] });
  });
});

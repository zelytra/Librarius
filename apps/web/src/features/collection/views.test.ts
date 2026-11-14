import { describe, expect, test } from 'vitest';
import { byCategory, inManualOrder, keyOf, mergedOrder, moveKey, type ShelfEntry } from './views';

const item = (id: string, rankCode?: string): ShelfEntry => ({ item: { id, rankCode } });
const series = (id: string, rankCode?: string): ShelfEntry => ({ series: { id, rankCode } });

describe('library views', () => {
  test('names a title and a series block apart', () => {
    expect(keyOf(item('a'))).toBe('item:a');
    expect(keyOf(series('s'))).toBe('series:s');
  });

  test('lays the placed entries out first, in order, the others after as they came', () => {
    const entries = [item('a'), item('b'), series('s'), item('c')];
    const ordered = inManualOrder(entries, ['item:c', 'series:s']);
    expect(ordered.map(keyOf)).toEqual(['item:c', 'series:s', 'item:a', 'item:b']);
  });

  test('moves one key, and keeps what another shelf placed', () => {
    expect(moveKey(['a', 'b', 'c'], 0, 2)).toEqual(['b', 'c', 'a']);
    expect(moveKey(['a', 'b', 'c'], 2, 0)).toEqual(['c', 'a', 'b']);
    expect(mergedOrder(['b', 'a'], ['a', 'x', 'b', 'y'])).toEqual(['b', 'a', 'x', 'y']);
  });

  test('splits the shelf by category in the reader’s order, a series whole, the unfiled last', () => {
    const categories = [
      { code: 'or', label: 'Or', color: '#c9a227' },
      { code: 'argent', label: 'Argent' },
      { code: 'bronze', label: 'Bronze' },
    ];
    const sections = byCategory([item('a'), series('s', 'or'), item('b', 'argent'), item('c', 'or')], categories);
    expect(sections.map((s) => [s.code, s.entries.map(keyOf)])).toEqual([
      ['or', ['series:s', 'item:c']],
      ['argent', ['item:b']],
      [undefined, ['item:a']],
    ]);
  });
});

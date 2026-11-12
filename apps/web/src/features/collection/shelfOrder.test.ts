import { describe, expect, test } from 'vitest';
import type { LibraryItemDto, SeriesSummaryDto } from '../../api/generated/librarius';
import { interleave } from './shelfOrder';

/** #387: grouped, the author and genre sorts file the series among the one-shots. */

const item = (id: string, authors: string, genres?: string): LibraryItemDto => ({
  id,
  book: { title: id, authors, genres },
});
const run = (id: string, authors?: string, genre?: string): SeriesSummaryDto => ({ id, title: id, authors, genre });

const order = (entries: ReturnType<typeof interleave>) =>
  entries.map((e) => ('series' in e ? `S:${e.series.id}` : e.item.id));

describe('interleave', () => {
  test('files each series among the titles by author', () => {
    const items = [item('a1', 'Asimov'), item('h1', 'Herbert'), item('t1', 'Tolkien')];
    const series = [run('berserk', 'Miura'), run('akira', 'Otomo'), run('dune', 'Herbert')];

    expect(order(interleave(series, items, 'author', false))).toEqual([
      'a1', 'S:dune', 'h1', 'S:berserk', 'S:akira', 't1',
    ]);
  });

  test('files by first genre, unknown genres last', () => {
    const items = [item('f', 'X', 'Fantasy, Aventure'), item('p', 'X', 'Policier')];
    const series = [run('none'), run('seinen', 'Y', 'seinen')];

    expect(order(interleave(series, items, 'genre', false))).toEqual(['f', 'p', 'S:seinen', 'S:none']);
  });

  test('holds back a series sorting after every loaded title while pages remain', () => {
    const items = [item('a1', 'Asimov')];
    const series = [run('zz', 'Zola')];

    expect(order(interleave(series, items, 'author', true))).toEqual(['a1']);
    expect(order(interleave(series, items, 'author', false))).toEqual(['a1', 'S:zz']);
  });
});

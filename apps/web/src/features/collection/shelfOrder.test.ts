import { describe, expect, test } from 'vitest';
import type { LibraryItemDto, SeriesSummaryDto } from '../../api/generated/librarius';
import { interleave, mergeByActivity } from './shelfOrder';

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

/** #473: the latest-activity shelf keeps the server's order of titles, page after page. */
describe('mergeByActivity', () => {
  const item = (id: string, acquiredAt?: string) => ({ id, acquiredAt }) as LibraryItemDto;
  const series = (id: string, lastActivity: string) => ({ id, lastActivity }) as SeriesSummaryDto;
  const keys = (out: ReturnType<typeof mergeByActivity>) =>
    out.map((e) => ('series' in e ? `s:${e.series.id}` : `i:${e.item.id}`));

  test('never moves a title: one with no date the screen can read stays where the server put it', () => {
    // The server orders on the day added when nothing else is known; the screen cannot see that
    // day, and used to sink such a title below the next page's dated ones.
    const page1 = [item('a', '2026-11-10'), item('b'), item('c', '2026-11-01')];
    expect(keys(mergeByActivity([], page1, true))).toEqual(['i:a', 'i:b', 'i:c']);
    const page2 = [...page1, item('d', '2026-10-20')];
    expect(keys(mergeByActivity([], page2, false))).toEqual(['i:a', 'i:b', 'i:c', 'i:d']);
  });

  test('files a series before the first title less recent than it', () => {
    const items = [item('a', '2026-11-10'), item('c', '2026-11-01')];
    expect(keys(mergeByActivity([series('s', '2026-11-05')], items, false))).toEqual(['i:a', 's:s', 'i:c']);
  });

  test('keeps a series older than every loaded title for the page that reaches its date', () => {
    const items = [item('a', '2026-11-10')];
    expect(keys(mergeByActivity([series('s', '2020-01-01')], items, true))).toEqual(['i:a']);
    expect(keys(mergeByActivity([series('s', '2020-01-01')], items, false))).toEqual(['i:a', 's:s']);
  });
});

import { describe, expect, test } from 'vitest';
import { searchFromUrl, searchToUrl } from './searchUrl';

describe('search in the URL (#435)', () => {
  test('round-trips every criterion', () => {
    const params = { q: 'dune', author: 'Herbert', year: 1965, language: 'fr', kind: ['BOOK' as const, 'MANGA' as const] };
    expect(searchFromUrl(searchToUrl(params))).toEqual({ ...params, isbn: undefined, publisher: undefined });
  });

  test('holds no search when nothing was searched', () => {
    expect(searchFromUrl(new URLSearchParams())).toBeNull();
    expect(searchFromUrl(new URLSearchParams('kind=BOOK'))).toBeNull();
  });

  test('ignores a medium it does not know', () => {
    expect(searchFromUrl(new URLSearchParams('q=x&kind=VINYL'))?.kind).toBeUndefined();
  });
});

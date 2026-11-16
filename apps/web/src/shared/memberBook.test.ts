import { describe, expect, test } from 'vitest';
import { bookTarget } from './memberBook';

/** #513: where a book seen on someone else's profile or in the feed leads. */
describe('bookTarget', () => {
  test("opens the reader's own Detail when they hold the work too", () => {
    expect(bookTarget({ editionId: 'e1', title: 'Dune', myItemId: 'item-9' })).toEqual({ to: '/detail/item-9' });
  });

  test('opens the catalog page otherwise, carrying the book', () => {
    const target = bookTarget({
      editionId: 'e1', kind: 'BOOK', title: 'Dune', authors: 'Frank Herbert', coverUrl: 'c.jpg',
      isbn13: '9782266320481', provider: 'openlibrary', providerRef: 'OL1W', year: 1965,
    });
    expect(target.to).toBe('/catalog/openlibrary/OL1W');
    expect(target.state?.result).toMatchObject({
      title: 'Dune', authors: 'Frank Herbert', coverUrl: 'c.jpg', isbn13: '9782266320481', kind: 'BOOK', year: 1965,
    });
  });

  test('a book entered by hand still opens, under its edition', () => {
    expect(bookTarget({ editionId: 'e1', title: 'Notes' }).to).toBe('/catalog/librarius/e1');
  });
});

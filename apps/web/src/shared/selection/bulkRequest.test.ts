import { describe, expect, test } from 'vitest';
import { bulkRequest } from './bulkRequest';

describe('bulkRequest', () => {
  test('sends the series picked whole apart from the titles, and only the changes picked', () => {
    expect(bulkRequest(['item-1', 'series:s1', 'item-2'], { rating: 'clear', category: 'clear' })).toEqual({
      ids: ['item-1', 'item-2'],
      seriesIds: ['s1'],
      clearCategory: true,
      clearRating: true,
    });
  });
});

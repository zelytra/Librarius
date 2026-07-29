import { describe, expect, test } from 'vitest';
import { chartMax, cumulative, monthLabels, monthlySeries } from './timeline';

/**
 * A chart that is subtly wrong looks exactly like a chart that is right: the series the
 * bars are drawn from is checked here against a dataset whose every figure is known.
 */
describe('monthlySeries', () => {
  /** Three months out of twelve, exactly what the API answers with. */
  const points = [
    { period: '2026-01', books: 3, pages: 300 },
    { period: '2026-03', books: 1, pages: 150 },
    { period: '2026-12', books: 4, pages: 200 },
  ];

  test('pads the year out to its twelve months, in order', () => {
    const series = monthlySeries(points, 2026);

    expect(series).toHaveLength(12);
    expect(series.map((month) => month.month)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]);
    expect(series.map((month) => month.books)).toEqual([3, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 4]);
    expect(series.map((month) => month.pages)).toEqual([300, 0, 150, 0, 0, 0, 0, 0, 0, 0, 0, 200]);
  });

  /** The window is picked by the client, so a stale answer must not bleed into it. */
  test('ignores the buckets of another year', () => {
    const series = monthlySeries([...points, { period: '2025-01', books: 99, pages: 9999 }], 2026);

    expect(series[0]).toEqual({ month: 1, books: 3, pages: 300 });
  });

  test('reads a year with no reading as twelve zeros', () => {
    expect(monthlySeries([], 2026).every((month) => month.books === 0)).toBe(true);
    expect(monthlySeries(undefined, 2026)).toHaveLength(12);
  });

  /** Year granularity carries no month: those buckets belong to no slot. */
  test('leaves out the buckets that carry no month', () => {
    const series = monthlySeries([{ period: '2026', books: 8, pages: 650 }], 2026);

    expect(series.every((month) => month.books === 0)).toBe(true);
  });
});

describe('cumulative', () => {
  test('adds the months up as the year goes by', () => {
    expect(cumulative([3, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 4])).toEqual([
      3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 8,
    ]);
    expect(cumulative([])).toEqual([]);
  });
});

describe('chartMax', () => {
  /** Never zero: an empty year would otherwise divide every bar by nothing. */
  test('never returns zero', () => {
    expect(chartMax([3, 1, 4])).toBe(4);
    expect(chartMax([0, 0, 0])).toBe(1);
    expect(chartMax([])).toBe(1);
  });
});

describe('monthLabels', () => {
  test('takes the month names from the locale', () => {
    expect(monthLabels('narrow')).toHaveLength(12);
    expect(monthLabels('long')[0]).toBe('janvier');
    expect(monthLabels('long')[11]).toBe('décembre');
  });
});

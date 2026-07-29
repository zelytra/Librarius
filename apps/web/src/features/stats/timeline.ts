import type { TimelinePointDto } from '../../api/generated/librarius';

/**
 * Turning the timeline the API answers with into something chartable.
 *
 * <p>The API only reports the buckets the user actually read something in, so the payload
 * follows the data rather than the range asked for. A chart needs the opposite: twelve
 * slots, in order, holes included — a year with two blank months has to look like one.
 */

/** One month of the chart, whether the user read anything in it or not. */
export interface MonthPoint {
  /** 1 to 12. */
  month: number;
  books: number;
  pages: number;
}

/** The two things a month can be measured in. */
export type Metric = 'books' | 'pages';

/** Pads a year out to its twelve months, in order, missing buckets reading zero. */
export function monthlySeries(
  points: TimelinePointDto[] | undefined,
  year: number,
): MonthPoint[] {
  const byMonth = new Map<number, TimelinePointDto>();
  for (const point of points ?? []) {
    const [bucketYear, bucketMonth] = (point.period ?? '').split('-');
    if (Number(bucketYear) === year && bucketMonth) {
      byMonth.set(Number(bucketMonth), point);
    }
  }

  return Array.from({ length: 12 }, (_, index) => {
    const point = byMonth.get(index + 1);
    return { month: index + 1, books: point?.books ?? 0, pages: point?.pages ?? 0 };
  });
}

/** Running total of a series — what a goal is actually measured against. */
export function cumulative(values: number[]): number[] {
  let total = 0;
  return values.map((value) => (total += value));
}

/**
 * The tallest value a chart has to fit, never zero: an empty year would otherwise divide
 * every bar by nothing.
 */
export function chartMax(values: number[]): number {
  return Math.max(1, ...values);
}

/** Month names of the locale, from its own calendar rather than a hardcoded list. */
export function monthLabels(style: 'narrow' | 'long'): string[] {
  const format = new Intl.DateTimeFormat('fr-FR', { month: style });
  return Array.from({ length: 12 }, (_, index) => format.format(new Date(2026, index, 1)));
}

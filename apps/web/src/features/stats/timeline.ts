import type { TimelinePointDto } from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';

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
  const format = new Intl.DateTimeFormat(activeLanguage(), { month: style });
  return Array.from({ length: 12 }, (_, index) => format.format(new Date(2026, index, 1)));
}

/** How many years the selector offers at most: the most recent ones. */
const MAX_YEARS = 6;

/**
 * The years the selector offers, most recent first: the current year and the last one always —
 * the goal is about the first, January readers look back at the second — then every year that
 * holds a finished reading, and the year on screen in any case, capped to the most recent few.
 */
export function offeredYears(
  thisYear: number,
  selected: number,
  points: { period?: string; books?: number }[] = []
): number[] {
  const years = new Set([thisYear, thisYear - 1, selected]);
  for (const point of points) {
    const value = Number((point.period ?? '').slice(0, 4));
    if (Number.isInteger(value) && value > 1900 && value <= thisYear && (point.books ?? 0) > 0) {
      years.add(value);
    }
  }
  return [...years].sort((a, b) => b - a).slice(0, MAX_YEARS);
}

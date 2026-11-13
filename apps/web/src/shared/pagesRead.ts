import type { StatsDto } from '../api/generated/librarius';

export interface PagesRead {
  /** The figure to show: the exact sum, or an estimate for the whole of what was read. */
  value: number;
  /** Whether `value` is extrapolated — to be printed with "≈". */
  estimated: boolean;
  /** No figure worth showing at all: too few books of known length to say anything. */
  unknown: boolean;
}

/**
 * How many pages the reader has read, as a screen should state it.
 *
 * <p>Since #414 the server counts every read book: at its real length when a catalog gave
 * one, at an estimate otherwise (the series average, else a typical length for its kind).
 * The sum is therefore always a figure worth showing; it is marked "≈" as long as some of it
 * rests on estimates — the books read without a known length.
 */
export function pagesRead(stats: StatsDto | undefined): PagesRead {
  const read = stats?.read ?? 0;
  const pages = stats?.pagesRead ?? 0;
  const known = stats?.readWithPageCount ?? read;
  return { value: pages, estimated: read > 0 && known < read, unknown: false };
}

import type { StatsDto } from '../api/generated/librarius';

/** Below this many books of known length, an average says nothing about the rest. */
const MIN_SAMPLE = 10;

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
 * An imported library starts with almost no lengths — the catalog fills them in over the
 * following hours — so the exact sum read "16 275 pages" under 2 293 books: true of the 44
 * books whose length was known, and wildly wrong as an answer to "how much have I read". While
 * lengths are missing, the known books' average stands for the rest, and says it is an
 * estimate; once every read book has a length, the figure is the exact sum again.
 */
export function pagesRead(stats: StatsDto | undefined): PagesRead {
  const read = stats?.read ?? 0;
  const pages = stats?.pagesRead ?? 0;
  const known = stats?.readWithPageCount ?? read;
  if (read <= 0 || known >= read) return { value: pages, estimated: false, unknown: false };
  if (known < MIN_SAMPLE) return { value: 0, estimated: false, unknown: true };
  return { value: Math.round((pages / known) * read), estimated: true, unknown: false };
}

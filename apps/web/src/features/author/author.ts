import type { AuthorWorkDto, CatalogResult } from '../../api/generated/librarius';

/**
 * Pure reading of the author-facing shapes. Kept out of the components so both the Author
 * page and the name links on Detail read a credit line the same way.
 */

/**
 * Separators a credit line may use — the same set `AuthorNormalizer` splits on server side
 * (`apps/api/.../author/AuthorNormalizer.java`), minus the fold: this only has to isolate the
 * names well enough to search each of them, not to key them the way the database does.
 */
const SEPARATORS = /[,;/|&\r\n]/;

/** Splits a free-text credit line into names, trimmed and with the empty parts dropped. */
export function splitAuthorNames(creditLine?: string): string[] {
  if (!creditLine) return [];
  return creditLine
    .split(SEPARATORS)
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
}

/**
 * Two letters standing in for a photo: the first letter of the first and the last word of
 * the name, or the first two of a one-word name. The same spirit as `BookCover`'s fallback
 * when a title has no cover — something legible where an image would have been.
 */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/** One line under a bibliography tile: the series and volume when known, the kind otherwise. */
export function workCaption(work: AuthorWorkDto): string {
  if (work.seriesTitle && work.volumeNumber != null) {
    return `${work.seriesTitle} — ${work.volumeNumber}`;
  }
  // The kind is already the tile's badge: repeating it underneath said "LIVRE" twice. The
  // year is what tells two titles of one bibliography apart.
  return work.seriesTitle ?? (work.originalYear != null ? String(work.originalYear) : '');
}

/**
 * A bibliography entry the reader does not own, as the catalog page reads it: what the
 * bibliography carries is enough to show the title and to add it, the way a search result is.
 */
export function workAsCatalogResult(work: AuthorWorkDto): CatalogResult {
  return {
    kind: work.kind,
    title: work.title,
    authors: work.authors,
    year: work.originalYear,
    coverUrl: work.coverUrl,
    seriesTitle: work.seriesTitle,
    volumeNumber: work.volumeNumber,
  };
}

/** One entry of the bibliography as shown: a standalone title, or a whole series. */
export type BibliographyEntry =
  | { kind: 'work'; work: AuthorWorkDto }
  | {
      kind: 'series';
      title: string;
      /** The series screen, when the run is one the shared catalog holds. */
      seriesId?: string;
      /** The volumes known for the series, owned or not. */
      volumes: AuthorWorkDto[];
      owned: number;
      coverUrl?: string;
      coverIsOwn?: boolean;
    };

/**
 * Folds the volumes of one series into a single entry: a manga author's page listed every
 * volume one after the other — forty tiles of One Piece — where a reader looks for the series.
 * Volumes key on their series title, folded; the entry keeps the position of the first volume
 * met, so the bibliography's order (the reader's own titles first) still holds.
 */
export function groupBibliography(works: AuthorWorkDto[]): BibliographyEntry[] {
  const entries: BibliographyEntry[] = [];
  const bySeries = new Map<string, Extract<BibliographyEntry, { kind: 'series' }>>();
  const fold = (value: string) =>
    value
      .normalize('NFD')
      .replace(/\p{M}+/gu, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, ' ')
      .trim();
  // A work titled exactly like a series of the page — "One Piece" next to its volumes — is the
  // series itself, as a catalogue lists it: it belongs to that group, not beside it.
  const seriesKeys = new Set(works.filter((w) => w.seriesTitle?.trim()).map((w) => fold(w.seriesTitle!)));
  for (const work of works) {
    const series = work.seriesTitle?.trim() || (seriesKeys.has(fold(work.title ?? '')) ? work.title : undefined);
    if (!series) {
      entries.push({ kind: 'work', work });
      continue;
    }
    const key = fold(series);
    const isVolume = Boolean(work.seriesTitle?.trim());
    let entry = bySeries.get(key);
    if (!entry) {
      entry = { kind: 'series', title: series, volumes: [], owned: 0 };
      bySeries.set(key, entry);
      entries.push(entry);
    }
    if (isVolume) entry.volumes.push(work);
    if (isVolume && work.libraryItemId) entry.owned += 1;
    entry.seriesId ??= work.seriesId ?? undefined;
    // The reader's own first volume draws the series, else the first volume with a cover.
    const ownCover = Boolean(work.libraryItemId && work.coverUrl);
    if (work.coverUrl && (!entry.coverUrl || (ownCover && !entry.coverIsOwn))) {
      entry.coverUrl = work.coverUrl;
      entry.coverIsOwn = ownCover;
    }
  }
  // A "series" of a single volume nobody owns is just a title: shown as such.
  return entries.map((entry) =>
    entry.kind === 'series' && entry.volumes.length === 1 && entry.owned === 0
      ? { kind: 'work', work: entry.volumes[0] }
      : entry,
  );
}

/** One year of an author's bibliography: the year, or null for titles of unknown date. */
export interface BibliographyYear {
  year: number | null;
  entries: BibliographyEntry[];
}

/** The year an entry stands under: a title's own, a series' first volume's. */
function yearOf(entry: BibliographyEntry): number | null {
  if (entry.kind === 'work') return entry.work.originalYear ?? null;
  const years = entry.volumes.map((v) => v.originalYear).filter((y): y is number => y != null);
  return years.length > 0 ? Math.min(...years) : null;
}

/**
 * The bibliography by year, newest first: an author's page read as an unsorted wall of
 * covers, where a reader thinks of a career. A series sits under the year it began; the
 * titles nobody dated come last, under a group of their own. Within a year the order of
 * `groupBibliography` holds.
 */
export function groupByYear(entries: BibliographyEntry[]): BibliographyYear[] {
  const byYear = new Map<number | null, BibliographyEntry[]>();
  for (const entry of entries) {
    const year = yearOf(entry);
    const list = byYear.get(year) ?? [];
    list.push(entry);
    byYear.set(year, list);
  }
  return [...byYear.entries()]
    .sort(([a], [b]) => (a == null ? 1 : b == null ? -1 : b - a))
    .map(([year, list]) => ({ year, entries: list }));
}

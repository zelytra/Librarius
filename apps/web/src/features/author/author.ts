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

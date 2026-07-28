import type { BookView, SeriesSummaryDto, SeriesVolumeDto } from '../../api/generated/librarius';

/**
 * Pure reading of what `/api/series` returns. Kept out of the components so both the
 * Series screen and the Series view of the collection read a run the same way.
 */

/** The four states a volume can be in, in the order a run goes through them. */
export type VolumeState = 'read' | 'owned' | 'missing' | 'upcoming';

/**
 * State of one volume. The four API flags are not exclusive — a read volume is also an
 * owned one — so they are resolved into the single state the grid paints.
 */
export function volumeState(volume: SeriesVolumeDto): VolumeState {
  if (volume.read) return 'read';
  if (volume.owned) return 'owned';
  if (volume.missing) return 'missing';
  // Neither owned nor below the highest owned volume: still ahead of the reader. This is
  // also what a followed series with nothing owned yet looks like.
  return 'upcoming';
}

/** The holes in the run, in order — the volume numbers the Series screen offers to fill. */
export function missingVolumes(volumes: SeriesVolumeDto[]): number[] {
  return volumes
    .filter((v) => v.missing && v.volumeNumber != null)
    .map((v) => v.volumeNumber!);
}

/**
 * Denominator of the `x / y` progress. The announced total is the reference; without one
 * the number of volumes the API listed is the best that can be said, and it is never
 * smaller than what the user owns.
 */
export function runLength(totalVolumes: number | undefined, volumes: SeriesVolumeDto[]): number {
  if (totalVolumes && totalVolumes > 0) return totalVolumes;
  return volumes.filter((v) => v.volumeNumber != null).length;
}

/** Orderings offered on the Series view of the collection. */
export type SeriesSort = 'progress' | 'title';

/** Sort chips, in display order, each with the key of its label. */
export const SERIES_SORTS: { id: SeriesSort; labelKey: string }[] = [
  { id: 'progress', labelKey: 'series.sorts.progress' },
  { id: 'title', labelKey: 'series.sorts.title' },
];

/** Share of the announced run the user owns, between 0 and 1. Unknown total → 0. */
export function completion(series: SeriesSummaryDto): number {
  const total = series.totalVolumes ?? 0;
  if (total <= 0) return 0;
  return Math.min(1, (series.ownedCount ?? 0) / total);
}

/** A run with volumes still to buy — what the Series view exists to surface. */
export function isIncomplete(series: SeriesSummaryDto): boolean {
  const total = series.totalVolumes ?? 0;
  return total > 0 && (series.ownedCount ?? 0) < total;
}

/**
 * Narrows the series list down to what the collection filters ask for.
 *
 * `/api/series` takes no query parameter: it answers with the series the user has a stake
 * in, one row each, ordered by title. That is a few dozen entries — orders of magnitude
 * below the collection itself — so filtering here rather than server-side keeps the
 * List / Series toggle instant and costs one request for the whole view.
 */
export function filterSeries(
  list: SeriesSummaryDto[],
  { kind, search, sort }: { kind: string; search: string; sort: SeriesSort },
): SeriesSummaryDto[] {
  const needle = search.trim().toLowerCase();
  const matching = list.filter(
    (s) => s.kind === kind && (!needle || (s.title ?? '').toLowerCase().includes(needle)),
  );
  // Least complete first: the point of the view is the run that still has holes in it.
  if (sort === 'progress') {
    return [...matching].sort((a, b) => completion(a) - completion(b));
  }
  return [...matching].sort((a, b) => (a.title ?? '').localeCompare(b.title ?? '', 'fr'));
}

/**
 * Identifier of the series a title belongs to, or `undefined` when it belongs to none.
 *
 * `BookView` carries the series *title* but no identifier, so the link from a volume to
 * its series is resolved against the caller's own series — matched on kind and title, the
 * very key the API attaches a work to a series with. A `seriesId` on `BookView` would
 * remove this lookup; see the pull request.
 */
export function seriesIdOf(list: SeriesSummaryDto[], book: BookView | undefined): string | undefined {
  const title = book?.seriesTitle?.trim().toLowerCase();
  if (!title) return undefined;
  return list.find((s) => s.kind === book?.kind && s.title?.trim().toLowerCase() === title)?.id;
}

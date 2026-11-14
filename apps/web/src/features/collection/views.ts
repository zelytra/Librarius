/**
 * The pure half of the library views (#451): how an entry is named in a view's manual order,
 * how the shelf is laid out in that order, and how it splits into one section per category.
 */
import type { CategoryDto, LibraryItemDto, SeriesSummaryDto } from '../../api/generated/librarius';

/** One cell of the shelf: a series block, or a single title. */
export type ShelfEntry = { series: SeriesSummaryDto } | { item: LibraryItemDto };

/** The key an entry has in a view's manual order: `series:<id>` or `item:<id>`. */
export function keyOf(entry: ShelfEntry): string {
  return 'series' in entry ? `series:${entry.series.id}` : `item:${entry.item.id}`;
}

/**
 * The entries in the order the reader placed them; an entry never placed follows the placed
 * ones, in the order it came in.
 */
export function inManualOrder<T extends ShelfEntry>(entries: T[], order: string[]): T[] {
  const position = new Map(order.map((key, index) => [key, index]));
  return entries
    .map((entry, index) => ({ entry, index, at: position.get(keyOf(entry)) }))
    .sort((a, b) => {
      if (a.at != null && b.at != null) return a.at - b.at;
      if (a.at != null) return -1;
      if (b.at != null) return 1;
      return a.index - b.index;
    })
    .map((x) => x.entry);
}

/** `keys` with the one at `from` moved to `to`. */
export function moveKey(keys: string[], from: number, to: number): string[] {
  if (from === to || from < 0 || from >= keys.length) return keys;
  const next = [...keys];
  const [moved] = next.splice(from, 1);
  next.splice(Math.max(0, Math.min(to, next.length)), 0, moved);
  return next;
}

/**
 * The new manual order once the shown entries were rearranged: the shown ones in their new
 * order, then whatever the view had placed that is not shown (another shelf, a page not loaded
 * yet), so arranging one shelf never forgets another.
 */
export function mergedOrder(shown: string[], previous: string[]): string[] {
  const visible = new Set(shown);
  return [...shown, ...previous.filter((key) => !visible.has(key))];
}

/** The category an entry files under: a title's own, a series' most common one. */
export function rankOf(entry: ShelfEntry): string | undefined {
  return ('series' in entry ? entry.series.rankCode : entry.item.rankCode) ?? undefined;
}

export interface CategorySection<T> {
  /** The category's code; undefined for the titles filed under none. */
  code?: string;
  label?: string;
  color?: string;
  entries: T[];
}

/**
 * The shelf split by category, in the order the reader ranks their categories, series kept
 * whole as blocks; the entries filed under none last. Empty sections are left out.
 */
export function byCategory<T extends ShelfEntry>(entries: T[], categories: CategoryDto[]): CategorySection<T>[] {
  const sections: CategorySection<T>[] = categories.map((c) => ({
    code: c.code,
    label: c.label,
    color: c.color,
    entries: [],
  }));
  const none: CategorySection<T> = { entries: [] };
  const byCode = new Map(sections.map((s) => [s.code, s]));
  for (const entry of entries) {
    const code = rankOf(entry);
    (code ? byCode.get(code) ?? none : none).entries.push(entry);
  }
  return [...sections, none].filter((s) => s.entries.length > 0);
}

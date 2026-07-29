import type { DashboardSectionDto } from '../../api/generated/librarius';

/**
 * The Home dashboard's layout: which sections show, and in which order (#54).
 *
 * <p>Kept out of the components, and pure — the same reasoning as {@code shared/goal.ts}:
 * the interesting logic is a handful of array operations, and it is easier to get right,
 * and to test, without a render involved.
 */

/**
 * One section of the dashboard, as the API sends and receives it. `DashboardSectionDto`
 * itself, not a parallel type: `hidden` is generated as optional (a bare `boolean` carries
 * no `required` marker in the schema, unlike `@NotBlank code`), and a second type
 * declaring it mandatory would only fight that everywhere the two meet.
 */
export type DashboardSectionPref = DashboardSectionDto;

/**
 * The Home sections, in the order a fresh account sees them — the same order they used to
 * be hard-coded in before this table existed. Mirrors the backend's
 * `DashboardLayoutService.DEFAULT_ORDER`; the two are not generated from one source because
 * the wire shape (`sections: string`) is deliberately loose — see the DTO's javadoc.
 */
export const DEFAULT_SECTION_ORDER = [
  'resumeReading',
  'toRead',
  'counters',
  'goal',
  'upcoming',
  'recentlyRead',
] as const;

/**
 * What a brand new account sees, and the fallback while the real layout is still loading
 * or failed to load: every section, in the default order, none hidden. The API applies the
 * same default server-side, so this is only ever a stand-in for the instant before its
 * answer arrives — never a second source of truth a screen has to keep in step by hand.
 */
export function defaultLayout(): DashboardSectionPref[] {
  return DEFAULT_SECTION_ORDER.map((code) => ({ code, hidden: false }));
}

/**
 * Swaps the entry at `index` with its neighbour in the given direction, clamped to the
 * array's bounds — moving the first entry up, or the last one down, does nothing.
 */
export function moveSection(
  sections: DashboardSectionPref[],
  index: number,
  direction: -1 | 1,
): DashboardSectionPref[] {
  const target = index + direction;
  if (target < 0 || target >= sections.length) return sections;
  const next = [...sections];
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}

/** Flips whether the entry at `index` is hidden. */
export function toggleHidden(sections: DashboardSectionPref[], index: number): DashboardSectionPref[] {
  return sections.map((section, i) => (i === index ? { ...section, hidden: !section.hidden } : section));
}

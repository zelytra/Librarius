/**
 * Where the stack of pages read would reach, as a sky behind it (#415).
 *
 * <p>Pure, like {@code readingStack.ts}: the decision worth testing is "which scene for how
 * many pages", and it is far easier to pin down without a render.
 *
 * <p>Not to scale, on purpose. Real paper reaches the clouds after some ten thousand books and
 * the Moon never, so the scale is a story scale: each milestone is reached at a page count a
 * reader can actually get to — the clouds after a few books, the edge of space after about a
 * hundred, the Moon around four hundred, the outer planets in the low thousands — and the real
 * distance of that milestone is what the sky claims. Between two milestones the distance is
 * interpolated on a logarithmic scale, since the distances span twenty-three orders of
 * magnitude and the page counts barely three.
 */

export type SkyScene =
  | 'ground'
  | 'clouds'
  | 'stratosphere'
  | 'space'
  | 'orbit'
  | 'moon'
  | 'mars'
  | 'jupiter'
  | 'saturn'
  | 'uranus'
  | 'neptune'
  | 'pluto'
  | 'heliopause'
  | 'stars'
  | 'galaxy'
  | 'andromeda'
  | 'edge';

export interface SkyMilestone {
  scene: SkyScene;
  /** Pages read from which the stack reaches it. */
  pages: number;
  /** Its real distance from the ground, in kilometres. */
  km: number;
}

/**
 * The milestones, bottom up. Distances are the usual mean figures: Earth–planet at average
 * opposition-ish distance (the gap between the two orbits), the heliopause at 120 AU, Proxima
 * Centauri at 4.24 light years, the galactic centre at 26 000, Andromeda at 2.5 million, and
 * the edge of the observable universe at 46.5 billion.
 */
export const SKY_MILESTONES: readonly SkyMilestone[] = [
  { scene: 'ground', pages: 0, km: 0.001 },
  { scene: 'clouds', pages: 1_500, km: 2 },
  { scene: 'stratosphere', pages: 8_000, km: 20 },
  { scene: 'space', pages: 25_000, km: 100 },
  { scene: 'orbit', pages: 60_000, km: 400 },
  { scene: 'moon', pages: 120_000, km: 384_400 },
  { scene: 'mars', pages: 200_000, km: 78e6 },
  { scene: 'jupiter', pages: 300_000, km: 628e6 },
  { scene: 'saturn', pages: 400_000, km: 1.28e9 },
  { scene: 'uranus', pages: 500_000, km: 2.72e9 },
  { scene: 'neptune', pages: 600_000, km: 4.35e9 },
  { scene: 'pluto', pages: 700_000, km: 5.9e9 },
  { scene: 'heliopause', pages: 900_000, km: 1.8e10 },
  { scene: 'stars', pages: 1_200_000, km: 4.0e13 },
  { scene: 'galaxy', pages: 1_600_000, km: 2.5e17 },
  { scene: 'andromeda', pages: 2_200_000, km: 2.4e19 },
  { scene: 'edge', pages: 3_000_000, km: 4.4e23 },
];

export interface SkyPosition {
  /** The last milestone reached. */
  current: SkyMilestone;
  /** The one after it, absent past the edge of the universe. */
  next?: SkyMilestone;
  /** How far between the two, 0 to 1 — what the sky's tint blends on. */
  progress: number;
  /** Pages still to read to reach {@link next}. */
  pagesToNext: number;
  /** The distance the stack stands for on the story scale, in kilometres. */
  km: number;
}

/** Where a stack of {@code pages} pages stands among the milestones. */
export function skyPosition(pages: number): SkyPosition {
  const read = Math.max(0, Number.isFinite(pages) ? pages : 0);
  let index = 0;
  while (index + 1 < SKY_MILESTONES.length && SKY_MILESTONES[index + 1].pages <= read) index++;
  const current = SKY_MILESTONES[index];
  const next = SKY_MILESTONES[index + 1];
  if (!next) return { current, progress: 1, pagesToNext: 0, km: current.km };
  const progress = (read - current.pages) / (next.pages - current.pages);
  const km = 10 ** (Math.log10(current.km) + progress * (Math.log10(next.km) - Math.log10(current.km)));
  return { current, next, progress, pagesToNext: Math.ceil(next.pages - read), km };
}

/**
 * A distance as it reads best: metres near the ground, kilometres, then astronomical units
 * inside the solar system and light years past it.
 */
export function formatDistance(
  km: number,
  locale: string,
  units: { au: string; ly: string; mly: string },
): string {
  const number = (value: number, unit: string, digits = 0) =>
    value.toLocaleString(locale, { style: 'unit', unit, unitDisplay: 'short', maximumFractionDigits: digits });
  const AU = 149_597_870.7;
  const LIGHT_YEAR = 9.4607e12;
  if (km < 1) return number(km * 1000, 'meter');
  if (km < 1e7) return number(km, 'kilometer');
  const decimal = (value: number) => value.toLocaleString(locale, { maximumFractionDigits: 1 });
  if (km < LIGHT_YEAR / 10) return `${decimal(km / AU)} ${units.au}`;
  const years = km / LIGHT_YEAR;
  return years >= 1e6 ? `${decimal(years / 1e6)} ${units.mly}` : `${decimal(years)} ${units.ly}`;
}

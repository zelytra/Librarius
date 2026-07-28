/**
 * Cover colours for titles that have no cover image.
 *
 * Single source of truth: the palette used to be copied into HomePage,
 * CollectionPage, DetailPage and WishlistPage with a different number of
 * entries each time, so the same title came out in a different colour
 * depending on the screen it was seen from.
 *
 * These hex values stay here rather than in `tokens.css`: the colour is picked
 * in JavaScript from the title, which a CSS variable cannot do. The file is
 * named `coverPalette` and not `cover` so it can sit next to `Cover.tsx` on a
 * case-insensitive filesystem.
 */
export const PALETTE = [
  '#bccab2',
  '#cabdd6',
  '#ddb9b3',
  '#b6c6d6',
  '#dccfae',
  '#aec8c0',
  '#d8b6a6',
  '#bcc9d8',
  '#c2caa6',
  '#b9b3c9',
];

/** Stable colour for a title: the same seed always yields the same entry. */
export function colorFor(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return PALETTE[h % PALETTE.length];
}

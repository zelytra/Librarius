/**
 * The two viewport widths the layout changes at, in pixels.
 *
 * They are the JS half of `--bp-tablet` / `--bp-desktop` in
 * `shared/styles/tokens.css`, which is where the reasoning behind the values
 * lives — both are measured against the app's own cover column rather than
 * taken from a device catalogue. The numbers exist twice because `@media`
 * cannot read a custom property and `matchMedia` cannot read a CSS file;
 * `breakpoints.test.ts` fails the build if the two halves ever drift apart.
 *
 * Read them from here, never from a literal: a component that hardcodes 600
 * silently stops following the layout the day the breakpoint moves.
 */
export const BREAKPOINTS = {
  tablet: 600,
  desktop: 1120,
} as const;

export type Breakpoint = keyof typeof BREAKPOINTS;

/**
 * The media query matching a breakpoint and every width above it — the same
 * condition the responsive blocks of `tokens.css` use, for the cases CSS cannot
 * answer on its own: which navigation to *render*, rather than how to draw it.
 */
export function mediaAtLeast(breakpoint: Breakpoint): string {
  return `(min-width: ${BREAKPOINTS[breakpoint]}px)`;
}

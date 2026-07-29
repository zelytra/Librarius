import { useMemo, useSyncExternalStore } from 'react';

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

/**
 * Whether the viewport is currently at least this wide, kept in step as the window is
 * resized.
 *
 * This is the *render* decision CSS cannot make: hiding a component with `display: none`
 * still mounts it, still runs its hooks and still leaves it in the accessibility tree, so
 * "which navigation exists" is a question for JS. How that navigation is drawn stays in
 * CSS, through the tokens.
 *
 * `matchMedia` is guarded, the way `theme/themes.ts` guards it: jsdom does not implement
 * it, and every component test mounts the shell. Absent, the answer is `false` — the
 * phone layout, which is the app's own default.
 */
export function useViewportAtLeast(breakpoint: Breakpoint): boolean {
  // Subscribing to a store React re-subscribes to on every render would tear the listener
  // down and set it up again each time, so both halves are memoised with the query.
  const store = useMemo(() => {
    const list =
      typeof window !== 'undefined' && typeof window.matchMedia === 'function'
        ? window.matchMedia(mediaAtLeast(breakpoint))
        : null;
    return {
      subscribe: (onChange: () => void) => {
        list?.addEventListener('change', onChange);
        return () => list?.removeEventListener('change', onChange);
      },
      getSnapshot: () => list?.matches ?? false,
    };
  }, [breakpoint]);

  return useSyncExternalStore(store.subscribe, store.getSnapshot);
}

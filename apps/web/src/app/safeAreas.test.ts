import { describe, expect, test } from 'vitest';
// The stylesheets as written, not as applied: jsdom lays nothing out, and a browser without
// insets is exactly the case the `0px` fallbacks cover. `vitest.config.ts` lets these two
// through its `css.include`, or a stubbed file would come back empty.
import shell from './AppShell.module.css?raw';
import bottomNav from './BottomNav.module.css?raw';

/**
 * #529: from API 35 on, an app that targets it draws edge to edge — the WebView fills the
 * window, status bar and gesture bar included. `index.html` already asks for it
 * (`viewport-fit=cover`) and Capacitor 8 fills `env(safe-area-inset-*)` in; what was missing
 * is the shell reading them, which is why the header slid under the clock and the bottom bar
 * under the gesture bar.
 *
 * Asserted on the stylesheet rather than on a rendered component: jsdom computes no insets,
 * and a browser without them is exactly the case the `0px` fallbacks cover.
 */
describe('safe areas', () => {
  test('the frame reserves the status bar at the top', () => {
    expect(shell).toMatch(/\.frame\s*\{[^}]*padding-top:\s*env\(safe-area-inset-top,\s*0px\)/);
  });

  // A phone held sideways puts the notch on one of the two sides.
  test('the frame reserves the notch on both sides', () => {
    expect(shell).toMatch(/\.frame\s*\{[^}]*padding-left:\s*env\(safe-area-inset-left,\s*0px\)/);
    expect(shell).toMatch(/\.frame\s*\{[^}]*padding-right:\s*env\(safe-area-inset-right,\s*0px\)/);
  });

  test('the bottom bar reserves the gesture bar underneath itself', () => {
    expect(bottomNav).toMatch(/\.nav\s*\{[^}]*padding:[^;]*env\(safe-area-inset-bottom,\s*0px\)/);
  });

  // The bar keeps its own height: the inset is added to it, never taken out of it, or the
  // touch targets would shrink below 44px on a phone with a gesture bar. Under the global
  // `box-sizing: border-box` the padding counts inside `min-height`, so the inset has to be
  // in the `min-height` too — without it the icons hugged the bar's top edge (#543).
  test('the bottom bar keeps its height when an inset is added', () => {
    expect(bottomNav).toMatch(
      /\.nav\s*\{[^}]*min-height:\s*calc\(var\(--bottom-nav-height\)\s*\+\s*env\(safe-area-inset-bottom,\s*0px\)\)/,
    );
    expect(bottomNav).not.toMatch(/\.nav\s*\{[^}]*\n\s*height:\s*var\(--bottom-nav-height\)/);
  });

  // Icon and label are centred in the tab's height, whatever the label's line count (#543).
  test('a tab centres its content vertically', () => {
    expect(bottomNav).toMatch(/\.tab\s*\{[^}]*display:\s*flex;[^}]*align-items:\s*center;[^}]*justify-content:\s*center/);
  });

  // The sheet rises from the bar, so it has to clear the same inset the bar now reserves.
  test('the « Plus » sheet rises above the bar and the gesture bar', () => {
    expect(bottomNav).toMatch(
      /\.sheet\s*\{[^}]*bottom:\s*calc\(var\(--bottom-nav-height\)\s*\+\s*8px\s*\+\s*env\(safe-area-inset-bottom,\s*0px\)\)/,
    );
  });

  // Every inset carries its own `0px` fallback: a browser that knows nothing of them must
  // lay the app out exactly as it did before.
  test('every inset falls back to zero', () => {
    for (const css of [shell, bottomNav]) {
      const insets = css.match(/env\(safe-area-inset-[a-z]+[^)]*\)/g) ?? [];
      expect(insets.length).toBeGreaterThan(0);
      for (const inset of insets) expect(inset).toMatch(/,\s*0px\)$/);
    }
  });
});

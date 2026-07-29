import { describe, expect, test } from 'vitest';
import tokens from '../styles/tokens.css?raw';
import { BREAKPOINTS, mediaAtLeast } from './breakpoints';

/** Every `@media (min-width: …px)` the stylesheet declares. */
function minWidths(css: string): number[] {
  return [...css.matchAll(/@media[^{]*\(\s*min-width:\s*(\d+)px\s*\)/g)].map((m) => Number(m[1]));
}

function tokenPx(css: string, name: string): number {
  const match = new RegExp(`--${name}:\\s*(\\d+)px`).exec(css);
  expect(match, `--${name} is not declared in tokens.css`).not.toBeNull();
  return Number(match?.[1]);
}

describe('breakpoints', () => {
  test('build the query the responsive blocks of tokens.css are written with', () => {
    expect(mediaAtLeast('tablet')).toBe('(min-width: 600px)');
    expect(mediaAtLeast('desktop')).toBe('(min-width: 1120px)');
  });

  /**
   * The values live twice — `@media` cannot read a custom property, `matchMedia` cannot
   * read a CSS file. This is what makes the duplication safe: move one and the suite goes
   * red, rather than a component silently rendering for a viewport the stylesheet has
   * stopped agreeing with.
   */
  test('agree with the tokens they mirror', () => {
    expect(tokenPx(tokens, 'bp-tablet')).toBe(BREAKPOINTS.tablet);
    expect(tokenPx(tokens, 'bp-desktop')).toBe(BREAKPOINTS.desktop);
  });

  /**
   * And the reason a component never needs a breakpoint of its own: the responsive values
   * are resolved here, once, and read as plain custom properties everywhere else. A third
   * step in this file is a design decision, not a detail — it should not slip in.
   */
  test('are the only two widths the stylesheet switches on', () => {
    expect(minWidths(tokens)).toEqual([BREAKPOINTS.tablet, BREAKPOINTS.desktop]);
  });
});

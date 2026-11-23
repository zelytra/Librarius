import { describe, expect, test } from 'vitest';
// The stylesheet as written: jsdom resolves no custom property (see `app/safeAreas.test.ts`).
import css from './HomeShowcase.module.css?raw';

/**
 * #612: the "Ta bibliothèque" banner stayed green on every palette. It was painted with
 * `--tint-sage`, a fixed category tint, rather than a token each `data-theme` redefines.
 */
describe('the Home banner', () => {
  const banner = /\.banner\s*\{([^}]*)\}/.exec(css)?.[1] ?? '';

  test('is painted with the palette’s own soft accent', () => {
    expect(banner).toMatch(/background:[^;]*var\(--accent-soft\)/);
  });

  test('uses no fixed category tint', () => {
    expect(banner).not.toMatch(/--tint-/);
  });
});

import { describe, expect, test } from 'vitest';
// The stylesheet as written: jsdom computes no cascade (see `app/safeAreas.test.ts`).
import css from './ReadingSheetPage.module.css?raw';

/**
 * #596: in the « Nuit » palette, the sheet's title and section headings were white on its
 * white page — `global.css` paints every h1–h3 in the app's ink. The sheet keeps its own ink
 * for every heading, whatever the app's theme.
 */
describe('the reading sheet ink', () => {
  test('gives its headings the sheet ink, not the app theme ink', () => {
    expect(css).toMatch(/\.sheet :is\(h1, h2, h3\)\s*\{[^}]*color:\s*inherit/);
  });
});

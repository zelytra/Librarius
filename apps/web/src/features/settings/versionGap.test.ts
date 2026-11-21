import { describe, expect, test } from 'vitest';
// The stylesheet as written: jsdom lays nothing out (see `app/safeAreas.test.ts`).
import css from './SettingsPage.module.css?raw';

/** #604: the version line touched the last category card. */
describe('the settings version line', () => {
  test('keeps the gap the page puts between its blocks', () => {
    expect(css).toMatch(/\.version\s*\{[^}]*margin-top:\s*\d+px/);
  });
});

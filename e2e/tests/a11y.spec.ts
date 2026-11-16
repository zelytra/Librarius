import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '../support/fixtures';

/**
 * #78 — WCAG 2.1 AA, checked by axe-core in a real browser.
 *
 * jsdom cannot compute colours, so contrast is only measurable here. Every main screen is
 * audited at phone width in each of the four themes; a serious or critical violation fails
 * the journey with the rule and the offending nodes. Screens that need data of their own
 * (a title, an event) are left to the manual audit described in docs/wiki/Contributing.md.
 */
const THEMES = ['creme', 'sauge', 'rose', 'nuit'] as const;
const SCREENS = [
  '/',
  '/collection',
  '/to-read',
  '/discover',
  '/wishlist',
  '/stats',
  '/achievements',
  '/categories',
  '/people',
  '/notifications',
  '/settings',
];

test.use({ viewport: { width: 390, height: 844 } });
// Eleven screens audited in a row: longer than one journey.
test.setTimeout(120_000);

for (const theme of THEMES) {
  test(`meets WCAG 2.1 AA on the main screens in the ${theme} theme`, async ({ page }) => {
    await page.goto('/');
    await page.evaluate((name) => localStorage.setItem('librarius.theme', name), theme);
    const failures: string[] = [];
    for (const path of SCREENS) {
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      const { violations } = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();
      for (const violation of violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')) {
        failures.push(`${path} — ${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(', ')}`);
      }
    }
    expect(failures, failures.join('\n')).toEqual([]);
  });
}

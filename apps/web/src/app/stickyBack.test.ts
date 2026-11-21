import { describe, expect, test } from 'vitest';
import css from '../shared/styles/global.css?raw';

// Every screen with a back button, read as written (#592).
const screens = import.meta.glob('../features/**/*Page.tsx', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;

/**
 * #592: on a phone the back button scrolled away with the page, and the reader had to scroll
 * all the way up to leave. The row holding it sticks to the top of the screen.
 */
describe('the back button', () => {
  test('sticks to the top of the screen', () => {
    expect(css).toMatch(/\.sticky-header\s*\{[^}]*position:\s*sticky;[^}]*top:\s*0;[^}]*background:\s*var\(--bg\)/);
    expect(css).toMatch(/\.sticky-back\s*\{[^}]*position:\s*sticky;[^}]*top:\s*\d+px;[^}]*pointer-events:\s*none/);
    expect(css).toMatch(/\.sticky-back > \*\s*\{[^}]*pointer-events:\s*auto/);
  });

  // The icon button in a screen's header, not the « Retour » of an error state.
  const BACK = "aria-label={t('common.back')}";
  const withBack = Object.entries(screens).filter(([, source]) => source.includes(BACK));

  test('is found on the screens that have one', () => {
    expect(withBack.length).toBeGreaterThanOrEqual(11);
  });

  test.each(withBack.map(([path, source]) => [path.replace('../features/', ''), source]))(
    'rides a sticky row on %s',
    (_, source) => {
      const at = source.indexOf(BACK);
      const row = source.lastIndexOf('<div', at);
      expect(source.slice(row, at)).toMatch(/sticky-(header|back)/);
    },
  );
});

import { describe, expect, test } from 'vitest';
import tokens from './tokens.css?raw';
import boot from '../../../public/theme-boot.js?raw';
import { THEMES } from '../theme/themes';

/** Every block whose selector list names the palette, merged in order: the later one wins. */
function declarations(id: string): Record<string, string> {
  const values: Record<string, string> = {};
  for (const [, selectors, body] of tokens.matchAll(/([^{}]+)\{([^}]*)\}/g)) {
    if (!selectors.includes(`[data-theme='${id}']`)) continue;
    for (const [, name, value] of body.matchAll(/--([\w-]+):\s*([^;]+);/g)) values[name] = value.trim();
  }
  return values;
}

const DARK = new Set(['nuit', 'ardoise']);

/** A palette's value; a light palette inherits what it does not redeclare from creme. */
function token(id: string, name: string): string {
  const value = declarations(id)[name] ?? (DARK.has(id) ? undefined : declarations('creme')[name]);
  expect(value, `--${name} is not declared for ${id}`).toBeDefined();
  const reference = /^var\(--([\w-]+)\)$/.exec(value!);
  return reference ? token(id, reference[1]) : value!;
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (light + 0.05) / (dark + 0.05);
}

const palettes = THEMES.map((t) => t.id).filter((id) => id !== 'systeme');

/**
 * #572: more colours to choose from. Every palette offered in Settings is declared in
 * tokens.css, reads at WCAG AA, and is painted before the first frame by the theme boot.
 */
describe('the palettes', () => {
  test('offer at least eight, two of them dark', () => {
    expect(palettes.length).toBeGreaterThanOrEqual(8);
    expect(palettes.filter((id) => DARK.has(id))).toHaveLength(2);
  });

  describe.each(palettes)('%s', (id) => {
    const surfaces = ['bg', 'surface', 'surface-2'];

    test.each(['ink', 'ink-soft', 'muted', 'faint'])('writes %s at 4.5:1 on every surface', (text) => {
      for (const surface of surfaces) {
        expect(contrast(token(id, text), token(id, surface)), `${text} on ${surface}`).toBeGreaterThanOrEqual(4.5);
      }
    });

    test('draws its accent at 3:1 on its surfaces, and its filled controls at 4.5:1', () => {
      for (const surface of surfaces) expect(contrast(token(id, 'accent-deep'), token(id, surface))).toBeGreaterThanOrEqual(3);
      expect(contrast(token(id, 'accent-fill'), token(id, 'on-accent-fill'))).toBeGreaterThanOrEqual(4.5);
      expect(contrast(token(id, 'rose'), token(id, 'on-accent-fill'))).toBeGreaterThanOrEqual(4.5);
    });

    test('is painted before the first frame, in its own background', () => {
      const painted = new RegExp(`${id}: '(#[0-9a-f]{6})'`, 'i').exec(boot)?.[1];
      expect(painted?.toLowerCase()).toBe(token(id, 'bg').toLowerCase());
    });
  });
});

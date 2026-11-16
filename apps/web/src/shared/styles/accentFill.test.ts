import { describe, expect, test } from 'vitest';
import tokens from './tokens.css?raw';

/** The declarations of the palette block whose selector list names `[data-theme='<id>']`. */
function palette(id: string): Record<string, string> {
  const block = new RegExp(`\\[data-theme='${id}'\\][^{]*\\{([^}]*)\\}`).exec(tokens);
  expect(block, `no palette block for ${id}`).not.toBeNull();
  const values: Record<string, string> = {};
  for (const [, name, value] of (block?.[1] ?? '').matchAll(/--([\w-]+):\s*([^;]+);/g)) {
    values[name] = value.trim();
  }
  return values;
}

/** A palette's own value, or creme's — the light palettes inherit what they do not redeclare. */
function token(id: string, name: string): string {
  const value = palette(id)[name] ?? palette('creme')[name];
  expect(value, `--${name} is not declared for ${id}`).toBeDefined();
  const reference = /^var\(--([\w-]+)\)$/.exec(value);
  return reference ? token(id, reference[1]) : value;
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

/**
 * A filled control — primary button, add, read, the selected chip — is painted with
 * `--accent-fill` and written in `--on-accent-fill` (#519). On a light page it is a deep
 * fill with white text, the way a light theme reads; the pastel fill with dark ink is the
 * dark theme's, and is what the light themes used to show.
 */
describe('accent fills', () => {
  test.each(['creme', 'sauge', 'rose'])('carry white text in the light palette %s', (id) => {
    expect(token(id, 'on-accent-fill').toLowerCase()).toBe('#ffffff');
  });

  test('keep the dark ink on the pastel fill in the dark palette', () => {
    expect(token('nuit', 'accent-fill')).toBe(token('nuit', 'accent'));
    expect(token('nuit', 'on-accent-fill')).toBe('#1b2416');
  });

  test.each(['creme', 'sauge', 'rose', 'nuit'])('meet AA contrast (4.5:1) in %s', (id) => {
    expect(contrast(token(id, 'accent-fill'), token(id, 'on-accent-fill'))).toBeGreaterThanOrEqual(4.5);
  });

  test.each(['creme', 'sauge', 'rose', 'nuit'])('carry the danger fill at AA contrast in %s', (id) => {
    expect(contrast(token(id, 'rose'), token(id, 'on-accent-fill'))).toBeGreaterThanOrEqual(4.5);
  });
});

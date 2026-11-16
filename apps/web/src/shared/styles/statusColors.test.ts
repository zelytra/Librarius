import { describe, expect, test } from 'vitest';
import tokens from './tokens.css?raw';

/**
 * The red, amber and green the screens say « error », « to look at » and « done » with (#522).
 * They have to stay vivid — the pastel tints read as grey — and still meet WCAG 2.1 AA in every
 * palette: the ink at 4.5:1 on its soft fill and on the surfaces, the fill at 3:1 as a graphic.
 */

const STATUSES = { success: [90, 160], warning: [30, 50], danger: [-15, 12] } as const;
const THEMES = ['creme', 'sauge', 'rose', 'nuit'] as const;

function block(selector: string): string {
  const start = tokens.indexOf(`${selector} {`);
  expect(start, `${selector} is not declared in tokens.css`).toBeGreaterThanOrEqual(0);
  return tokens.slice(start, tokens.indexOf('\n}', start));
}

/** A theme's value of a token: its own block first, then the base `creme` one it inherits. */
function token(theme: (typeof THEMES)[number], name: string): string {
  const own = theme === 'creme' ? '' : block(`[data-theme='${theme}']`);
  const base = block(":root,\n[data-theme='creme']");
  const pick = (css: string) => new RegExp(`--${name}:\\s*(#[0-9a-f]{6})\\b`, 'i').exec(css)?.[1];
  const value = pick(own) ?? (theme === 'nuit' ? undefined : pick(base));
  expect(value, `--${name} is not declared for ${theme}`).toBeDefined();
  return value!;
}

function rgb(hex: string): number[] {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
}

function luminance(hex: string): number {
  const [r, g, b] = rgb(hex).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [dark, light] = [luminance(a), luminance(b)].sort((x, y) => x - y);
  return (light + 0.05) / (dark + 0.05);
}

/** Hue in degrees and HSL saturation of a colour. */
function hsl(hex: string): { hue: number; saturation: number } {
  const [r, g, b] = rgb(hex);
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const d = max - min;
  const l = (max + min) / 2;
  const saturation = d === 0 ? 0 : d / (1 - Math.abs(2 * l - 1));
  const hue = d === 0 ? 0 : max === r ? 60 * (((g - b) / d) % 6) : max === g ? 60 * ((b - r) / d + 2) : 60 * ((r - g) / d + 4);
  return { hue: hue > 180 ? hue - 360 : hue, saturation };
}

describe.each(THEMES)('status colours in the %s palette (#522)', (theme) => {
  const surfaces = ['bg', 'surface', 'surface-2'].map((name) => token(theme, name));

  test.each(Object.keys(STATUSES) as (keyof typeof STATUSES)[])('%s is a vivid colour of its family', (status) => {
    const [from, to] = STATUSES[status];
    const { hue, saturation } = hsl(token(theme, status));
    expect(saturation).toBeGreaterThanOrEqual(0.45);
    expect(hue).toBeGreaterThanOrEqual(from);
    expect(hue).toBeLessThanOrEqual(to);
  });

  test.each(Object.keys(STATUSES))('%s reads at AA as text and as a graphic', (status) => {
    const fill = token(theme, status);
    const soft = token(theme, `${status}-soft`);
    const ink = token(theme, `${status}-ink`);
    expect(contrast(ink, soft)).toBeGreaterThanOrEqual(4.5);
    expect(contrast(token(theme, 'ink'), soft)).toBeGreaterThanOrEqual(4.5);
    expect(contrast(token(theme, 'ink-soft'), soft)).toBeGreaterThanOrEqual(4.5);
    for (const surface of surfaces) {
      expect(contrast(ink, surface)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(fill, surface)).toBeGreaterThanOrEqual(3);
    }
  });

  test('the error toast text reads on its fill', () => {
    expect(contrast(token(theme, 'on-danger'), token(theme, 'danger'))).toBeGreaterThanOrEqual(4.5);
  });
});

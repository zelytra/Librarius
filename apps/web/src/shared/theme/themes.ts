/**
 * Themes offered in Settings.
 *
 * Four of them are palettes, and match the `data-theme` blocks in tokens.css.
 * The fifth, `systeme`, is a preference rather than a palette: it follows
 * `prefers-color-scheme` and resolves to one of the four.
 */
export const THEMES = [
  {
    id: 'systeme',
    labelKey: 'settings.themes.systeme',
    // Half light, half dark: the swatch stands for "whatever the system says".
    swatch: 'linear-gradient(135deg, #f3ede3 0 50%, #211e1a 50% 100%)',
  },
  { id: 'creme', labelKey: 'settings.themes.creme', swatch: '#f3ede3' },
  { id: 'sauge', labelKey: 'settings.themes.sauge', swatch: '#dfe6da' },
  { id: 'rose', labelKey: 'settings.themes.rose', swatch: '#f1e2df' },
  { id: 'nuit', labelKey: 'settings.themes.nuit', swatch: '#211e1a' },
] as const;

export type ThemeId = (typeof THEMES)[number]['id'];

/** A theme that is an actual palette — `systeme` once resolved. */
export type PaletteId = Exclude<ThemeId, 'systeme'>;

/** Where the preference is kept. The boot script in index.html reads the same key. */
export const STORAGE_KEY = 'librarius.theme';

export const DEFAULT_THEME: ThemeId = 'systeme';

export function isThemeId(value: string | null): value is ThemeId {
  return value != null && THEMES.some((t) => t.id === value);
}

/**
 * The media query, guarded: jsdom does not implement `matchMedia`, and the
 * provider is mounted by every component test.
 */
export function darkModeQuery(): MediaQueryList | null {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null;
}

/** Palette a preference maps to: `systeme` asks the system, the rest are literal. */
export function resolveTheme(theme: ThemeId): PaletteId {
  if (theme !== 'systeme') return theme;
  return darkModeQuery()?.matches ? 'nuit' : 'creme';
}

/** Preference stored by a previous session, or the default. */
export function readStoredTheme(): ThemeId {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return isThemeId(stored) ? stored : DEFAULT_THEME;
  } catch {
    // Storage unavailable (private mode, blocked cookies): use the default.
    return DEFAULT_THEME;
  }
}

/**
 * Puts a resolved palette on `<html>`.
 *
 * The boot script in index.html has already done this once, before the first
 * paint; it also painted the root element, because the stylesheet is not
 * parsed yet at that point. Both are refreshed here from the palette actually
 * in force, so switching theme does not leave the old colour behind in the
 * overscroll area or in the browser chrome.
 */
export function applyTheme(palette: PaletteId): void {
  const root = document.documentElement;
  root.setAttribute('data-theme', palette);

  const bg = getComputedStyle(root).getPropertyValue('--bg').trim();
  if (!bg) return;
  root.style.backgroundColor = bg;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', bg);
}

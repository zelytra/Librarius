import { Capacitor, SystemBars, SystemBarsStyle } from '@capacitor/core';

/**
 * Themes offered in Settings.
 *
 * All but one are palettes, and match the `data-theme` blocks in tokens.css (#572 added
 * Océan, Lavande, Sable and the dark Ardoise). `systeme` is a preference rather than a
 * palette: it follows `prefers-color-scheme` and resolves to Crème or Nuit.
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
  // #572: more colours.
  { id: 'ocean', labelKey: 'settings.themes.ocean', swatch: '#dde8ee' },
  { id: 'lavande', labelKey: 'settings.themes.lavande', swatch: '#e7e2ee' },
  { id: 'sable', labelKey: 'settings.themes.sable', swatch: '#efe6d6' },
  { id: 'nuit', labelKey: 'settings.themes.nuit', swatch: '#211e1a' },
  { id: 'ardoise', labelKey: 'settings.themes.ardoise', swatch: '#1b2129' },
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
/** The palettes drawn light on dark: their system bars take light icons (#533). */
const DARK_PALETTES: ReadonlySet<PaletteId> = new Set(['nuit', 'ardoise']);

/**
 * Colours the native container's status and gesture bars from the palette (#533).
 *
 * The container draws edge to edge, so both bars sit over the app's own background, and
 * Capacitor otherwise picks their icons from the device's night mode — white icons on Crème
 * on a phone set to dark. `style` names the bar's background: `LIGHT` gives dark icons.
 * No `bar`, so the status bar and the gesture bar follow the same rule. A browser has
 * neither, so nothing is asked of it.
 */
export function applySystemBars(palette: PaletteId): void {
  if (!Capacitor.isNativePlatform()) return;
  const style = DARK_PALETTES.has(palette) ? SystemBarsStyle.Dark : SystemBarsStyle.Light;
  // A failure leaves the system's own choice in place, which is what shipped before.
  SystemBars.setStyle({ style }).catch(() => {});
}

export function applyTheme(palette: PaletteId): void {
  const root = document.documentElement;
  root.setAttribute('data-theme', palette);

  const bg = getComputedStyle(root).getPropertyValue('--bg').trim();
  if (!bg) return;
  root.style.backgroundColor = bg;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', bg);
}

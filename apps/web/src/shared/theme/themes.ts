/** Thèmes disponibles (correspondent aux jetons CSS de tokens.css). */
export const THEMES = [
  { id: 'creme', labelKey: 'settings.themes.creme', swatch: '#f3ede3' },
  { id: 'sauge', labelKey: 'settings.themes.sauge', swatch: '#dfe6da' },
  { id: 'rose', labelKey: 'settings.themes.rose', swatch: '#f1e2df' },
  { id: 'nuit', labelKey: 'settings.themes.nuit', swatch: '#3a352f' },
] as const;

export type ThemeId = (typeof THEMES)[number]['id'];

export const DEFAULT_THEME: ThemeId = 'creme';

export function isThemeId(value: string | null): value is ThemeId {
  return value != null && THEMES.some((t) => t.id === value);
}

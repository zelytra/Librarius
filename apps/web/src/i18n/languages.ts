import i18next from 'i18next';

/**
 * Languages the interface is available in.
 *
 * Each one is labelled with its **endonym** — its own name in its own language — and
 * never translated. Someone who lands on the wrong language has to be able to find
 * their way out of it, and "Français" is the only label a French reader recognises on
 * an English screen.
 */
export const LANGUAGES = [
  { id: 'fr', label: 'Français' },
  { id: 'en', label: 'English' },
] as const;

export type LanguageId = (typeof LANGUAGES)[number]['id'];

/** Where the choice is kept. Same convention as the theme (`librarius.theme`). */
export const STORAGE_KEY = 'librarius.language';

/**
 * The locale the copy is authored in, and the one i18next falls back to for a key an
 * other locale would be missing.
 */
export const DEFAULT_LANGUAGE: LanguageId = 'fr';

export function isLanguageId(value: string | null | undefined): value is LanguageId {
  return value != null && LANGUAGES.some((l) => l.id === value);
}

/**
 * The language in force, narrowed to one the app actually ships — and, since the ids are
 * BCP-47 tags, the locale to hand `Intl` as well.
 *
 * Formatting is part of translating: an English screen printing « mercredi 29 juillet », or
 * 1 234 where a reader expects 1,234, is untranslated in a subtler way than a stray French
 * sentence. Every `Intl` call in the app reads its locale from here.
 *
 * It goes to the i18next singleton rather than to `./index`, so a pure helper can format a
 * date without dragging the initialisation side effect into its own unit test. Before init
 * — which is only ever the case in such a test — it is the default language.
 */
export function activeLanguage(): LanguageId {
  const active = i18next.resolvedLanguage ?? i18next.language;
  return isLanguageId(active) ? active : DEFAULT_LANGUAGE;
}

/** Choice made in Settings by a previous session, if there is one. */
export function readStoredLanguage(): LanguageId | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return isLanguageId(stored) ? stored : null;
  } catch {
    // Storage unavailable (private mode, blocked cookies): nothing was ever stored.
    return null;
  }
}

export function storeLanguage(language: LanguageId): void {
  try {
    localStorage.setItem(STORAGE_KEY, language);
  } catch {
    /* storage unavailable: the choice holds for this session only */
  }
}

/**
 * First supported language the browser asks for.
 *
 * `navigator.languages` is ordered by preference and holds regional tags (`en-GB`,
 * `fr-CA`), so only the primary subtag is compared: the app has one English and one
 * French, not one per region.
 */
export function detectBrowserLanguage(
  advertised: readonly string[] = typeof navigator === 'undefined' ? [] : navigator.languages,
): LanguageId | null {
  for (const tag of advertised) {
    const primary = tag.split('-')[0]?.toLowerCase();
    if (isLanguageId(primary)) return primary;
  }
  return null;
}

/**
 * The language to start in, strongest source first:
 *
 * 1. the choice made in Settings, kept in `localStorage`;
 * 2. what the browser advertises;
 * 3. French.
 *
 * The user profile of [#75](https://github.com/zelytra/Librarius/issues/75) will carry a
 * language too, and it arrives too late to be read here — the app has to paint before
 * the API has answered. It slots in without touching this function: whoever loads the
 * profile calls `storeLanguage` + `changeLanguage` when it names one, which is exactly
 * what the Settings switcher does, and the stored value is then the profile's on the
 * next boot. The browser preference keeps its place as the first-visit default, which is
 * all it was ever for.
 */
export function resolveInitialLanguage(): LanguageId {
  return readStoredLanguage() ?? detectBrowserLanguage() ?? DEFAULT_LANGUAGE;
}

import i18n, { type BackendModule } from 'i18next';
import { initReactI18next } from 'react-i18next';
import fr from './locales/fr.json';
import {
  DEFAULT_LANGUAGE,
  LANGUAGES,
  resolveInitialLanguage,
  storeLanguage,
  type LanguageId,
} from './languages';

/**
 * The locales other than French, fetched when they are first needed.
 *
 * French is bundled: it is the default and the fallback, so the first screen of almost every
 * visit paints from it. English used to be bundled too, and each locale weighs ~17 kB gz —
 * the eager payload had reached #79's 200 kB ceiling with both in it. Loaded on demand, a
 * reader who never switches never downloads it, and the service worker precaches its chunk
 * for the one who does. A switch goes through i18next's own `changeLanguage`, which waits for
 * this loader, so no caller has to know.
 */
const LAZY_LOCALES: Partial<Record<LanguageId, () => Promise<{ default: Record<string, unknown> }>>> = {
  en: () => import('./locales/en.json'),
};

const lazyLocales: BackendModule = {
  type: 'backend',
  init: () => {},
  read(language, _namespace, callback) {
    const load = LAZY_LOCALES[language as LanguageId];
    if (!load) {
      callback(null, {});
      return;
    }
    load().then(
      (module) => callback(null, module.default),
      (error: Error) => callback(error, null),
    );
  },
};

// French and English are both complete: `locales.test.ts` fails the build on the first key
// that exists in one and not in the other. `fallbackLng` is therefore about the locale the
// copy is authored in, not about a hole anyone is expected to hit.
/**
 * Settles once the initial language is ready to render: at once for French, after its chunk
 * for English. `main.tsx` waits for it, so an English reader never sees a French first paint.
 */
export const i18nReady: Promise<unknown> = i18n
  .use(lazyLocales)
  .use(initReactI18next)
  .init({
    resources: { fr: { translation: fr } },
    // The bundled French is complete: only a language missing from `resources` is fetched.
    partialBundledLanguages: true,
    lng: resolveInitialLanguage(),
    fallbackLng: DEFAULT_LANGUAGE,
    supportedLngs: LANGUAGES.map((l) => l.id),
    interpolation: { escapeValue: false },
  });

/**
 * The two strings that live outside React: `<html lang>` and the tab title.
 *
 * A wrong `lang` is not cosmetic — it is what a screen reader picks its voice from and what
 * the browser hyphenates on — and `index.html` ships `lang="fr"` with a French `<title>`,
 * which is right for the default and for anyone arriving without JavaScript. This module is
 * imported by `main.tsx` before the first render, so both are already correct when the page
 * paints, and they follow every later switch.
 *
 * The web manifest is the one thing that cannot follow: it is a static file, so an installed
 * shortcut keeps its French name whatever the interface is set to.
 */
function applyDocumentLocale(language: string): void {
  if (typeof document === 'undefined') return;
  document.documentElement.lang = language;
  document.title = i18n.t('app.name');
}

applyDocumentLocale(i18n.language);
i18n.on('languageChanged', applyDocumentLocale);

/**
 * Switches the interface and remembers the choice for the next visit.
 *
 * The single way in: the Settings switcher goes through it today, and the profile
 * preference of [#75](https://github.com/zelytra/Librarius/issues/75) will go through the
 * same call once loaded, rather than reaching for `i18n.changeLanguage` and leaving the
 * stored value behind.
 *
 * Resolves once the switch has actually been applied. Fire-and-forget callers stay
 * unaffected; a caller that needs the interface to be on the new locale before it
 * continues — a test resetting the shared singleton between cases, for one — can await it.
 */
export async function changeLanguage(language: LanguageId): Promise<void> {
  storeLanguage(language);
  await i18n.changeLanguage(language);
}

export default i18n;

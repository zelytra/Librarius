import { afterEach, describe, expect, test } from 'vitest';
import i18n from './index';
import { LANGUAGES } from './languages';
import { monthLabels } from '../features/stats/timeline';
import { formatPace } from '../shared/goal';
import en from './locales/en.json';
import fr from './locales/fr.json';

/**
 * The locales, checked against each other rather than spot-checked.
 *
 * A missing key does not crash: i18next quietly falls back to French, so an English screen
 * grows a French line and nobody notices until a user reports it. The parity check below is
 * what makes that a build failure — it is the CI gate issue #77 asks for, and it runs in
 * the `web` workflow with the rest of the suite.
 */

type Node = { [key: string]: string | Node };

/** `{ a: { b: "x" } }` → `{ "a.b" => "x" }`. */
function flatten(node: Node, prefix = ''): Map<string, string> {
  const flat = new Map<string, string>();
  for (const [key, value] of Object.entries(node)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === 'string') flat.set(path, value);
    else for (const [sub, leaf] of flatten(value, path)) flat.set(sub, leaf);
  }
  return flat;
}

/** The `{{name}}` slots a string interpolates, sorted so two strings can be compared. */
function placeholders(value: string): string[] {
  return [...value.matchAll(/\{\{\s*([\w.]+)/g)].map((match) => match[1]).sort();
}

const CLDR_CATEGORIES = /_(zero|one|two|few|many|other)$/;

const locales = { en: flatten(en as Node), fr: flatten(fr as Node) };

describe('locale parity', () => {
  test('the locales ship exactly the same keys', () => {
    const missingInEnglish = [...locales.fr.keys()].filter((key) => !locales.en.has(key));
    const missingInFrench = [...locales.en.keys()].filter((key) => !locales.fr.has(key));

    expect({ missingInEnglish, missingInFrench }).toEqual({
      missingInEnglish: [],
      missingInFrench: [],
    });
  });

  test('every key is translated in both, and none is left empty', () => {
    for (const [language, entries] of Object.entries(locales)) {
      const empty = [...entries].filter(([, value]) => value.trim() === '').map(([key]) => key);
      expect({ language, empty }).toEqual({ language, empty: [] });
    }
  });

  /**
   * A translator dropping a `{{year}}` is the interpolation bug that survives review: the
   * sentence still reads, it is simply missing the number it was written around.
   */
  test('a translated string interpolates the same values as the French one', () => {
    const diverging = [...locales.fr]
      .filter(([key, french]) => {
        const english = locales.en.get(key);
        return english != null && placeholders(french).join() !== placeholders(english).join();
      })
      .map(([key]) => key);

    expect(diverging).toEqual([]);
  });

  test('every language offered in Settings has a resource bundle', () => {
    for (const { id } of LANGUAGES) {
      expect(i18n.getResourceBundle(id, 'translation')).toBeTruthy();
    }
  });
});

/**
 * Keys the code asks for, checked against the keys the locales carry.
 *
 * The parity check above compares the two files with each other, so it is blind to a key
 * missing from **both** — and that is not a hypothetical: `home.upcoming` disappeared when
 * #57 replaced the old catalog-trends block, while `DashboardEditor`'s label map went on
 * pointing at it, and the customize panel rendered the literal string `home.upcoming` as a
 * section name in French and in English alike. Nothing failed. i18next returns the key when
 * it cannot resolve one, so a missing translation is not an error at runtime, it is a screen
 * with a dotted identifier printed on it.
 *
 * The scan deliberately does **not** look for `t('…')`. That was the first shape tried, and
 * it missed the very bug above: the key was a value in `DashboardEditor`'s label map, read
 * later through `t(SECTION_LABEL_KEY[code])`, and no pass over call sites can see it. What
 * it looks for instead is any single-quoted literal whose first segment is one of the
 * locale's own top-level namespaces — `home.…`, `collection.…` — wherever it is written.
 *
 * Keys assembled at run time (`` t(`wishlist.priority.${p}`) ``) still cannot be resolved
 * statically. That is a real limit, and it is why the enum-backed ones are exercised by
 * their own screen's tests instead.
 */
describe('keys the code uses', () => {
  /**
   * Every hand-written source file, as text. Read through Vite rather than `node:fs`:
   * `tsconfig.app.json` covers `src` and deliberately leaves the node types out, so that
   * nobody can reach for `process.env` in a component and have it typecheck. `?raw` is the
   * same seam `breakpoints.test.ts` already uses to read a stylesheet.
   */
  const sources: Record<string, string> = import.meta.glob(
    ['../**/*.ts', '../**/*.tsx', '!../**/*.test.ts', '!../**/*.test.tsx', '!../api/generated/**'],
    { query: '?raw', import: 'default', eager: true },
  );

  /** The locale's own top-level names — `home`, `collection`, `common`… */
  const namespaces = [...new Set([...locales.fr.keys()].map((key) => key.split('.')[0]))];
  const keyLike = new RegExp(`'((?:${namespaces.join('|')})(?:\\.\\w+)+)'`, 'g');

  /** Every literal that looks like a key of ours, wherever it is written. */
  function literalKeys(source: string): string[] {
    return [...source.matchAll(keyLike)].map((match) => match[1]);
  }

  /**
   * A plural key is stored suffixed, so `t('series.volumesOwned', { count })` resolves
   * through `_one` / `_other` and never through the bare path.
   */
  function resolves(key: string, entries: Map<string, string>): boolean {
    return entries.has(key) || entries.has(`${key}_one`) || entries.has(`${key}_other`);
  }

  test('every key written as a literal exists in the locales', () => {
    const unresolved = Object.entries(sources)
      .flatMap(([file, source]) => literalKeys(source).map((key) => ({ file, key })))
      .filter(({ key }) => !resolves(key, locales.fr))
      // Named with the file, because the point of failing is to say where to look.
      .map(({ file, key }) => `${key} (${file.replace(/^\.\.\//, '')})`)
      .filter((entry, index, all) => all.indexOf(entry) === index);

    expect(unresolved).toEqual([]);
  });
});

/**
 * Plurals.
 *
 * i18next v26 delegates to `Intl.PluralRules`, and the two languages disagree on zero:
 * French counts it as singular (« 0 série »), English does not ("0 series"). The suffixed
 * keys are therefore not a French detail to be copied across — each locale gets the forms
 * its own rules ask for, and the tests below assert the rendered string rather than the
 * shape of the file.
 *
 * Neither locale carries `_many`. French reserves that category for 10^6 and above, a count
 * no shelf of books reaches; if one ever does, the parity test above forces the form into
 * both files at once.
 */
describe('plurals', () => {
  const t = { en: i18n.getFixedT('en'), fr: i18n.getFixedT('fr') };

  test('every plural key exists in both the one and the other form', () => {
    const incomplete = [...locales.fr.keys()]
      .filter((key) => CLDR_CATEGORIES.test(key))
      .map((key) => key.replace(CLDR_CATEGORIES, ''))
      .filter((base, index, bases) => bases.indexOf(base) === index)
      .filter((base) =>
        Object.values(locales).some(
          (entries) => !entries.has(`${base}_one`) || !entries.has(`${base}_other`),
        ),
      );

    expect(incomplete).toEqual([]);
  });

  test('zero is singular in French and plural in English', () => {
    expect(t.fr('collection.seriesTotal', { count: 0 })).toBe('0 série');
    expect(t.en('collection.seriesTotal', { count: 0 })).toBe('0 series');

    expect(t.fr('goal.units.BOOKS', { count: 0 })).toBe('livre');
    expect(t.en('goal.units.BOOKS', { count: 0 })).toBe('books');
  });

  test('one is singular in both', () => {
    expect(t.fr('series.volumesOwned', { count: 1 })).toBe('1 tome');
    expect(t.en('series.volumesOwned', { count: 1 })).toBe('1 volume');

    expect(t.fr('goal.units.PAGES', { count: 1 })).toBe('page');
    expect(t.en('goal.units.PAGES', { count: 1 })).toBe('page');
  });

  test('more than one is plural in both', () => {
    expect(t.fr('series.volumesOwned', { count: 3 })).toBe('3 tomes');
    expect(t.en('series.volumesOwned', { count: 3 })).toBe('3 volumes');

    expect(t.fr('goal.units.VOLUMES', { count: 42 })).toBe('tomes');
    expect(t.en('goal.units.VOLUMES', { count: 42 })).toBe('volumes');
  });

  /**
   * Two English words that do not take an -s. Writing them by hand in both forms is the
   * point: "1 series" and "2 series", "1 read" and "2 read".
   */
  test('an English plural that is spelled like its singular still renders', () => {
    expect(t.en('collection.seriesTotal', { count: 1 })).toBe('1 series');
    expect(t.en('collection.seriesTotal', { count: 7 })).toBe('7 series');

    expect(t.en('series.readCount', { count: 1 })).toBe('1 read');
    expect(t.en('series.readCount', { count: 7 })).toBe('7 read');
    expect(t.fr('series.readCount', { count: 7 })).toBe('7 lus');
  });
});

/**
 * What `Intl` produces is translated copy too. Every formatter in the app reads its locale
 * from `activeLanguage()`, so switching the interface has to move the month names and the
 * thousands separator with it — a screen reading "July" above « 1 234 pages » is half
 * translated, and nothing in the locale files would ever catch it.
 */
describe('formatting', () => {
  afterEach(() => i18n.changeLanguage('fr'));

  test('month names come from the language in force', async () => {
    expect(monthLabels('long')[6]).toBe('juillet');

    await i18n.changeLanguage('en');
    expect(monthLabels('long')[6]).toBe('July');
  });

  test('thousands are grouped the way the language groups them', async () => {
    await i18n.changeLanguage('en');
    expect(formatPace(1234)).toBe('1,234');

    // French uses a narrow no-break space, whose exact code point moves between ICU
    // versions: what the test can assert is that it is not the English grouping.
    await i18n.changeLanguage('fr');
    expect(formatPace(1234)).not.toBe('1,234');
    expect(formatPace(1234)).toMatch(/^1\s?234$/u);
  });
});

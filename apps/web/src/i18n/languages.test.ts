import { afterAll, beforeEach, describe, expect, test } from 'vitest';
import {
  DEFAULT_LANGUAGE,
  STORAGE_KEY,
  detectBrowserLanguage,
  readStoredLanguage,
  resolveInitialLanguage,
  storeLanguage,
} from './languages';

/** jsdom advertises `en-US`; each test states the browser it is describing. */
function browserSpeaks(...tags: string[]): void {
  Object.defineProperty(navigator, 'languages', { value: tags, configurable: true });
}

beforeEach(() => localStorage.removeItem(STORAGE_KEY));

// `src/test/setup.ts` pins the suite to French: put it back for whatever runs next.
afterAll(() => storeLanguage('fr'));

describe('browser detection', () => {
  test('matches on the primary subtag, so a regional English still counts', () => {
    expect(detectBrowserLanguage(['en-GB', 'en'])).toBe('en');
    expect(detectBrowserLanguage(['fr-CA'])).toBe('fr');
  });

  test('follows the order the browser gives, not the order we ship', () => {
    expect(detectBrowserLanguage(['en', 'fr'])).toBe('en');
    expect(detectBrowserLanguage(['fr', 'en'])).toBe('fr');
  });

  test('skips the languages the app does not have', () => {
    expect(detectBrowserLanguage(['de-DE', 'it', 'en-US'])).toBe('en');
  });

  test('gives up rather than guessing when none is offered', () => {
    expect(detectBrowserLanguage(['de', 'ja'])).toBeNull();
    expect(detectBrowserLanguage([])).toBeNull();
  });
});

describe('the stored choice', () => {
  test('survives until it is changed', () => {
    storeLanguage('en');
    expect(readStoredLanguage()).toBe('en');
    storeLanguage('fr');
    expect(readStoredLanguage()).toBe('fr');
  });

  test('is ignored when it names a language the app no longer ships', () => {
    localStorage.setItem(STORAGE_KEY, 'eo');
    expect(readStoredLanguage()).toBeNull();
  });
});

describe('the language the app starts in', () => {
  test('is the browser preference on a first visit', () => {
    browserSpeaks('en-US', 'en');
    expect(resolveInitialLanguage()).toBe('en');
  });

  /** The point of the switcher: a choice made once is not re-litigated on every load. */
  test('is the stored choice even when the browser asks for the other one', () => {
    browserSpeaks('en-US', 'en');
    storeLanguage('fr');
    expect(resolveInitialLanguage()).toBe('fr');

    browserSpeaks('fr-FR', 'fr');
    storeLanguage('en');
    expect(resolveInitialLanguage()).toBe('en');
  });

  test('falls back to the locale the copy is written in', () => {
    browserSpeaks('de-DE');
    expect(resolveInitialLanguage()).toBe(DEFAULT_LANGUAGE);
    expect(DEFAULT_LANGUAGE).toBe('fr');
  });
});

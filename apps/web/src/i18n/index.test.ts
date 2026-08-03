import { afterEach, expect, test } from 'vitest';
import i18n, { changeLanguage } from './index';
import { STORAGE_KEY } from './languages';

// The interface is authored in French; leave the shared singleton on it for the next file.
// Awaiting the reset is the whole point: a suite that starts mid-switch reads the wrong copy,
// which is the intermittent failure this contract removes.
afterEach(() => changeLanguage('fr'));

test('resolves only once the new locale is live, and remembers the choice', async () => {
  await changeLanguage('en');

  expect(i18n.language).toBe('en');
  expect(localStorage.getItem(STORAGE_KEY)).toBe('en');
});

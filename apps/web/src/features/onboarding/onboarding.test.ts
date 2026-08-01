import { beforeEach, describe, expect, test } from 'vitest';
import { hasSeenOnboarding, markOnboardingSeen, shouldShowOnboarding, STORAGE_KEY } from './onboarding';

describe('onboarding', () => {
  beforeEach(() => {
    localStorage.removeItem(STORAGE_KEY);
  });

  test('has not been seen before anything is stored', () => {
    expect(hasSeenOnboarding()).toBe(false);
  });

  test('is marked seen once dismissed, and stays that way', () => {
    markOnboardingSeen();

    expect(hasSeenOnboarding()).toBe(true);
    expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull();
  });

  test('shows for a new account: an empty library, never dismissed', () => {
    expect(shouldShowOnboarding(true)).toBe(true);
  });

  test('never shows a returning user with any library, dismissed or not', () => {
    expect(shouldShowOnboarding(false)).toBe(false);
  });

  test('does not come back once dismissed, even if the library empties out again', () => {
    markOnboardingSeen();

    expect(shouldShowOnboarding(true)).toBe(false);
  });
});

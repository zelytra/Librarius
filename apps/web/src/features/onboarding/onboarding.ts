/**
 * First-login onboarding (#76): whether the short, dismissible tour should be offered.
 *
 * <p>There is no endpoint behind this — the collection is already known to the caller
 * (Home computes the same emptiness for its own empty state), and "has this account
 * already seen the tour" only ever needs to survive on this device, the same rule the
 * theme and the language already follow.
 */

/** Where the choice is kept. Same convention as the theme (`librarius.theme`). */
export const STORAGE_KEY = 'librarius.onboarding-seen';

/**
 * Whether the flow has already been dismissed or completed on this device.
 *
 * <p>Storage unavailable (private mode, blocked cookies) reads as "already seen": the
 * safer failure is to say nothing, not to reopen the tour on every navigation.
 */
export function hasSeenOnboarding(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) != null;
  } catch {
    return true;
  }
}

/** Marks the flow as seen — skipped, finished, or closed, the three read the same. */
export function markOnboardingSeen(): void {
  try {
    localStorage.setItem(STORAGE_KEY, 'true');
  } catch {
    /* storage unavailable: the flow simply reappears next time, which is harmless */
  }
}

/**
 * Whether the tour should be shown for a given account.
 *
 * <p>Both conditions matter: a returning user with a populated library must never see
 * it again even if the flag is somehow missing, and one who dismissed it must not have
 * it come back because they later emptied their collection.
 */
export function shouldShowOnboarding(libraryEmpty: boolean): boolean {
  return libraryEmpty && !hasSeenOnboarding();
}

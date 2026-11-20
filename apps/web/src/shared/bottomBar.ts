import { NAV_DESTINATIONS, NOTIFICATIONS_DESTINATION, PEOPLE_DESTINATION, PHONE_PRIMARY } from '../app/navigation';

/**
 * The pages the phone's bottom bar shows (#561): four by default (#504), the reader's pick
 * otherwise, kept on this device — the bar is the device's. Everything else stays in « Plus ».
 */
export const BOTTOM_BAR_STORAGE_KEY = 'librarius.bottomBar';
export const BOTTOM_BAR_MAX = 4;

/** What the bar may hold, in the order it draws them. */
export const BOTTOM_BAR_CHOICES: readonly string[] = [
  ...NAV_DESTINATIONS.map((d) => d.to),
  PEOPLE_DESTINATION.to,
  NOTIFICATIONS_DESTINATION.to,
];

const valid = (pick: unknown): pick is string[] =>
  Array.isArray(pick) && pick.length > 0 && pick.length <= BOTTOM_BAR_MAX
  && pick.every((to) => BOTTOM_BAR_CHOICES.includes(to));

export function readBottomBar(): readonly string[] {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(BOTTOM_BAR_STORAGE_KEY) ?? 'null');
    return valid(stored) ? BOTTOM_BAR_CHOICES.filter((to) => stored.includes(to)) : PHONE_PRIMARY;
  } catch {
    return PHONE_PRIMARY;
  }
}

export function storeBottomBar(pick: readonly string[]): void {
  try {
    if (valid([...pick])) localStorage.setItem(BOTTOM_BAR_STORAGE_KEY, JSON.stringify(pick));
  } catch {
    /* storage unavailable: the pick lasts as long as the page */
  }
}

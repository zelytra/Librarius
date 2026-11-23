import { patchApiMePreferences, type PreferencesDto } from '../api/generated/librarius';

/**
 * Sends a display preference to the account (#613), so the member's other devices pick it up.
 *
 * Fire and forget: the choice is already in force and stored on this device, which is what
 * the reader sees. A save that fails (offline, API down) is not worth an error on a colour
 * swatch; the next change sends the whole field again.
 */
export function saveAccountPreferences(patch: PreferencesDto): void {
  patchApiMePreferences(patch).catch(() => {
    /* kept on this device; the next change sends it again */
  });
}

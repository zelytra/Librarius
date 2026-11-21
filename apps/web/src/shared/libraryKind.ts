import type { Kind } from '../api/generated/librarius';
import { KINDS } from './kinds';

/**
 * The shelf the library opens on (#545): `auto` is the kind the reader owns the most of — the
 * behaviour since #183, still the default —, otherwise the kind they picked in Settings. Kept
 * on this device, like the theme.
 */
export type LibraryKindPreference = 'auto' | Kind;

export const LIBRARY_KIND_STORAGE_KEY = 'librarius.libraryKind';

export function readLibraryKind(): LibraryKindPreference {
  try {
    const stored = localStorage.getItem(LIBRARY_KIND_STORAGE_KEY);
    // Book, manga or BD only (#597): a stored kind the library does not offer is no pick.
    return KINDS.some((k) => k.id === stored) ? (stored as Kind) : 'auto';
  } catch {
    return 'auto';
  }
}

export function storeLibraryKind(preference: LibraryKindPreference): void {
  try {
    if (preference === 'auto') localStorage.removeItem(LIBRARY_KIND_STORAGE_KEY);
    else localStorage.setItem(LIBRARY_KIND_STORAGE_KEY, preference);
  } catch {
    /* storage unavailable: the pick lasts as long as the page */
  }
}

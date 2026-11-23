import { useEffect, useRef } from 'react';
import { useApiAuth } from '../shared/api';
import { useGetApiMe, type PreferencesDto } from '../api/generated/librarius';
import { useTheme } from '../shared/theme/context';
import { STORAGE_KEY as THEME_STORAGE_KEY, isThemeId } from '../shared/theme/themes';
import { BOTTOM_BAR_STORAGE_KEY, readBottomBar, storeBottomBar } from '../shared/bottomBar';
import { LIBRARY_KIND_STORAGE_KEY, readLibraryKind, storeLibraryKind } from '../shared/libraryKind';
import { KINDS } from '../shared/kinds';
import {
  SHEET_THEMES,
  SHEET_THEME_STORAGE_KEY,
  readSheetTheme,
  storeSheetTheme,
  type SheetTheme,
} from '../features/sheet/readingSheet';
import { saveAccountPreferences } from '../shared/accountPreferences';

/** Whether this device holds a choice of its own under `key`, rather than the default. */
function storedLocally(key: string): boolean {
  try {
    return localStorage.getItem(key) != null;
  } catch {
    return false;
  }
}

/**
 * Lets the account's display preferences drive this device once the profile is loaded (#613),
 * the loop `useProfileLanguage` closes for the language.
 *
 * `localStorage` stays the value the app boots on — the first paint cannot wait for the API —
 * and this hook replaces it with the account's. A value this version of the app does not know
 * (a palette added later, a page since removed) is ignored, and the device keeps its own.
 *
 * A key the account has never held, while this device has a choice of its own, is sent up:
 * whatever was picked before the preferences followed the account is not lost.
 */
export function useProfilePreferences(): void {
  const auth = useApiAuth();
  const { data: me } = useGetApiMe({ query: { enabled: auth.authed } });
  const preferences = me?.preferences;
  const { setTheme } = useTheme();
  // `setTheme` changes identity with the theme: read through a ref so applying it does not
  // re-run the effect.
  const setThemeRef = useRef(setTheme);
  setThemeRef.current = setTheme;

  useEffect(() => {
    if (!preferences) return;
    const { theme, bottomBar, libraryKind, sheetTheme } = preferences;
    const upload: PreferencesDto = {};

    if (theme != null) {
      if (isThemeId(theme)) setThemeRef.current(theme);
    } else if (storedLocally(THEME_STORAGE_KEY)) {
      const local = localStorage.getItem(THEME_STORAGE_KEY);
      if (isThemeId(local)) upload.theme = local;
    }

    if (bottomBar != null) storeBottomBar(bottomBar);
    else if (storedLocally(BOTTOM_BAR_STORAGE_KEY)) upload.bottomBar = [...readBottomBar()];

    if (libraryKind != null) {
      if (libraryKind === 'auto' || KINDS.some((k) => k.id === libraryKind)) {
        storeLibraryKind(libraryKind as Parameters<typeof storeLibraryKind>[0]);
      }
    } else if (storedLocally(LIBRARY_KIND_STORAGE_KEY)) {
      upload.libraryKind = readLibraryKind();
    }

    if (sheetTheme != null) {
      if (SHEET_THEMES.includes(sheetTheme as SheetTheme)) storeSheetTheme(sheetTheme as SheetTheme);
    } else if (storedLocally(SHEET_THEME_STORAGE_KEY)) {
      upload.sheetTheme = readSheetTheme();
    }

    if (Object.keys(upload).length > 0) saveAccountPreferences(upload);
  }, [preferences]);
}

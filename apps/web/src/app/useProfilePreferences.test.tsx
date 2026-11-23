import { render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { TestProviders } from '../test/utils';
import { http, HttpResponse, server } from '../test/server';
import { resetAuth, setAuthenticated } from '../test/oidcMock';
import { BOTTOM_BAR_STORAGE_KEY, readBottomBar } from '../shared/bottomBar';
import { LIBRARY_KIND_STORAGE_KEY, readLibraryKind } from '../shared/libraryKind';
import { STORAGE_KEY as THEME_STORAGE_KEY } from '../shared/theme/themes';
import { SHEET_THEME_STORAGE_KEY, readSheetTheme } from '../features/sheet/readingSheet';
import type { PreferencesDto } from '../api/generated/librarius';

vi.mock('react-oidc-context', () => import('../test/oidcMock'));

const { useProfilePreferences } = await import('./useProfilePreferences');

function Probe() {
  useProfilePreferences();
  return null;
}

function profileHolds(preferences: PreferencesDto) {
  server.use(http.get('*/api/me', () => HttpResponse.json({ id: 'alice', locale: 'fr', preferences })));
}

/** Records what reaches `PATCH /api/me/preferences`. */
function captureUploads() {
  const sent: PreferencesDto[] = [];
  server.use(
    http.patch('*/api/me/preferences', async ({ request }) => {
      const body = (await request.json()) as PreferencesDto;
      sent.push(body);
      return HttpResponse.json(body);
    }),
  );
  return sent;
}

/**
 * #613: a choice made on one device shows on the others. The account's preferences arrive with
 * the profile and replace what this device had; a choice this device made before the account
 * held any is sent up rather than lost.
 */
describe('useProfilePreferences', () => {
  beforeEach(() => {
    resetAuth();
    setAuthenticated(true);
    localStorage.clear();
  });
  afterEach(() => localStorage.clear());

  test('applies the account’s choices on this device', async () => {
    profileHolds({ theme: 'rose', bottomBar: ['/', '/people'], libraryKind: 'MANGA', sheetTheme: 'notebook' });
    render(<Probe />, { wrapper: TestProviders });

    await waitFor(() => expect(document.documentElement.getAttribute('data-theme')).toBe('rose'));
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('rose');
    expect(readBottomBar()).toEqual(['/', '/people']);
    expect(readLibraryKind()).toBe('MANGA');
    expect(readSheetTheme()).toBe('notebook');
  });

  test('ignores a value this version of the app does not know', async () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'sauge');
    profileHolds({ theme: 'fuchsia', bottomBar: ['/nowhere'] });
    render(<Probe />, { wrapper: TestProviders });

    await waitFor(() => expect(document.documentElement.getAttribute('data-theme')).toBe('sauge'));
    expect(localStorage.getItem(BOTTOM_BAR_STORAGE_KEY)).toBeNull();
  });

  test('sends up what this device chose before the account held anything', async () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'ocean');
    localStorage.setItem(LIBRARY_KIND_STORAGE_KEY, 'BOOK');
    profileHolds({ sheetTheme: 'classic' });
    const sent = captureUploads();
    render(<Probe />, { wrapper: TestProviders });

    await waitFor(() => expect(sent).toEqual([{ theme: 'ocean', libraryKind: 'BOOK' }]));
    expect(localStorage.getItem(SHEET_THEME_STORAGE_KEY)).toBe('classic');
  });

  test('sends nothing when the account already holds every choice', async () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'ocean');
    profileHolds({ theme: 'rose', bottomBar: ['/'], libraryKind: 'auto', sheetTheme: 'classic' });
    const sent = captureUploads();
    render(<Probe />, { wrapper: TestProviders });

    await waitFor(() => expect(document.documentElement.getAttribute('data-theme')).toBe('rose'));
    expect(sent).toEqual([]);
  });
});

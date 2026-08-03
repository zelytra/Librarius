import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { removeUser, resetAuth, setAuthenticated, signoutRedirect } from '../../test/oidcMock';
import { changeLanguage } from '../../i18n';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { SettingsPage } = await import('./SettingsPage');

/**
 * jsdom implements neither object URLs nor a real download, so the browser side is captured
 * rather than performed. What the test can and should assert is that the API was called and
 * that the file the API named is the one handed to the browser.
 */
const savedFiles: string[] = [];

beforeEach(() => {
  savedFiles.length = 0;
  URL.createObjectURL = vi.fn(() => 'blob:librarius');
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function saved(
    this: HTMLAnchorElement,
  ) {
    savedFiles.push(this.download);
  });
});

afterEach(() => vi.restoreAllMocks());

/** The API answers with a file, and the name the browser should save it under. */
function exportReturns(body: string, filename: string, contentType: string) {
  server.use(http.get('*/api/export', () =>
    new HttpResponse(body, {
      headers: {
        'Content-Type': contentType,
        'Content-Disposition': `attachment; filename="${filename}"`,
      },
    })));
}

describe('SettingsPage — export', () => {
  beforeEach(resetAuth);

  test('downloads the complete archive as JSON', async () => {
    exportReturns('{"schemaVersion":1}', 'librarius-export-2026-07-28.json',
      'application/json;charset=UTF-8');
    renderWithProviders(<SettingsPage />);

    await userEvent.click(screen.getByText('Fichier JSON'));

    await waitFor(() => expect(savedFiles).toEqual(['librarius-export-2026-07-28.json']));
    expect(await screen.findByText('Export téléchargé.')).toBeInTheDocument();
  });

  test('downloads the book list as CSV', async () => {
    exportReturns('Title;Author\r\n', 'librarius-export-2026-07-28.csv', 'text/csv;charset=UTF-8');
    renderWithProviders(<SettingsPage />);

    await userEvent.click(screen.getByText('Fichier CSV'));

    await waitFor(() => expect(savedFiles).toEqual(['librarius-export-2026-07-28.csv']));
  });

  /**
   * A large account is built in the background: the API answers 202 with a job, and the
   * screen comes back for the file instead of reporting a failure.
   */
  test('waits for a deferred export and downloads it once it is ready', async () => {
    server.use(
      http.get('*/api/export', () =>
        HttpResponse.json({ id: 'job-1', status: 'PENDING' }, { status: 202 })),
      http.get('*/api/export/:jobId', () =>
        new HttpResponse('{"schemaVersion":1}', {
          headers: {
            'Content-Type': 'application/json',
            'Content-Disposition': 'attachment; filename="librarius-export-big.json"',
          },
        })),
    );
    renderWithProviders(<SettingsPage />);

    await userEvent.click(screen.getByText('Fichier JSON'));

    await waitFor(() => expect(savedFiles).toEqual(['librarius-export-big.json']),
      { timeout: 5000 });
  }, 10_000);

  test('reports a failed export instead of pretending it worked', async () => {
    server.use(http.get('*/api/export', () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<SettingsPage />);

    await userEvent.click(screen.getByText('Fichier JSON'));

    expect(await screen.findByText('Erreur 500')).toBeInTheDocument();
    expect(savedFiles).toEqual([]);
  });

  test('offers to sign in rather than to export when there is no session', () => {
    setAuthenticated(false);
    renderWithProviders(<SettingsPage />);

    expect(screen.getByText('Se connecter pour exporter')).toBeInTheDocument();
    expect(screen.queryByText('Fichier JSON')).not.toBeInTheDocument();
  });
});

describe('SettingsPage — language', () => {
  beforeEach(resetAuth);

  // The rest of the suite asserts the French copy, and i18next is a singleton.
  afterEach(() => changeLanguage('fr'));

  // The Profile section (#75) carries its own language control, so the standalone switcher
  // is the last one in the page — targeting it keeps this about the quick toggle.
  const standaloneSwitch = (name: string) =>
    screen.getAllByRole('button', { name }).at(-1) as HTMLElement;

  test('switches the interface and keeps the choice for the next visit', async () => {
    renderWithProviders(<SettingsPage />);
    expect(screen.getByRole('heading', { name: 'Réglages' })).toBeInTheDocument();

    await userEvent.click(standaloneSwitch('English'));

    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument();
    expect(localStorage.getItem('librarius.language')).toBe('en');
    // The two strings React does not own: the `lang` a screen reader reads from, and the
    // tab title `index.html` shipped in French.
    expect(document.documentElement.lang).toBe('en');
    expect(document.title).toBe('My Library');
  });

  test('labels each language in its own words, so it can be found from the other one',
    async () => {
      renderWithProviders(<SettingsPage />);

      await userEvent.click(standaloneSwitch('English'));

      expect(await screen.findAllByRole('button', { name: 'Français' })).not.toHaveLength(0);
    });
});

describe('SettingsPage — account deletion', () => {
  beforeEach(resetAuth);

  /** Opens the panel and waits for the profile, which the confirmation is checked against. */
  async function openTheDangerPanel() {
    renderWithProviders(<SettingsPage />);
    await userEvent.click(screen.getByText('Supprimer mon compte', { selector: 'button' }));
    return screen.findByLabelText(/tape ton nom d'utilisateur : alice/);
  }

  test('states what goes, what stays, and how long the backups keep it', async () => {
    await openTheDangerPanel();

    expect(screen.getByText(/Cette action est définitive/)).toBeInTheDocument();
    expect(screen.getByText(/restent dans le catalogue partagé/)).toBeInTheDocument();
    expect(screen.getByText(/jusqu'à six mois/)).toBeInTheDocument();
  });

  test('refuses to delete until the username is typed exactly', async () => {
    const field = await openTheDangerPanel();
    const confirm = screen.getByRole('button', { name: 'Supprimer définitivement' });

    expect(confirm).toBeDisabled();
    await userEvent.type(field, 'bob');
    expect(confirm).toBeDisabled();
  });

  test('deletes the account once the username matches, and drops the session', async () => {
    let deleted = false;
    server.use(http.delete('*/api/me', () => {
      deleted = true;
      return HttpResponse.json({
        libraryItems: 3, wishlistItems: 1, goals: 0, categories: 0, seriesFollows: 0,
      });
    }));
    const field = await openTheDangerPanel();

    await userEvent.type(field, 'alice');
    await userEvent.click(screen.getByRole('button', { name: 'Supprimer définitivement' }));

    expect(await screen.findByText(/Ton compte et toutes tes données ont été supprimés/))
      .toBeInTheDocument();
    expect(deleted).toBe(true);
    expect(removeUser).toHaveBeenCalled();
  });

  /** The API refuses when it cannot delete the login, and says nothing was erased. */
  test('shows the reason when the API refuses, and keeps the session', async () => {
    server.use(http.delete('*/api/me', () =>
      HttpResponse.json({ message: 'La suppression a échoué côté authentification.' },
        { status: 503 })));
    const field = await openTheDangerPanel();

    await userEvent.type(field, 'alice');
    await userEvent.click(screen.getByRole('button', { name: 'Supprimer définitivement' }));

    expect(await screen.findByText(/La suppression a échoué côté authentification/))
      .toBeInTheDocument();
    expect(removeUser).not.toHaveBeenCalled();
  });

  test('offers the export from inside the confirmation', async () => {
    exportReturns('{"schemaVersion":1}', 'librarius-export-2026-07-28.json', 'application/json');
    await openTheDangerPanel();

    await userEvent.click(screen.getByText("Exporter mes données d'abord"));

    await waitFor(() => expect(savedFiles).toEqual(['librarius-export-2026-07-28.json']));
  });

  test('is invisible without a session', () => {
    setAuthenticated(false);
    renderWithProviders(<SettingsPage />);

    expect(screen.queryByText('Supprimer mon compte')).not.toBeInTheDocument();
  });
});

describe('SettingsPage — session', () => {
  beforeEach(resetAuth);

  test('signs the user out, ending the Keycloak session', async () => {
    renderWithProviders(<SettingsPage />);

    await userEvent.click(screen.getByRole('button', { name: 'Se déconnecter' }));

    expect(signoutRedirect).toHaveBeenCalled();
  });

  test('hides the sign-out when there is no session', () => {
    setAuthenticated(false);
    renderWithProviders(<SettingsPage />);

    expect(screen.queryByRole('button', { name: 'Se déconnecter' })).not.toBeInTheDocument();
  });
});

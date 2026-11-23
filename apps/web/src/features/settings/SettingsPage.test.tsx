import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { removeUser, resetAuth, setAuthenticated, signoutRedirect } from '../../test/oidcMock';
import { changeLanguage } from '../../i18n';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { SettingsPage } = await import('./SettingsPage');

/** Settings by category (#546): each one is its own address. */
function renderSettings(category?: string) {
  return renderWithProviders(<SettingsPage />, {
    route: category ? `/settings/${category}` : '/settings',
    path: '/settings/:category?',
  });
}

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
    renderSettings('data');

    await userEvent.click(await screen.findByText('Fichier JSON'));

    await waitFor(() => expect(savedFiles).toEqual(['librarius-export-2026-07-28.json']));
    expect(await screen.findByText('Export téléchargé.')).toBeInTheDocument();
  });

  test('downloads the book list as CSV', async () => {
    exportReturns('Title;Author\r\n', 'librarius-export-2026-07-28.csv', 'text/csv;charset=UTF-8');
    renderSettings('data');

    await userEvent.click(await screen.findByText('Fichier CSV'));

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
    renderSettings('data');

    await userEvent.click(await screen.findByText('Fichier JSON'));

    await waitFor(() => expect(savedFiles).toEqual(['librarius-export-big.json']),
      { timeout: 5000 });
  }, 10_000);

  test('reports a failed export instead of pretending it worked', async () => {
    server.use(http.get('*/api/export', () => new HttpResponse(null, { status: 500 })));
    renderSettings('data');

    await userEvent.click(await screen.findByText('Fichier JSON'));

    expect(await screen.findByText('Erreur 500')).toBeInTheDocument();
    expect(savedFiles).toEqual([]);
  });

  test('offers to sign in rather than to export when there is no session', async () => {
    setAuthenticated(false);
    renderSettings('data');

    expect(await screen.findByText('Se connecter pour exporter')).toBeInTheDocument();
    expect(screen.queryByText('Fichier JSON')).not.toBeInTheDocument();
  });
});

describe('SettingsPage — language', () => {
  beforeEach(() => setAuthenticated(false));
  afterEach(() => {
    resetAuth();
    // The rest of the suite asserts the French copy, and i18next is a singleton.
    changeLanguage('fr');
  });

  // Signed out, the device-local switcher in Appearance is the only language control.
  test('switches the interface and keeps the choice for the next visit', async () => {
    renderSettings('appearance');
    expect(screen.getByRole('heading', { name: 'Apparence' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'English' }));

    expect(await screen.findByRole('heading', { name: 'Appearance' })).toBeInTheDocument();
    expect(localStorage.getItem('librarius.language')).toBe('en');
    // The two strings React does not own: the `lang` a screen reader reads from, and the
    // tab title `index.html` shipped in French.
    expect(document.documentElement.lang).toBe('en');
    expect(document.title).toBe('Librarius');
  });

  test('labels each language in its own words, so it can be found from the other one',
    async () => {
      renderSettings('appearance');

      await userEvent.click(screen.getByRole('button', { name: 'English' }));

      expect(await screen.findAllByRole('button', { name: 'Français' })).not.toHaveLength(0);
    });
});

/** #547: signed in, the language is the profile's — one control, never two. */
describe('SettingsPage — a single language control', () => {
  beforeEach(resetAuth);

  test('offers the language in the profile only, and points there from Appearance', async () => {
    renderSettings('profile');
    expect(await screen.findAllByRole('button', { name: 'English' })).toHaveLength(1);
  });

  test('leaves Appearance without a second switcher', async () => {
    renderSettings('appearance');

    expect(screen.queryByRole('button', { name: 'English' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'La langue se règle dans ton profil' }))
      .toHaveAttribute('href', '/settings/profile');
  });
});

/** #546: the settings split into categories, each a page of its own. */
describe('SettingsPage — categories', () => {
  beforeEach(resetAuth);

  test('lists the categories, each leading to its own page', async () => {
    renderSettings();

    // The first list is the categories; the legal links (#74) close the page under it.
    const links = within(screen.getAllByRole('list')[0]).getAllByRole('link');
    expect(links.map((l) => l.getAttribute('href'))).toEqual([
      '/settings/profile', '/settings/reading', '/settings/appearance', '/settings/community', '/settings/data',
    ]);
    await userEvent.click(screen.getByRole('link', { name: /Données et compte/ }));
    expect(await screen.findByRole('heading', { name: 'Données et compte' })).toBeInTheDocument();
    expect(await screen.findByText('Fichier JSON')).toBeInTheDocument();
  });

  test('shows only the sections of the category opened', () => {
    renderSettings('community');

    expect(screen.getByRole('heading', { name: 'Communauté' })).toBeInTheDocument();
    expect(screen.queryByText('Fichier JSON')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retour aux réglages' })).toBeInTheDocument();
  });

  /** #74: the legal documents are one tap away from Settings. */
  test('links the terms, the privacy policy and the legal notice under the list', () => {
    renderSettings();

    const legal = screen.getByRole('navigation', { name: 'Informations légales' });
    expect(within(legal).getByRole('link', { name: 'Conditions d’utilisation' })).toHaveAttribute('href', '/legal/terms');
    expect(within(legal).getByRole('link', { name: 'Confidentialité' })).toHaveAttribute('href', '/legal/privacy');
    expect(within(legal).getByRole('link', { name: 'Mentions légales' })).toHaveAttribute('href', '/legal/notice');
  });

  test('sends an unknown category back to the list', () => {
    renderWithProviders(<SettingsPage />, { route: '/settings/nope', path: '/settings/:category?' });
    // The redirect lands on the list, which this route also renders.
    expect(screen.getByRole('link', { name: /Profil/ })).toBeInTheDocument();
  });
});

/** #545: the library's opening shelf, picked here. */
describe('SettingsPage — library opening shelf', () => {
  beforeEach(() => {
    resetAuth();
    localStorage.removeItem('librarius.libraryKind');
  });

  /** #597: the shelves the library always offers (#383), nothing it does not show. */
  test("offers the library's own shelves only", () => {
    renderSettings('reading');

    const group = screen.getByRole('group', { name: "Bibliothèque à l'ouverture" });
    expect(within(group).getAllByRole('button').map((b) => b.textContent)).toEqual(['Automatique', 'Livre', 'Manga', 'BD']);
  });

  test('is automatic until a kind is picked, and keeps the pick', async () => {
    renderSettings('reading');

    expect(screen.getByRole('button', { name: 'Automatique' })).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(screen.getByRole('button', { name: 'Manga' }));

    expect(screen.getByRole('button', { name: 'Manga' })).toHaveAttribute('aria-pressed', 'true');
    expect(localStorage.getItem('librarius.libraryKind')).toBe('MANGA');

    await userEvent.click(screen.getByRole('button', { name: 'Automatique' }));
    expect(localStorage.getItem('librarius.libraryKind')).toBeNull();
  });
});

/** #550: the reading sheet's theme, picked on a preview, and the blank sheet. */
describe('SettingsPage — reading sheet', () => {
  beforeEach(() => {
    resetAuth();
    localStorage.removeItem('librarius.sheetTheme');
  });

  test('picks a theme from its preview, and keeps it', async () => {
    renderSettings('reading');

    const handwritten = await screen.findByRole('button', { name: 'Manuscrite' });
    expect(handwritten).toHaveAttribute('aria-pressed', 'true');
    // Each choice draws the sheet itself, in its theme.
    const notebook = screen.getByRole('button', { name: 'Cahier' });
    expect(notebook.querySelector('article[data-theme="notebook"]')).not.toBeNull();

    await userEvent.click(notebook);
    expect(notebook).toHaveAttribute('aria-pressed', 'true');
    expect(localStorage.getItem('librarius.sheetTheme')).toBe('notebook');
  });

  test('prints a blank sheet from here', async () => {
    renderSettings('reading');

    expect(await screen.findByRole('link', { name: 'Imprimer une fiche vierge' })).toHaveAttribute('href', '/sheet/blank');
  });
});

describe('SettingsPage — account deletion', () => {
  beforeEach(resetAuth);

  /** Opens the panel and waits for the profile, which the confirmation is checked against. */
  async function openTheDangerPanel() {
    renderSettings('data');
    await userEvent.click(await screen.findByText('Supprimer mon compte', { selector: 'button' }));
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

  test('is invisible without a session', async () => {
    setAuthenticated(false);
    renderSettings('data');

    // The category is a lazy chunk: wait for it before asserting what it leaves out.
    await screen.findByText('Se connecter pour exporter');
    expect(screen.queryByText('Supprimer mon compte')).not.toBeInTheDocument();
  });
});

describe('SettingsPage — session', () => {
  beforeEach(resetAuth);

  test('signs the user out, ending the Keycloak session', async () => {
    renderSettings('profile');

    await userEvent.click(screen.getByRole('button', { name: 'Se déconnecter' }));

    expect(signoutRedirect).toHaveBeenCalled();
  });

  test('hides the sign-out when there is no session', () => {
    setAuthenticated(false);
    renderSettings('profile');

    expect(screen.queryByRole('button', { name: 'Se déconnecter' })).not.toBeInTheDocument();
  });
});

/** #496: the administration moved to the navigation; Settings no longer carries a way in. */
describe('SettingsPage — administration', () => {
  beforeEach(() => setAuthenticated(true));
  afterEach(() => resetAuth());

  test('leaves the administration to the navigation, even for an admin', async () => {
    server.use(http.get('*/api/me', () => HttpResponse.json({ id: 'alice', displayName: 'Alice', admin: true })));
    renderSettings('profile');

    await screen.findByDisplayValue('Alice');
    expect(screen.queryByRole('button', { name: "Ouvrir l'administration" })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
  });
});

/** #561: the pages of the phone's bottom bar, picked here — four at most, one at least. */
describe('SettingsPage — bottom bar', () => {
  beforeEach(() => {
    resetAuth();
    localStorage.removeItem('librarius.bottomBar');
  });

  test('starts from the four pages of the bar, and keeps a new pick', async () => {
    renderSettings('appearance');
    const group = screen.getByRole('group', { name: 'Barre du bas (téléphone)' });
    const box = (name: string) => within(group).getByRole('checkbox', { name });

    expect(box('Accueil')).toBeChecked();
    expect(box('Stats')).not.toBeChecked();
    // Four already: nothing more can be added until one goes.
    expect(box('Stats')).toBeDisabled();

    await userEvent.click(box('Souhaits'));
    await userEvent.click(box('Stats'));
    expect(JSON.parse(localStorage.getItem('librarius.bottomBar') ?? '[]')).toEqual(['/', '/collection', '/discover', '/stats']);
  });

  test('keeps at least one page', async () => {
    localStorage.setItem('librarius.bottomBar', JSON.stringify(['/']));
    renderSettings('appearance');

    const group = screen.getByRole('group', { name: 'Barre du bas (téléphone)' });
    expect(within(group).getByRole('checkbox', { name: 'Accueil' })).toBeDisabled();
  });
});

/** #591: back never loops inside Settings — a category goes to the list, the list leaves. */
describe('SettingsPage — back', () => {
  beforeEach(resetAuth);

  test('goes from a category to the list, then out of Settings', async () => {
    const { MemoryRouter, Route, Routes } = await import('react-router');
    const { render } = await import('@testing-library/react');
    const { QueryClientProvider } = await import('@tanstack/react-query');
    const { createTestQueryClient } = await import('../../test/utils');
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <MemoryRouter initialEntries={['/', '/settings']} initialIndex={1}>
          <Routes>
            <Route path="/" element={<p>Accueil test</p>} />
            <Route path="/settings/:category?" element={<SettingsPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await userEvent.click(screen.getByRole('link', { name: /Profil/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Retour aux réglages' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Retour' }));
    expect(await screen.findByText('Accueil test')).toBeInTheDocument();
  });

  test('leaves for Home when Settings was opened directly', async () => {
    const { MemoryRouter, Route, Routes } = await import('react-router');
    const { render } = await import('@testing-library/react');
    const { QueryClientProvider } = await import('@tanstack/react-query');
    const { createTestQueryClient } = await import('../../test/utils');
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <MemoryRouter initialEntries={['/settings/profile']}>
          <Routes>
            <Route path="/" element={<p>Accueil test</p>} />
            <Route path="/settings/:category?" element={<SettingsPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Retour aux réglages' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Retour' }));
    expect(await screen.findByText('Accueil test')).toBeInTheDocument();
  });
});

/** #613: every display choice made here is sent to the account, for the member's other devices. */
describe('SettingsPage — preferences follow the account', () => {
  const sent: unknown[] = [];

  beforeEach(() => {
    resetAuth();
    setAuthenticated(true);
    localStorage.clear();
    sent.length = 0;
    server.use(
      http.patch('*/api/me/preferences', async ({ request }) => {
        const body = await request.json();
        sent.push(body);
        return HttpResponse.json(body);
      }),
    );
  });
  afterEach(() => localStorage.clear());

  test('sends the palette and the bottom bar picked under Appearance', async () => {
    renderSettings('appearance');

    await userEvent.click(screen.getByRole('button', { name: 'Rose' }));
    await waitFor(() => expect(sent).toContainEqual({ theme: 'rose' }));

    const group = screen.getByRole('group', { name: 'Barre du bas (téléphone)' });
    await userEvent.click(within(group).getByRole('checkbox', { name: 'Accueil' }));
    await waitFor(() => expect(sent).toContainEqual({ bottomBar: expect.not.arrayContaining(['/']) as unknown }));
  });

  test('sends the opening shelf and the sheet theme picked under Reading', async () => {
    renderSettings('reading');

    await userEvent.click(screen.getByRole('button', { name: 'Manga' }));
    await waitFor(() => expect(sent).toContainEqual({ libraryKind: 'MANGA' }));
    await userEvent.click(screen.getByRole('button', { name: 'Automatique' }));
    await waitFor(() => expect(sent).toContainEqual({ libraryKind: 'auto' }));

    await userEvent.click(await screen.findByRole('button', { name: 'Cahier' }));
    await waitFor(() => expect(sent).toContainEqual({ sheetTheme: 'notebook' }));
  });
});

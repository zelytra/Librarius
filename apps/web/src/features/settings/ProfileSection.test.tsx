import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';
import { changeLanguage } from '../../i18n';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { ProfileSection } = await import('./ProfileSection');

/** Serves a profile on `GET /api/me`. */
function profileReturns(me: {
  displayName?: string;
  locale?: string;
  timeZone?: string;
}) {
  server.use(http.get('*/api/me', () => HttpResponse.json({ id: 'alice', ...me })));
}

/** Records what reaches `PATCH /api/me`, echoing the body back the way the API does. */
function captureSaves() {
  const saved: unknown[] = [];
  server.use(
    http.patch('*/api/me', async ({ request }) => {
      const body = await request.json();
      saved.push(body);
      return HttpResponse.json({ id: 'alice', ...(body as object) });
    }),
  );
  return saved;
}

describe('ProfileSection', () => {
  beforeEach(resetAuth);
  // The rest of the suite asserts the French copy, and i18next is a singleton.
  afterEach(() => changeLanguage('fr'));

  test('fills the form from the stored profile and saves the three fields', async () => {
    profileReturns({ displayName: 'alice', locale: 'fr', timeZone: 'Europe/Paris' });
    const saved = captureSaves();
    renderWithProviders(<ProfileSection />);

    const name = await screen.findByLabelText('Nom affiché');
    await waitFor(() => expect(name).toHaveValue('alice'));
    expect(screen.getByLabelText('Fuseau horaire')).toHaveValue('Europe/Paris');

    await userEvent.clear(name);
    await userEvent.type(name, 'Alice Liddell');
    await userEvent.selectOptions(screen.getByLabelText('Fuseau horaire'), 'America/New_York');
    await userEvent.click(screen.getByText('Enregistrer'));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toEqual({
      displayName: 'Alice Liddell',
      locale: 'fr',
      timeZone: 'America/New_York',
    });
    expect(await screen.findByText('Profil enregistré.')).toBeInTheDocument();
  });

  test('clears the time zone by choosing "follow this device"', async () => {
    profileReturns({ displayName: 'alice', locale: 'fr', timeZone: 'Asia/Tokyo' });
    const saved = captureSaves();
    renderWithProviders(<ProfileSection />);

    await waitFor(() =>
      expect(screen.getByLabelText('Fuseau horaire')).toHaveValue('Asia/Tokyo'));
    await userEvent.selectOptions(screen.getByLabelText('Fuseau horaire'), '');
    await userEvent.click(screen.getByText('Enregistrer'));

    await waitFor(() => expect(saved).toHaveLength(1));
    // An empty zone is left out of the body rather than sent as a blank string.
    expect(saved[0]).toEqual({ displayName: 'alice', locale: 'fr' });
  });

  test('applies the chosen language to the interface on save', async () => {
    profileReturns({ displayName: 'alice', locale: 'fr' });
    captureSaves();
    renderWithProviders(<ProfileSection />);

    expect(await screen.findByRole('heading', { name: 'Profil' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'English' }));
    await userEvent.click(screen.getByText('Enregistrer'));

    expect(await screen.findByRole('heading', { name: 'Profile' })).toBeInTheDocument();
    expect(localStorage.getItem('librarius.language')).toBe('en');
  });

  test('refuses to save without a display name', async () => {
    profileReturns({ displayName: 'alice', locale: 'fr' });
    const saved = captureSaves();
    renderWithProviders(<ProfileSection />);

    const name = await screen.findByLabelText('Nom affiché');
    // Wait for the prefill before clearing, so the async profile cannot repopulate the field
    // right after and re-enable the button under the assertion.
    await waitFor(() => expect(name).toHaveValue('alice'));
    await userEvent.clear(name);

    const submit = screen.getByText('Enregistrer');
    expect(submit).toBeDisabled();
    await userEvent.click(submit);
    expect(saved).toHaveLength(0);
  });

  test('says so when saving fails rather than pretending it worked', async () => {
    profileReturns({ displayName: 'alice', locale: 'fr' });
    server.use(http.patch('*/api/me', () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<ProfileSection />);

    await userEvent.click(await screen.findByText('Enregistrer'));

    expect(await screen.findByText(/Impossible d'enregistrer le profil/)).toBeInTheDocument();
    expect(screen.queryByText('Profil enregistré.')).not.toBeInTheDocument();
  });

  test('asks for a session instead of a form when there is none', async () => {
    setAuthenticated(false);
    renderWithProviders(<ProfileSection />);

    expect(await screen.findByText(/Connecte-toi pour modifier ton profil/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Nom affiché')).not.toBeInTheDocument();
  });
});

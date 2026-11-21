import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated, signoutRedirect } from '../../test/oidcMock';
import { Toaster } from '../../shared/ui/Toaster';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { TermsWatcher } = await import('./TermsWatcher');

const ME = { id: 'alice', displayName: 'Alice', email: 'alice@test.fr', locale: 'fr', currentTermsVersion: 1 };

function serveMe(termsVersion: number | null) {
  server.use(http.get('*/api/me', () => HttpResponse.json({ ...ME, termsVersion, termsAcceptedAt: termsVersion ? '2026-11-21T10:00:00Z' : null })));
}

function renderWatcher() {
  return renderWithProviders(
    <>
      <TermsWatcher />
      <Toaster />
    </>,
  );
}

/**
 * #74: a member accepts the terms and the privacy policy on their first sign-in, and again
 * when their version changes. Until they do, the app waits behind the request.
 */
describe('terms acceptance', () => {
  beforeEach(() => setAuthenticated(true));
  afterEach(() => resetAuth());

  test('a member who has accepted nothing is asked, with the documents one tap away', async () => {
    serveMe(null);
    renderWatcher();
    const dialog = await screen.findByRole('dialog', { name: 'Conditions d’utilisation' });
    expect(within(dialog).getByRole('link', { name: 'Conditions d’utilisation' })).toHaveAttribute('href', '/legal/terms');
    expect(within(dialog).getByRole('link', { name: 'Confidentialité' })).toHaveAttribute('href', '/legal/privacy');
  });

  test('accepting records the version shown, then lets the member in', async () => {
    serveMe(null);
    let body: unknown;
    server.use(http.put('*/api/me/terms', async ({ request }) => {
      body = await request.json();
      return HttpResponse.json({ ...ME, termsVersion: 1, termsAcceptedAt: '2026-11-21T10:00:00Z' });
    }));
    renderWatcher();

    await userEvent.click(await screen.findByRole('button', { name: 'J’accepte' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(body).toEqual({ version: 1 });
    expect(await screen.findByText('Merci, c’est noté.')).toBeInTheDocument();
  });

  test('a refusal from the server keeps the request open and says so', async () => {
    serveMe(null);
    server.use(http.put('*/api/me/terms', () => new HttpResponse(null, { status: 500 })));
    renderWatcher();

    await userEvent.click(await screen.findByRole('button', { name: 'J’accepte' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('L’acceptation n’a pas pu être enregistrée');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  test('declining signs the member out', async () => {
    serveMe(null);
    renderWatcher();
    await userEvent.click(await screen.findByRole('button', { name: 'Refuser et me déconnecter' }));
    expect(signoutRedirect).toHaveBeenCalled();
  });

  test('a member who accepted the version in force is not asked', async () => {
    serveMe(1);
    renderWatcher();
    // Let the profile arrive before asserting the absence.
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  test('a member who accepted an older version is asked again', async () => {
    server.use(http.get('*/api/me', () => HttpResponse.json({ ...ME, currentTermsVersion: 2, termsVersion: 1 })));
    renderWatcher();
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });

  test('nobody is asked while signed out', async () => {
    setAuthenticated(false);
    serveMe(null);
    renderWatcher();
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});

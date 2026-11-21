import { screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { LegalPage } = await import('./LegalPage');

function renderLegal(doc: string) {
  return renderWithProviders(<LegalPage />, { route: `/legal/${doc}`, path: '/legal/:doc' });
}

/**
 * #74: the terms of service, the privacy policy and the legal notice, readable by anyone —
 * no account, no sign-in.
 */
describe('legal pages', () => {
  afterEach(() => resetAuth());

  test('the terms of service are readable signed out', async () => {
    setAuthenticated(false);
    renderLegal('terms');
    expect(await screen.findByRole('heading', { level: 1, name: "Conditions générales d'utilisation" })).toBeInTheDocument();
    expect(screen.getByText(/dernière mise à jour/i)).toBeInTheDocument();
  });

  test('the privacy policy names the data, the processors, the retention and the rights', async () => {
    setAuthenticated(false);
    renderLegal('privacy');
    expect(await screen.findByRole('heading', { level: 1, name: 'Politique de confidentialité' })).toBeInTheDocument();
    for (const heading of [/données collectées/i, /sous-traitants/i, /conservation/i, /tes droits/i]) {
      expect(screen.getByRole('heading', { level: 2, name: heading })).toBeInTheDocument();
    }
    expect(screen.getByText(/Open Library/)).toBeInTheDocument();
    expect(screen.getByText(/AniList/)).toBeInTheDocument();
  });

  test('the legal notice names the publisher and the host', async () => {
    renderLegal('notice');
    expect(await screen.findByRole('heading', { level: 1, name: 'Mentions légales' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: /éditeur/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: /hébergeur/i })).toBeInTheDocument();
  });

  test('each document links to the two others', async () => {
    renderLegal('terms');
    await screen.findByRole('heading', { level: 1 });
    const nav = screen.getByRole('navigation', { name: 'Informations légales' });
    expect(nav).toContainElement(screen.getByRole('link', { name: 'Confidentialité' }));
    expect(screen.getByRole('link', { name: 'Confidentialité' })).toHaveAttribute('href', '/legal/privacy');
    expect(screen.getByRole('link', { name: 'Mentions légales' })).toHaveAttribute('href', '/legal/notice');
    // The page being read is marked, not linked to itself.
    expect(screen.getByRole('link', { name: 'Conditions d’utilisation' })).toHaveAttribute('aria-current', 'page');
  });

  test('an unknown document falls back to the terms', async () => {
    renderWithProviders(<LegalPage />, { route: '/legal/nope', path: '/legal/:doc' });
    expect(await screen.findByRole('heading', { level: 1, name: "Conditions générales d'utilisation" })).toBeInTheDocument();
  });

  test('the back button is there for a reader who came from the app', async () => {
    setAuthenticated(true);
    renderLegal('privacy');
    await screen.findByRole('heading', { level: 1 });
    expect(screen.getByRole('button', { name: 'Retour' })).toBeInTheDocument();
  });
});

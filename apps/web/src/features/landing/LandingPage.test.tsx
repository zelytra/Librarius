import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { TestProviders } from '../../test/utils';
import { resetAuth, setAuthenticated, signinRedirect } from '../../test/oidcMock';
import i18n from '../../i18n';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { LandingPage } = await import('./LandingPage');

/**
 * The page renders against a two-route table rather than on its own: half of what it does
 * is decide it should not be on screen at all, and a `<Navigate>` with nowhere to go
 * proves nothing.
 */
function renderLanding() {
  return render(
    <TestProviders route="/welcome">
      <Routes>
        <Route path="/welcome" element={<LandingPage />} />
        <Route path="/" element={<p>tableau de bord</p>} />
      </Routes>
    </TestProviders>,
  );
}

describe('LandingPage', () => {
  beforeEach(() => {
    resetAuth();
    setAuthenticated(false);
  });

  test('states what the product is before asking for anything', () => {
    renderLanding();

    expect(screen.getByText('Ta bibliothèque, tome par tome.')).toBeInTheDocument();
    expect(screen.getByText(/jusqu'au tome et à l'édition/)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  /**
   * Most of this page is reached through a computed key — `landing.features.${key}` and its
   * siblings — which the static scan #230 added to `i18n/locales.test.ts` cannot resolve;
   * its own docstring says so, and leaves those to the screen's test. i18next prints the key
   * when it cannot resolve one, so a hole in either locale would land here as a dotted
   * identifier on a marketing page, which is the worst screen in the app to find one on.
   */
  test('resolves every key it builds, in both locales', async () => {
    try {
      for (const language of ['fr', 'en']) {
        await i18n.changeLanguage(language);
        const { unmount } = renderLanding();

        expect(document.body.textContent).not.toMatch(/\b(?:landing|app|auth)\.[a-z]/i);
        unmount();
      }
    } finally {
      // The language is process-wide, and the French copy is what the rest of this file
      // asserts on. Restored here rather than in an `afterAll`, which would run too late.
      await i18n.changeLanguage('fr');
    }
  });

  /**
   * The differentiator PRODUCT § 1 names, and the reason this page exists rather than a
   * generic "track your books" pitch: competitors reason in standalone books.
   */
  test('makes the series argument, and draws the run it is about', () => {
    renderLanding();

    expect(screen.getByText("Une série n'est pas une pile de livres")).toBeInTheDocument();
    expect(screen.getByText('12 / 105 tomes')).toBeInTheDocument();
    expect(screen.getByText('Incomplète')).toBeInTheDocument();
    // Twelve tiles, announced once: the caption carries the meaning, not the tiles.
    expect(screen.getByRole('img', { name: /douze tomes/ })).toBeInTheDocument();
  });

  /**
   * Sign-up is open on the realm, so this is a real call to action — but the instance
   * behind it is staging, and someone about to type in a collection is owed that before
   * they start rather than after a reset.
   */
  test('says the instance is a test environment, next to the invitation', () => {
    renderLanding();

    expect(screen.getByText(/environnement de test/)).toBeInTheDocument();
    expect(screen.getByText(/remises à zéro/)).toBeInTheDocument();
  });

  test('sends the visitor to Keycloak from either call to action', async () => {
    renderLanding();

    const calls = screen.getAllByText('Se connecter ou créer un compte');
    expect(calls).toHaveLength(2);

    await userEvent.click(calls[0]);
    expect(signinRedirect).toHaveBeenCalled();
  });

  /** Being shown a marketing page when you already have a library is a bug, not a page. */
  test('sends a reader who already has a library to the application', () => {
    setAuthenticated(true);
    renderLanding();

    expect(screen.getByText('tableau de bord')).toBeInTheDocument();
    expect(screen.queryByText('Ta bibliothèque, tome par tome.')).not.toBeInTheDocument();
  });
});

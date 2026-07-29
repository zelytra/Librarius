import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { TestProviders } from '../test/utils';
import { resetAuth, setAuthenticated, setLoading, signinRedirect } from '../test/oidcMock';
import { LOADING_DELAY_MS } from './ui/states';

vi.mock('react-oidc-context', () => import('../test/oidcMock'));

const { LoginGate } = await import('./LoginGate');

function renderGate() {
  return render(
    <TestProviders>
      <LoginGate prompt="Connecte-toi pour voir tes souhaits.">
        <p>contenu protégé</p>
      </LoginGate>
    </TestProviders>,
  );
}

describe('LoginGate', () => {
  beforeEach(resetAuth);
  afterEach(() => vi.useRealTimers());

  test('renders the guarded content once the session is resolved', () => {
    renderGate();

    expect(screen.getByText('contenu protégé')).toBeInTheDocument();
    // The branded screen is for the two waiting branches only.
    expect(screen.queryByText('Ma Bibliothèque')).not.toBeInTheDocument();
  });

  /**
   * A cold load used to open on a line of grey text. The branding is on screen straight
   * away; only the indicator waits for the wait to be worth showing.
   */
  test('names the application while the session is still being resolved', () => {
    vi.useFakeTimers();
    setLoading(true);
    renderGate();

    expect(screen.getByText('Ma Bibliothèque')).toBeInTheDocument();
    expect(screen.getByText(/bibliothèque personnelle/)).toBeInTheDocument();
    expect(screen.queryByText('contenu protégé')).not.toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(LOADING_DELAY_MS);
    });

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText('Ouverture de ta bibliothèque…')).toBeInTheDocument();
  });

  /** Being signed out is not a failure: the screen invites, it does not report an error. */
  test('welcomes rather than warns when there is no session', () => {
    setAuthenticated(false);
    renderGate();

    expect(screen.getByText('Ma Bibliothèque')).toBeInTheDocument();
    expect(screen.getByText('Connecte-toi pour voir tes souhaits.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByText('contenu protégé')).not.toBeInTheDocument();
  });

  test('sends the user to Keycloak from the welcome screen', async () => {
    setAuthenticated(false);
    renderGate();

    await userEvent.click(screen.getByText('Se connecter'));

    expect(signinRedirect).toHaveBeenCalled();
  });

  /** The presentation changed; what counts as an authenticated screen did not. */
  test('falls back to its own wording when the screen supplies no prompt', () => {
    setAuthenticated(false);
    render(
      <TestProviders>
        <LoginGate>
          <p>contenu protégé</p>
        </LoginGate>
      </TestProviders>,
    );

    expect(screen.getByText('Connecte-toi pour accéder à cette section.')).toBeInTheDocument();
  });
});

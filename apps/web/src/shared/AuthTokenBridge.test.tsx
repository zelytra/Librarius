import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';

/**
 * Coming back after the access token expired (#381). The stored user is `expired` but holds a
 * refresh token: the app must renew it, not send the reader to the sign-in screen.
 */

interface StoredUser {
  access_token: string;
  expired: boolean;
  refresh_token?: string;
}

const state: { user: StoredUser | undefined } = { user: undefined };
const signinSilent = vi.fn();
const removeUser = vi.fn();

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({
    user: state.user,
    isLoading: false,
    isAuthenticated: Boolean(state.user && !state.user.expired),
    signinSilent,
    removeUser,
    signinRedirect: vi.fn(),
    signoutRedirect: vi.fn(),
  }),
}));

const { AuthTokenBridge } = await import('./AuthTokenBridge');
const { useApiAuth } = await import('./api');

function Probe() {
  const { authed, loading } = useApiAuth();
  return <p>{loading ? 'loading' : authed ? 'signed-in' : 'signed-out'}</p>;
}

function renderApp() {
  return render(
    <>
      <AuthTokenBridge />
      <Probe />
    </>,
  );
}

describe('session restore at startup', () => {
  beforeEach(() => {
    signinSilent.mockReset();
    removeUser.mockReset();
  });

  test('renews an expired session that holds a refresh token, and waits meanwhile', async () => {
    state.user = { access_token: 'old', expired: true, refresh_token: 'offline' };
    signinSilent.mockResolvedValue({ access_token: 'new' });

    renderApp();

    expect(screen.getByText('loading')).toBeInTheDocument();
    await waitFor(() => expect(signinSilent).toHaveBeenCalledTimes(1));
    expect(removeUser).not.toHaveBeenCalled();
  });

  test('drops the stored user when the renewal is refused', async () => {
    state.user = { access_token: 'old', expired: true, refresh_token: 'revoked' };
    signinSilent.mockRejectedValue(new Error('invalid_grant'));

    renderApp();

    await waitFor(() => expect(removeUser).toHaveBeenCalledTimes(1));
  });

  test('does not try to renew a session without a refresh token', () => {
    state.user = { access_token: 'old', expired: true };

    renderApp();

    expect(screen.getByText('signed-out')).toBeInTheDocument();
    expect(signinSilent).not.toHaveBeenCalled();
  });

  test('leaves a valid session alone', () => {
    state.user = { access_token: 'fresh', expired: false, refresh_token: 'offline' };

    renderApp();

    expect(screen.getByText('signed-in')).toBeInTheDocument();
    expect(signinSilent).not.toHaveBeenCalled();
  });
});

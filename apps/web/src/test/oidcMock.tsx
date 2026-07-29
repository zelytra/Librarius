// This module is only loaded by the tests: it is never served to the browser, so hot
// refresh does not apply to it. It has to export the provider, the hook and the
// functions that drive the simulated state, all at once.
/* eslint-disable react-refresh/only-export-components */
import type { ReactNode } from 'react';
import { vi } from 'vitest';

/**
 * Stand-in for `react-oidc-context` in tests.
 *
 * The real provider would trigger a redirect to Keycloak, which is out of reach for a
 * unit test. Screens consume authentication through `useApiAuth()`, which builds on
 * `useAuth()`: replacing it here is enough to cover both states.
 *
 * Usage in a test file:
 * ```ts
 * vi.mock('react-oidc-context', () => import('../../test/oidcMock'));
 * ```
 */

export const signinRedirect = vi.fn();
export const removeUser = vi.fn();

/** Mutable state, to be adjusted before rendering (see `setAuthenticated`). */
export const authState = {
  isAuthenticated: true,
  isLoading: false,
  accessToken: 'jeton-de-test',
};

export function setAuthenticated(value: boolean) {
  authState.isAuthenticated = value;
  authState.isLoading = false;
}

export function setLoading(value: boolean) {
  authState.isLoading = value;
}

/** Restores the default state: signed in, loading finished. */
export function resetAuth() {
  authState.isAuthenticated = true;
  authState.isLoading = false;
  signinRedirect.mockClear();
  removeUser.mockClear();
}

export function AuthProvider({ children }: { children: ReactNode }) {
  return <>{children}</>;
}

export function useAuth() {
  return {
    isAuthenticated: authState.isAuthenticated,
    isLoading: authState.isLoading,
    user: authState.isAuthenticated ? { access_token: authState.accessToken } : undefined,
    signinRedirect,
    removeUser,
  };
}

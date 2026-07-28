// Ce module n'est chargé que par les tests : il n'est jamais servi au navigateur, donc
// le rafraîchissement à chaud ne le concerne pas. Il doit exporter à la fois le
// fournisseur, le hook et les fonctions qui pilotent l'état simulé.
/* eslint-disable react-refresh/only-export-components */
import type { ReactNode } from 'react';
import { vi } from 'vitest';

/**
 * Substitut de `react-oidc-context` pour les tests.
 *
 * Le vrai fournisseur déclencherait une redirection vers Keycloak, hors de portée d'un
 * test unitaire. Les écrans consomment l'authentification via `useApiAuth()`, qui
 * s'appuie sur `useAuth()` : le remplacer ici suffit à couvrir les deux états.
 *
 * Utilisation dans un fichier de test :
 * ```ts
 * vi.mock('react-oidc-context', () => import('../../test/oidcMock'));
 * ```
 */

export const signinRedirect = vi.fn();

/** État mutable, à ajuster avant le rendu (voir `setAuthenticated`). */
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

/** Remet l'état par défaut : connecté, chargement terminé. */
export function resetAuth() {
  authState.isAuthenticated = true;
  authState.isLoading = false;
  signinRedirect.mockClear();
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
  };
}

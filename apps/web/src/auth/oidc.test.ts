import { describe, expect, test } from 'vitest';
import { oidcConfig } from './oidc';

describe('oidcConfig', () => {
  /** Long sessions: an offline token survives Keycloak restarts and has no maximum lifetime. */
  test('asks for an offline token, and revokes it on sign-out', () => {
    expect(oidcConfig.scope?.split(' ')).toContain('offline_access');
    expect(oidcConfig.revokeTokensOnSignout).toBe(true);
  });
});

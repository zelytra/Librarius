import path from 'node:path';

/**
 * Addresses of the stack under test. The defaults match `docker-compose.e2e.yml`; the
 * environment variables exist so the suite can also be pointed at an already-running
 * stack (`E2E_STACK=external`).
 */
export const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:4173';
export const OIDC_AUTHORITY =
  process.env.E2E_OIDC_AUTHORITY ?? 'http://localhost:8081/realms/librarius';
export const OIDC_CLIENT_ID = process.env.E2E_OIDC_CLIENT_ID ?? 'librarius-web';

/** Test account of the local Keycloak realm (`infra/keycloak/realm-librarius.json`). */
export const TEST_USER = { username: 'alice', password: 'alice' };

export const E2E_DIR = path.resolve(__dirname, '..');
export const COMPOSE_FILE = path.join(E2E_DIR, 'docker-compose.e2e.yml');

/** True when the stack is managed elsewhere (developer loop, or a CI job that split it out). */
export const STACK_IS_EXTERNAL = process.env.E2E_STACK === 'external';

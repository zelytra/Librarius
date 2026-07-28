import { execFileSync } from 'node:child_process';
import { BASE_URL, COMPOSE_FILE, E2E_DIR, OIDC_AUTHORITY } from './config';

/** Runs a `docker compose` subcommand against the e2e stack, streaming its output. */
function compose(...args: string[]): void {
  execFileSync('docker', ['compose', '-f', COMPOSE_FILE, ...args], {
    cwd: E2E_DIR,
    stdio: 'inherit',
  });
}

export function startStack(): void {
  compose('up', '-d', '--remove-orphans');
}

export function stopStack(): void {
  compose('down', '-v', '--remove-orphans');
}

/** Dumps the container logs; called when readiness times out, where they are the diagnosis. */
export function dumpStackLogs(): void {
  try {
    compose('logs', '--tail', '80');
  } catch {
    // The logs are a courtesy: never mask the original failure with a docker error.
  }
}

async function waitForHttp(label: string, url: string, timeoutMs: number): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  let lastError = 'no attempt';
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(5_000) });
      if (response.ok) return;
      lastError = `HTTP ${response.status}`;
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`${label} was not ready after ${timeoutMs / 1000}s (${url}): ${lastError}`);
}

/**
 * Waits until the whole chain answers. The API is probed *through the web server* on
 * purpose: `/q/health/ready` only answers if nginx routes it to the API, which is the
 * class of regression this suite exists for.
 */
export async function waitForStack(): Promise<void> {
  await waitForHttp('Keycloak', `${OIDC_AUTHORITY}/.well-known/openid-configuration`, 120_000);
  await waitForHttp('Web', BASE_URL, 120_000);
  await waitForHttp('API (through the web server)', `${BASE_URL}/q/health/ready`, 180_000);
}
